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

public final class SQLiteCaravanStorage implements CaravanStorage {

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
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.executeUpdate(CREATE_CARAVANS_TABLE);
                statement.executeUpdate(CREATE_OWNER_INDEX);
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

    private void ensureInitialized() throws StorageException {
        if (connection == null) {
            throw new StorageException("SQLite storage has not been initialized.");
        }
    }
}
