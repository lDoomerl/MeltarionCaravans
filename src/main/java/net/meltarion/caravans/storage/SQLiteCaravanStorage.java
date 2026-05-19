package net.meltarion.caravans.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.model.CaravanStatus;
import net.meltarion.caravans.model.TradeOperationRecord;
import net.meltarion.caravans.model.TradeOperationType;
import net.meltarion.caravans.util.ItemStackSerializationUtil;

public final class SQLiteCaravanStorage implements CaravanStorage, CaravanInventoryStorage, TradeOperationStorage {

    private static final String CREATE_CARAVANS_TABLE = """
        CREATE TABLE IF NOT EXISTS caravans (
            id TEXT PRIMARY KEY,
            owner_uuid TEXT NOT NULL,
            owner_name TEXT NOT NULL,
            name TEXT NOT NULL,
            status TEXT NOT NULL,
            hp INTEGER NOT NULL,
            max_hp INTEGER NOT NULL,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
        )
        """;

    private static final String CREATE_OWNER_INDEX = """
        CREATE INDEX IF NOT EXISTS idx_caravans_owner_uuid
        ON caravans (owner_uuid)
        """;

    private static final String CREATE_CARAVAN_INVENTORIES_TABLE = """
        CREATE TABLE IF NOT EXISTS caravan_inventories (
            caravan_id TEXT PRIMARY KEY,
            contents TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            FOREIGN KEY (caravan_id) REFERENCES caravans(id) ON DELETE CASCADE
        )
        """;

    private static final String CREATE_TRADE_OPERATIONS_TABLE = """
        CREATE TABLE IF NOT EXISTS trade_operations (
            id TEXT PRIMARY KEY,
            caravan_id TEXT NOT NULL,
            type TEXT NOT NULL,
            item_stack TEXT NOT NULL,
            amount_per_transaction INTEGER NOT NULL,
            price_currency_amount INTEGER NOT NULL,
            max_total_amount INTEGER,
            fulfilled_amount INTEGER NOT NULL DEFAULT 0,
            reserved_inventory_slot INTEGER,
            active INTEGER NOT NULL DEFAULT 1,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            FOREIGN KEY (caravan_id) REFERENCES caravans(id) ON DELETE CASCADE
        )
        """;

    private static final String CREATE_TRADE_CARAVAN_INDEX = """
        CREATE INDEX IF NOT EXISTS idx_trade_operations_caravan_id
        ON trade_operations (caravan_id)
        """;

    private static final String CREATE_TRADE_TYPE_INDEX = """
        CREATE INDEX IF NOT EXISTS idx_trade_operations_type
        ON trade_operations (type)
        """;

    private static final String CREATE_TRADE_ACTIVE_INDEX = """
        CREATE INDEX IF NOT EXISTS idx_trade_operations_active
        ON trade_operations (active)
        """;

    private static final String SELECT_ALL_CARAVANS = """
        SELECT id, owner_uuid, owner_name, name, status, hp, max_hp, created_at, updated_at
        FROM caravans
        ORDER BY created_at ASC
        """;

