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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.model.CaravanRouteStopRecord;
import net.meltarion.caravans.model.CaravanStatus;
import net.meltarion.caravans.model.TradeOperationRecord;
import net.meltarion.caravans.model.TradeOperationType;
import net.meltarion.caravans.util.ItemStackSerializationUtil;

public final class SQLiteCaravanStorage implements CaravanStorage, CaravanInventoryStorage, TradeOperationStorage, CaravanRouteStorage {

    private static final String CREATE_CARAVANS_TABLE = """
        CREATE TABLE IF NOT EXISTS caravans (
            id TEXT PRIMARY KEY,
            owner_uuid TEXT NOT NULL,
            owner_name TEXT NOT NULL,
            name TEXT NOT NULL,
            status TEXT NOT NULL,
            hp INTEGER NOT NULL,
            max_hp INTEGER NOT NULL,
            world_name TEXT,
            virtual_x REAL,
            virtual_y REAL,
            virtual_z REAL,
            target_world_name TEXT,
            target_x REAL,
            target_y REAL,
            target_z REAL,
            movement_started_at TEXT,
            movement_updated_at TEXT,
            speed_blocks_per_second REAL NOT NULL DEFAULT 1.5,
            eta_seconds INTEGER,
            physical_spawned INTEGER NOT NULL DEFAULT 0,
            home_world_name TEXT,
            home_x REAL,
            home_y REAL,
            home_z REAL,
            current_route_stop_index INTEGER,
            route_running INTEGER NOT NULL DEFAULT 0,
            current_stop_started_at TEXT,
            current_stop_ends_at TEXT,
            returning_home_after_route INTEGER NOT NULL DEFAULT 0,
            route_loop_enabled INTEGER NOT NULL DEFAULT 0,
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

    private static final String CREATE_ROUTE_STOPS_TABLE = """
        CREATE TABLE IF NOT EXISTS caravan_route_stops (
            id TEXT PRIMARY KEY,
            caravan_id TEXT NOT NULL,
            stop_order INTEGER NOT NULL,
            town_name TEXT NOT NULL,
            world_name TEXT NOT NULL,
            x REAL NOT NULL,
            y REAL NOT NULL,
            z REAL NOT NULL,
            stop_duration_seconds INTEGER NOT NULL,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            FOREIGN KEY (caravan_id) REFERENCES caravans(id) ON DELETE CASCADE
        )
        """;

    private static final String CREATE_ROUTE_CARAVAN_INDEX = """
        CREATE INDEX IF NOT EXISTS idx_caravan_route_stops_caravan_id
        ON caravan_route_stops (caravan_id)
        """;

    private static final String CREATE_ROUTE_CARAVAN_ORDER_INDEX = """
        CREATE INDEX IF NOT EXISTS idx_caravan_route_stops_caravan_order
        ON caravan_route_stops (caravan_id, stop_order)
        """;

    private static final String SELECT_ALL_CARAVANS = """
        SELECT id, owner_uuid, owner_name, name, status, hp, max_hp,
               world_name, virtual_x, virtual_y, virtual_z,
               target_world_name, target_x, target_y, target_z,
               movement_started_at, movement_updated_at, speed_blocks_per_second, eta_seconds, physical_spawned,
               home_world_name, home_x, home_y, home_z,
               current_route_stop_index, route_running, current_stop_started_at, current_stop_ends_at, returning_home_after_route,
               route_loop_enabled,
               created_at, updated_at
        FROM caravans
        ORDER BY created_at ASC
        """;

    private static final String INSERT_CARAVAN = """
        INSERT INTO caravans (
            id, owner_uuid, owner_name, name, status, hp, max_hp,
            world_name, virtual_x, virtual_y, virtual_z,
            target_world_name, target_x, target_y, target_z,
            movement_started_at, movement_updated_at, speed_blocks_per_second, eta_seconds, physical_spawned,
            home_world_name, home_x, home_y, home_z,
            current_route_stop_index, route_running, current_stop_started_at, current_stop_ends_at, returning_home_after_route,
            route_loop_enabled,
            created_at, updated_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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

    private static final String UPDATE_CARAVAN = """
        UPDATE caravans
        SET owner_name = ?, name = ?, status = ?, hp = ?, max_hp = ?,
            world_name = ?, virtual_x = ?, virtual_y = ?, virtual_z = ?,
            target_world_name = ?, target_x = ?, target_y = ?, target_z = ?,
            movement_started_at = ?, movement_updated_at = ?, speed_blocks_per_second = ?, eta_seconds = ?, physical_spawned = ?,
            home_world_name = ?, home_x = ?, home_y = ?, home_z = ?,
            current_route_stop_index = ?, route_running = ?, current_stop_started_at = ?, current_stop_ends_at = ?, returning_home_after_route = ?,
            route_loop_enabled = ?,
            updated_at = ?
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

    private static final String UPDATE_TRADE_OPERATION = """
        UPDATE trade_operations
        SET item_stack = ?, amount_per_transaction = ?, price_currency_amount = ?,
            max_total_amount = ?, fulfilled_amount = ?, reserved_inventory_slot = ?,
            active = ?, updated_at = ?
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

    private static final String SELECT_ALL_ROUTE_STOPS = """
        SELECT id, caravan_id, stop_order, town_name, world_name, x, y, z, stop_duration_seconds, created_at, updated_at
        FROM caravan_route_stops
        ORDER BY caravan_id ASC, stop_order ASC, created_at ASC
        """;

    private static final String INSERT_ROUTE_STOP = """
        INSERT INTO caravan_route_stops (
            id, caravan_id, stop_order, town_name, world_name, x, y, z, stop_duration_seconds, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    private static final String UPDATE_ROUTE_STOP = """
        UPDATE caravan_route_stops
        SET stop_order = ?, town_name = ?, world_name = ?, x = ?, y = ?, z = ?, stop_duration_seconds = ?, updated_at = ?
        WHERE id = ?
        """;

    private static final String DELETE_ROUTE_STOP = """
        DELETE FROM caravan_route_stops
        WHERE id = ?
        """;

    private static final String DELETE_ROUTE_STOPS_BY_CARAVAN = """
        DELETE FROM caravan_route_stops
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
                ensureCaravanColumns(statement);
                statement.executeUpdate(CREATE_OWNER_INDEX);
                statement.executeUpdate(CREATE_CARAVAN_INVENTORIES_TABLE);
                statement.executeUpdate(CREATE_TRADE_OPERATIONS_TABLE);
                statement.executeUpdate(CREATE_TRADE_CARAVAN_INDEX);
                statement.executeUpdate(CREATE_TRADE_TYPE_INDEX);
                statement.executeUpdate(CREATE_TRADE_ACTIVE_INDEX);
                statement.executeUpdate(CREATE_ROUTE_STOPS_TABLE);
                statement.executeUpdate(CREATE_ROUTE_CARAVAN_INDEX);
                statement.executeUpdate(CREATE_ROUTE_CARAVAN_ORDER_INDEX);
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
            statement.setString(8, caravan.worldName());
            setNullableDouble(statement, 9, caravan.virtualX());
            setNullableDouble(statement, 10, caravan.virtualY());
            setNullableDouble(statement, 11, caravan.virtualZ());
            statement.setString(12, caravan.targetWorldName());
            setNullableDouble(statement, 13, caravan.targetX());
            setNullableDouble(statement, 14, caravan.targetY());
            setNullableDouble(statement, 15, caravan.targetZ());
            setNullableInstant(statement, 16, caravan.movementStartedAt());
            setNullableInstant(statement, 17, caravan.movementUpdatedAt());
            statement.setDouble(18, caravan.speedBlocksPerSecond());
            setNullableInteger(statement, 19, caravan.etaSeconds());
            statement.setInt(20, caravan.physicalSpawned() ? 1 : 0);
            statement.setString(21, caravan.homeWorldName());
            setNullableDouble(statement, 22, caravan.homeX());
            setNullableDouble(statement, 23, caravan.homeY());
            setNullableDouble(statement, 24, caravan.homeZ());
            setNullableInteger(statement, 25, caravan.currentRouteStopIndex());
            statement.setInt(26, caravan.routeRunning() ? 1 : 0);
            setNullableInstant(statement, 27, caravan.currentStopStartedAt());
            setNullableInstant(statement, 28, caravan.currentStopEndsAt());
            statement.setInt(29, caravan.returningHomeAfterRoute() ? 1 : 0);
            statement.setInt(30, caravan.routeLoopEnabled() ? 1 : 0);
            statement.setString(31, caravan.createdAt().toString());
            statement.setString(32, caravan.updatedAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Failed to insert caravan into SQLite.", exception);
        }
    }

    @Override
    public synchronized void updateCaravan(CaravanRecord caravan) throws StorageException {
        ensureInitialized();

        try (PreparedStatement statement = connection.prepareStatement(UPDATE_CARAVAN)) {
            statement.setString(1, caravan.ownerName());
            statement.setString(2, caravan.name());
            statement.setString(3, caravan.status().name());
            statement.setInt(4, caravan.hp());
            statement.setInt(5, caravan.maxHp());
            statement.setString(6, caravan.worldName());
            setNullableDouble(statement, 7, caravan.virtualX());
            setNullableDouble(statement, 8, caravan.virtualY());
            setNullableDouble(statement, 9, caravan.virtualZ());
            statement.setString(10, caravan.targetWorldName());
            setNullableDouble(statement, 11, caravan.targetX());
            setNullableDouble(statement, 12, caravan.targetY());
            setNullableDouble(statement, 13, caravan.targetZ());
            setNullableInstant(statement, 14, caravan.movementStartedAt());
            setNullableInstant(statement, 15, caravan.movementUpdatedAt());
            statement.setDouble(16, caravan.speedBlocksPerSecond());
            setNullableInteger(statement, 17, caravan.etaSeconds());
            statement.setInt(18, caravan.physicalSpawned() ? 1 : 0);
            statement.setString(19, caravan.homeWorldName());
            setNullableDouble(statement, 20, caravan.homeX());
            setNullableDouble(statement, 21, caravan.homeY());
            setNullableDouble(statement, 22, caravan.homeZ());
            setNullableInteger(statement, 23, caravan.currentRouteStopIndex());
            statement.setInt(24, caravan.routeRunning() ? 1 : 0);
            setNullableInstant(statement, 25, caravan.currentStopStartedAt());
            setNullableInstant(statement, 26, caravan.currentStopEndsAt());
            statement.setInt(27, caravan.returningHomeAfterRoute() ? 1 : 0);
            statement.setInt(28, caravan.routeLoopEnabled() ? 1 : 0);
            statement.setString(29, caravan.updatedAt().toString());
            statement.setString(30, caravan.id().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Failed to update caravan in SQLite.", exception);
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
                 PreparedStatement deleteRouteStops = connection.prepareStatement(DELETE_ROUTE_STOPS_BY_CARAVAN);
                 PreparedStatement deleteCaravan = connection.prepareStatement(DELETE_CARAVAN)) {
                deleteInventory.setString(1, caravanId.toString());
                deleteInventory.executeUpdate();

                deleteTradeOperations.setString(1, caravanId.toString());
                deleteTradeOperations.executeUpdate();

                deleteRouteStops.setString(1, caravanId.toString());
                deleteRouteStops.executeUpdate();

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
    public synchronized void updateTradeOperation(TradeOperationRecord tradeOperation) throws StorageException {
        ensureInitialized();

        try (PreparedStatement statement = connection.prepareStatement(UPDATE_TRADE_OPERATION)) {
            statement.setString(1, ItemStackSerializationUtil.serializeItemStack(tradeOperation.itemStack()));
            statement.setInt(2, tradeOperation.amountPerTransaction());
            statement.setInt(3, tradeOperation.priceCurrencyAmount());
            if (tradeOperation.maxTotalAmount() == null) {
                statement.setNull(4, java.sql.Types.INTEGER);
            } else {
                statement.setInt(4, tradeOperation.maxTotalAmount());
            }
            statement.setInt(5, tradeOperation.fulfilledAmount());
            if (tradeOperation.reservedInventorySlot() == null) {
                statement.setNull(6, java.sql.Types.INTEGER);
            } else {
                statement.setInt(6, tradeOperation.reservedInventorySlot());
            }
            statement.setInt(7, tradeOperation.active() ? 1 : 0);
            statement.setString(8, tradeOperation.updatedAt().toString());
            statement.setString(9, tradeOperation.id().toString());
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
    public synchronized List<CaravanRouteStopRecord> loadAllRouteStops() throws StorageException {
        ensureInitialized();

        List<CaravanRouteStopRecord> routeStops = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ALL_ROUTE_STOPS);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                routeStops.add(new CaravanRouteStopRecord(
                    UUID.fromString(resultSet.getString("id")),
                    UUID.fromString(resultSet.getString("caravan_id")),
                    resultSet.getInt("stop_order"),
                    resultSet.getString("town_name"),
                    resultSet.getString("world_name"),
                    resultSet.getDouble("x"),
                    resultSet.getDouble("y"),
                    resultSet.getDouble("z"),
                    resultSet.getInt("stop_duration_seconds"),
                    Instant.parse(resultSet.getString("created_at")),
                    Instant.parse(resultSet.getString("updated_at"))
                ));
            }
        } catch (SQLException exception) {
            throw new StorageException("Failed to load caravan route stops from SQLite.", exception);
        }

        return routeStops;
    }

    @Override
    public synchronized void insertRouteStop(CaravanRouteStopRecord routeStop) throws StorageException {
        ensureInitialized();

        try (PreparedStatement statement = connection.prepareStatement(INSERT_ROUTE_STOP)) {
            statement.setString(1, routeStop.id().toString());
            statement.setString(2, routeStop.caravanId().toString());
            statement.setInt(3, routeStop.stopOrder());
            statement.setString(4, routeStop.townName());
            statement.setString(5, routeStop.worldName());
            statement.setDouble(6, routeStop.x());
            statement.setDouble(7, routeStop.y());
            statement.setDouble(8, routeStop.z());
            statement.setInt(9, routeStop.stopDurationSeconds());
            statement.setString(10, routeStop.createdAt().toString());
            statement.setString(11, routeStop.updatedAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Failed to insert caravan route stop into SQLite.", exception);
        }
    }

    @Override
    public synchronized void updateRouteStop(CaravanRouteStopRecord routeStop) throws StorageException {
        ensureInitialized();

        try (PreparedStatement statement = connection.prepareStatement(UPDATE_ROUTE_STOP)) {
            statement.setInt(1, routeStop.stopOrder());
            statement.setString(2, routeStop.townName());
            statement.setString(3, routeStop.worldName());
            statement.setDouble(4, routeStop.x());
            statement.setDouble(5, routeStop.y());
            statement.setDouble(6, routeStop.z());
            statement.setInt(7, routeStop.stopDurationSeconds());
            statement.setString(8, routeStop.updatedAt().toString());
            statement.setString(9, routeStop.id().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Failed to update caravan route stop in SQLite.", exception);
        }
    }

    @Override
    public synchronized void deleteRouteStop(UUID routeStopId) throws StorageException {
        ensureInitialized();

        try (PreparedStatement statement = connection.prepareStatement(DELETE_ROUTE_STOP)) {
            statement.setString(1, routeStopId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Failed to delete caravan route stop from SQLite.", exception);
        }
    }

    @Override
    public synchronized void deleteRouteStopsByCaravan(UUID caravanId) throws StorageException {
        ensureInitialized();

        try (PreparedStatement statement = connection.prepareStatement(DELETE_ROUTE_STOPS_BY_CARAVAN)) {
            statement.setString(1, caravanId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new StorageException("Failed to delete caravan route stops from SQLite.", exception);
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
                resultSet.getString("world_name"),
                getNullableDouble(resultSet, "virtual_x"),
                getNullableDouble(resultSet, "virtual_y"),
                getNullableDouble(resultSet, "virtual_z"),
                resultSet.getString("target_world_name"),
                getNullableDouble(resultSet, "target_x"),
                getNullableDouble(resultSet, "target_y"),
                getNullableDouble(resultSet, "target_z"),
                parseNullableInstant(resultSet.getString("movement_started_at")),
                parseNullableInstant(resultSet.getString("movement_updated_at")),
                resultSet.getDouble("speed_blocks_per_second"),
                getNullableInteger(resultSet, "eta_seconds"),
                resultSet.getInt("physical_spawned") == 1,
                resultSet.getString("home_world_name"),
                getNullableDouble(resultSet, "home_x"),
                getNullableDouble(resultSet, "home_y"),
                getNullableDouble(resultSet, "home_z"),
                getNullableInteger(resultSet, "current_route_stop_index"),
                resultSet.getInt("route_running") == 1,
                parseNullableInstant(resultSet.getString("current_stop_started_at")),
                parseNullableInstant(resultSet.getString("current_stop_ends_at")),
                resultSet.getInt("returning_home_after_route") == 1,
                resultSet.getInt("route_loop_enabled") == 1,
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

    private void ensureCaravanColumns(Statement statement) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (ResultSet resultSet = statement.executeQuery("PRAGMA table_info(caravans)")) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("name"));
            }
        }

        addCaravanColumnIfMissing(statement, columns, "world_name", "ALTER TABLE caravans ADD COLUMN world_name TEXT");
        addCaravanColumnIfMissing(statement, columns, "virtual_x", "ALTER TABLE caravans ADD COLUMN virtual_x REAL");
        addCaravanColumnIfMissing(statement, columns, "virtual_y", "ALTER TABLE caravans ADD COLUMN virtual_y REAL");
        addCaravanColumnIfMissing(statement, columns, "virtual_z", "ALTER TABLE caravans ADD COLUMN virtual_z REAL");
        addCaravanColumnIfMissing(statement, columns, "target_world_name", "ALTER TABLE caravans ADD COLUMN target_world_name TEXT");
        addCaravanColumnIfMissing(statement, columns, "target_x", "ALTER TABLE caravans ADD COLUMN target_x REAL");
        addCaravanColumnIfMissing(statement, columns, "target_y", "ALTER TABLE caravans ADD COLUMN target_y REAL");
        addCaravanColumnIfMissing(statement, columns, "target_z", "ALTER TABLE caravans ADD COLUMN target_z REAL");
        addCaravanColumnIfMissing(statement, columns, "movement_started_at", "ALTER TABLE caravans ADD COLUMN movement_started_at TEXT");
        addCaravanColumnIfMissing(statement, columns, "movement_updated_at", "ALTER TABLE caravans ADD COLUMN movement_updated_at TEXT");
        addCaravanColumnIfMissing(statement, columns, "speed_blocks_per_second", "ALTER TABLE caravans ADD COLUMN speed_blocks_per_second REAL NOT NULL DEFAULT 1.5");
        addCaravanColumnIfMissing(statement, columns, "eta_seconds", "ALTER TABLE caravans ADD COLUMN eta_seconds INTEGER");
        addCaravanColumnIfMissing(statement, columns, "physical_spawned", "ALTER TABLE caravans ADD COLUMN physical_spawned INTEGER NOT NULL DEFAULT 0");
        addCaravanColumnIfMissing(statement, columns, "home_world_name", "ALTER TABLE caravans ADD COLUMN home_world_name TEXT");
        addCaravanColumnIfMissing(statement, columns, "home_x", "ALTER TABLE caravans ADD COLUMN home_x REAL");
        addCaravanColumnIfMissing(statement, columns, "home_y", "ALTER TABLE caravans ADD COLUMN home_y REAL");
        addCaravanColumnIfMissing(statement, columns, "home_z", "ALTER TABLE caravans ADD COLUMN home_z REAL");
        addCaravanColumnIfMissing(statement, columns, "current_route_stop_index", "ALTER TABLE caravans ADD COLUMN current_route_stop_index INTEGER");
        addCaravanColumnIfMissing(statement, columns, "route_running", "ALTER TABLE caravans ADD COLUMN route_running INTEGER NOT NULL DEFAULT 0");
        addCaravanColumnIfMissing(statement, columns, "current_stop_started_at", "ALTER TABLE caravans ADD COLUMN current_stop_started_at TEXT");
        addCaravanColumnIfMissing(statement, columns, "current_stop_ends_at", "ALTER TABLE caravans ADD COLUMN current_stop_ends_at TEXT");
        addCaravanColumnIfMissing(statement, columns, "returning_home_after_route", "ALTER TABLE caravans ADD COLUMN returning_home_after_route INTEGER NOT NULL DEFAULT 0");
        addCaravanColumnIfMissing(statement, columns, "route_loop_enabled", "ALTER TABLE caravans ADD COLUMN route_loop_enabled INTEGER NOT NULL DEFAULT 0");
    }

    private void addCaravanColumnIfMissing(Statement statement, Set<String> existingColumns, String column, String sql) throws SQLException {
        if (!existingColumns.contains(column)) {
            statement.executeUpdate(sql);
        }
    }

    private void setNullableDouble(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.REAL);
        } else {
            statement.setDouble(index, value);
        }
    }

    private void setNullableInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private void setNullableInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value.toString());
        }
    }

    private Double getNullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private Integer getNullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private Instant parseNullableInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}