    private static final String INSERT_CARAVAN = """
        INSERT INTO caravans (id, owner_uuid, owner_name, name, status, hp, max_hp, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    private static final String RENAME_CARAVAN = """
        UPDATE caravans
        SET name = ?, updated_at = ?
        WHERE id = ?
        """;

    private static final String DELETE_CARAVAN = """
        DELETE FROM caravans
        WHERE id = ?
        """;

    private static final String UPDATE_CARAVAN_STATE = """
        UPDATE caravans
        SET status = ?, hp = ?, updated_at = ?
        WHERE id = ?
        """;

    private static final String DELETE_CARAVAN_INVENTORY = """
        DELETE FROM caravan_inventories
        WHERE caravan_id = ?
        """;

    private static final String UPSERT_CARAVAN_INVENTORY = """
        INSERT INTO caravan_inventories (caravan_id, contents, updated_at)
        VALUES (?, ?, ?)
        ON CONFLICT(caravan_id) DO UPDATE SET
            contents = excluded.contents,
            updated_at = excluded.updated_at
        """;

    private static final String SELECT_CARAVAN_INVENTORY = """
        SELECT contents
        FROM caravan_inventories
        WHERE caravan_id = ?
        """;

    private static final String SELECT_ALL_TRADE_OPERATIONS = """
        SELECT id, caravan_id, type, item_stack, amount_per_transaction, price_currency_amount,
               max_total_amount, fulfilled_amount, reserved_inventory_slot, active, created_at, updated_at
        FROM trade_operations
        ORDER BY created_at ASC
        """;

    private static final String INSERT_TRADE_OPERATION = """
        INSERT INTO trade_operations (
            id, caravan_id, type, item_stack, amount_per_transaction, price_currency_amount,
            max_total_amount, fulfilled_amount, reserved_inventory_slot, active, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    private static final String UPDATE_TRADE_OPERATION_ACTIVE = """
        UPDATE trade_operations
        SET active = ?, updated_at = ?
        WHERE id = ?
        """;

    private static final String DELETE_TRADE_OPERATION = """
        DELETE FROM trade_operations
        WHERE id = ?
        """;

    private static final String DELETE_TRADE_OPERATIONS_BY_CARAVAN = """
        DELETE FROM trade_operations
        WHERE caravan_id = ?
        """;

    private final Path databasePath;
    private final Logger logger;

    private Connection connection;

    public SQLiteCaravanStorage(Path databasePath, Logger logger) {
        this.databasePath = databasePath;
        this.logger = logger;
    }

    @Override
    public synchronized void initialize() throws StorageException {
        try {
            Files.createDirectories(databasePath.getParent());
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());

            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.executeUpdate(CREATE_CARAVANS_TABLE);
                statement.executeUpdate(CREATE_OWNER_INDEX);
                statement.executeUpdate(CREATE_CARAVAN_INVENTORIES_TABLE);
                statement.executeUpdate(CREATE_TRADE_OPERATIONS_TABLE);
                statement.executeUpdate(CREATE_TRADE_CARAVAN_INDEX);
                statement.executeUpdate(CREATE_TRADE_TYPE_INDEX);
                statement.executeUpdate(CREATE_TRADE_ACTIVE_INDEX);
            }

            logger.info("Initialized SQLite caravan storage at " + databasePath.toAbsolutePath() + '.');
        } catch (SQLException exception) {
            throw new StorageException("Failed to initialize SQLite storage.", exception);
        } catch (Exception exception) {
            throw new StorageException("Failed to prepare SQLite database path.", exception);
        }
    }

    @Override
    public synchronized List<CaravanRecord> loadAllCaravans() throws StorageException {
        ensureInitialized();

        List<CaravanRecord> caravans = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ALL_CARAVANS);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                CaravanRecord caravan = mapRecord(resultSet);
                if (caravan != null) {
                    caravans.add(caravan);
                }
            }
        } catch (SQLException exception) {
            throw new StorageException("Failed to load caravans from SQLite.", exception);
        }

        logger.info("Loaded " + caravans.size() + " caravan records from SQLite.");
        return caravans;
    }

    @Override
    public synchronized void insertCaravan(CaravanRecord caravan) throws StorageException {
        ensureInitialized();

        try (PreparedStatement statement = connection.prepareStatement(INSERT_CARAVAN)) {
            statement.setString(1, caravan.id().toString());
            statement.setString(2, caravan.ownerId().toString());
            statement.setString(3, caravan.ownerName());
            statement.setString(4, caravan.name());
            statement.setString(5, caravan.status().name());
            statement.setInt(6, caravan.hp());
            statement.setInt(7, caravan.maxHp());
            statement.setString(8, caravan.createdAt().toString());
            statement.setString(9, caravan.updatedAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Failed to insert caravan into SQLite.", exception);
        }
    }

    @Override
    public synchronized void renameCaravan(UUID caravanId, String name, String updatedAt) throws StorageException {
        ensureInitialized();

        try (PreparedStatement statement = connection.prepareStatement(RENAME_CARAVAN)) {
            statement.setString(1, name);
            statement.setString(2, updatedAt);
            statement.setString(3, caravanId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Failed to rename caravan in SQLite.", exception);
        }
    }

    @Override
    public synchronized void deleteCaravan(UUID caravanId) throws StorageException {
        ensureInitialized();

        try (PreparedStatement statement = connection.prepareStatement(DELETE_CARAVAN)) {
            statement.setString(1, caravanId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Failed to delete caravan from SQLite.", exception);
        }
    }

    @Override
    public synchronized void updateCaravanState(UUID caravanId, String status, int hp, String updatedAt) throws StorageException {
        ensureInitialized();

        try (PreparedStatement statement = connection.prepareStatement(UPDATE_CARAVAN_STATE)) {
            statement.setString(1, status);
            statement.setInt(2, hp);
            statement.setString(3, updatedAt);
            statement.setString(4, caravanId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Failed to update caravan state in SQLite.", exception);
        }
    }

    @Override
    public synchronized void deleteCaravanData(UUID caravanId) throws StorageException {
        ensureInitialized();

        boolean previousAutoCommit = true;
        try {
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement deleteInventory = connection.prepareStatement(DELETE_CARAVAN_INVENTORY);
                 PreparedStatement deleteTradeOperations = connection.prepareStatement(DELETE_TRADE_OPERATIONS_BY_CARAVAN);
                 PreparedStatement deleteCaravan = connection.prepareStatement(DELETE_CARAVAN)) {
                deleteInventory.setString(1, caravanId.toString());
                deleteInventory.executeUpdate();

                deleteTradeOperations.setString(1, caravanId.toString());
                deleteTradeOperations.executeUpdate();

                deleteCaravan.setString(1, caravanId.toString());
                deleteCaravan.executeUpdate();
            }

            connection.commit();
        } catch (SQLException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw new StorageException("Failed to delete caravan data from SQLite.", exception);
        } finally {
            try {
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException ignored) {
            }
        }
    }

    @Override
    public synchronized String loadInventoryContents(UUID caravanId) throws StorageException {
        ensureInitialized();

        try (PreparedStatement statement = connection.prepareStatement(SELECT_CARAVAN_INVENTORY)) {
            statement.setString(1, caravanId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("contents");
                }
                return null;
            }
        } catch (SQLException exception) {
            throw new StorageException("Failed to load caravan inventory from SQLite.", exception);
        }
    }

    @Override
    public synchronized void saveInventoryContents(UUID caravanId, String serializedContents, String updatedAt) throws StorageException {
        ensureInitialized();

        try (PreparedStatement statement = connection.prepareStatement(UPSERT_CARAVAN_INVENTORY)) {
            statement.setString(1, caravanId.toString());
            statement.setString(2, serializedContents);
            statement.setString(3, updatedAt);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Failed to save caravan inventory to SQLite.", exception);
        }
    }

    @Override
    public synchronized void deleteInventoryContents(UUID caravanId) throws StorageException {
        ensureInitialized();

        try (PreparedStatement statement = connection.prepareStatement(DELETE_CARAVAN_INVENTORY)) {
            statement.setString(1, caravanId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Failed to delete caravan inventory from SQLite.", exception);
        }
    }

    @Override
    public synchronized List<TradeOperationRecord> loadAllTradeOperations() throws StorageException {
        ensureInitialized();

        List<TradeOperationRecord> tradeOperations = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ALL_TRADE_OPERATIONS);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                TradeOperationRecord tradeOperation = mapTradeOperation(resultSet);
                if (tradeOperation != null) {
                    tradeOperations.add(tradeOperation);
                }
            }
        } catch (SQLException exception) {
            throw new StorageException("Failed to load trade operations from SQLite.", exception);
        }

        logger.info("Loaded " + tradeOperations.size() + " trade operations from SQLite.");
        return tradeOperations;
    }

    @Override
    public synchronized void insertTradeOperation(TradeOperationRecord tradeOperation) throws StorageException {
        ensureInitialized();

        try (PreparedStatement statement = connection.prepareStatement(INSERT_TRADE_OPERATION)) {
            statement.setString(1, tradeOperation.id().toString());
            statement.setString(2, tradeOperation.caravanId().toString());
            statement.setString(3, tradeOperation.type().name());
            statement.setString(4, ItemStackSerializationUtil.serializeItemStack(tradeOperation.itemStack()));
            statement.setInt(5, tradeOperation.amountPerTransaction());
            statement.setInt(6, tradeOperation.priceCurrencyAmount());
            if (tradeOperation.maxTotalAmount() == null) {
                statement.setNull(7, java.sql.Types.INTEGER);
            } else {
                statement.setInt(7, tradeOperation.maxTotalAmount());
            }
            statement.setInt(8, tradeOperation.fulfilledAmount());
            if (tradeOperation.reservedInventorySlot() == null) {
                statement.setNull(9, java.sql.Types.INTEGER);
            } else {
                statement.setInt(9, tradeOperation.reservedInventorySlot());
            }
            statement.setInt(10, tradeOperation.active() ? 1 : 0);
            statement.setString(11, tradeOperation.createdAt().toString());
            statement.setString(12, tradeOperation.updatedAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Failed to insert trade operation into SQLite.", exception);
        }
    }

    @Override
    public synchronized void updateTradeOperationActiveState(UUID tradeOperationId, boolean active, String updatedAt) throws StorageException {
        ensureInitialized();

        try (PreparedStatement statement = connection.prepareStatement(UPDATE_TRADE_OPERATION_ACTIVE)) {
            statement.setInt(1, active ? 1 : 0);
            statement.setString(2, updatedAt);
            statement.setString(3, tradeOperationId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Failed to update trade operation in SQLite.", exception);
        }
    }

    @Override
    public synchronized void deleteTradeOperation(UUID tradeOperationId) throws StorageException {
        ensureInitialized();

        try (PreparedStatement statement = connection.prepareStatement(DELETE_TRADE_OPERATION)) {
            statement.setString(1, tradeOperationId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Failed to delete trade operation from SQLite.", exception);
        }
    }

    @Override
    public synchronized void deleteTradeOperationsByCaravan(UUID caravanId) throws StorageException {
        ensureInitialized();

        try (PreparedStatement statement = connection.prepareStatement(DELETE_TRADE_OPERATIONS_BY_CARAVAN)) {
            statement.setString(1, caravanId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Failed to delete caravan trade operations from SQLite.", exception);
        }
    }

    @Override
    public synchronized void close() throws StorageException {
        if (connection == null) {
            return;
        }

        try {
            connection.close();
            logger.info("Closed SQLite caravan storage.");
        } catch (SQLException exception) {
            throw new StorageException("Failed to close SQLite storage connection.", exception);
        } finally {
            connection = null;
        }
    }

    private CaravanRecord mapRecord(ResultSet resultSet) throws SQLException {
        try {
            return new CaravanRecord(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("owner_uuid")),
                resultSet.getString("owner_name"),
                resultSet.getString("name"),
                CaravanStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("hp"),
                resultSet.getInt("max_hp"),
                Instant.parse(resultSet.getString("created_at")),
                Instant.parse(resultSet.getString("updated_at"))
            );
        } catch (IllegalArgumentException exception) {
            logger.log(Level.WARNING, "Skipping invalid caravan row while reading SQLite data.", exception);
            return null;
        }
    }

    private TradeOperationRecord mapTradeOperation(ResultSet resultSet) throws SQLException {
        try {
            int reservedSlot = resultSet.getInt("reserved_inventory_slot");
            Integer reservedInventorySlot = resultSet.wasNull() ? null : reservedSlot;

            int maxTotal = resultSet.getInt("max_total_amount");
            Integer maxTotalAmount = resultSet.wasNull() ? null : maxTotal;

            return new TradeOperationRecord(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("caravan_id")),
                TradeOperationType.valueOf(resultSet.getString("type")),
                ItemStackSerializationUtil.deserializeItemStack(resultSet.getString("item_stack")),
                resultSet.getInt("amount_per_transaction"),
                resultSet.getInt("price_currency_amount"),
                maxTotalAmount,
                resultSet.getInt("fulfilled_amount"),
                reservedInventorySlot,
                resultSet.getInt("active") == 1,
                Instant.parse(resultSet.getString("created_at")),
                Instant.parse(resultSet.getString("updated_at"))
            );
        } catch (IllegalArgumentException | StorageException exception) {
            logger.log(Level.WARNING, "Skipping invalid trade operation row while reading SQLite data.", exception);
            return null;
        }
    }

    private void ensureInitialized() throws StorageException {
        if (connection == null) {
            throw new StorageException("SQLite storage has not been initialized.");
        }
    }
}
