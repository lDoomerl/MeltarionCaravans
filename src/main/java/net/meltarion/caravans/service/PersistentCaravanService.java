package net.meltarion.caravans.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.model.CaravanStatus;
import net.meltarion.caravans.storage.CaravanStorage;
import net.meltarion.caravans.storage.StorageException;
import org.bukkit.entity.Player;

public final class PersistentCaravanService implements CaravanService {

    private final ConfigManager configManager;
    private final CaravanStorage storage;
    private final Logger logger;
    private final Map<UUID, List<CaravanRecord>> caravansByOwner = new ConcurrentHashMap<>();

    public PersistentCaravanService(ConfigManager configManager, CaravanStorage storage, Logger logger) {
        this.configManager = configManager;
        this.storage = storage;
        this.logger = logger;
    }

    public void loadCaravans() throws StorageException {
        caravansByOwner.clear();

        for (CaravanRecord caravan : storage.loadAllCaravans()) {
            caravansByOwner
                .computeIfAbsent(caravan.ownerId(), ignored -> new ArrayList<>())
                .add(caravan);
        }

        caravansByOwner.values().forEach(this::sortCaravans);
        logger.info("Loaded " + caravansByOwner.values().stream().mapToInt(List::size).sum() + " caravans from storage.");
    }

    @Override
    public synchronized CaravanCreationResult createCaravan(Player owner, String requestedName) {
        String normalizedName = requestedName == null ? "" : requestedName.trim();
        if (normalizedName.isEmpty()) {
            return CaravanCreationResult.failure(CaravanCreationResult.FailureReason.INVALID_NAME, getCaravanLimit(owner));
        }

        UUID ownerId = owner.getUniqueId();
        List<CaravanRecord> caravans = caravansByOwner.computeIfAbsent(ownerId, ignored -> new ArrayList<>());
        int caravanLimit = getCaravanLimit(owner);
        if (caravans.size() >= caravanLimit) {
            return CaravanCreationResult.failure(CaravanCreationResult.FailureReason.LIMIT_REACHED, caravanLimit);
        }

        boolean duplicateExists = caravans.stream()
            .anyMatch(record -> record.name().equalsIgnoreCase(normalizedName));
        if (duplicateExists) {
            return CaravanCreationResult.failure(CaravanCreationResult.FailureReason.DUPLICATE_NAME, caravanLimit);
        }

        Instant now = Instant.now();
        int maxHp = configManager.getCaravanMaxHp();
        CaravanRecord caravanRecord = new CaravanRecord(
            UUID.randomUUID(),
            ownerId,
            owner.getName() == null ? ownerId.toString() : owner.getName(),
            normalizedName,
            CaravanStatus.IDLE,
            maxHp,
            maxHp,
            now,
            now
        );

        try {
            storage.insertCaravan(caravanRecord);
        } catch (StorageException exception) {
            logger.log(Level.SEVERE, "Failed to persist caravan '" + normalizedName + "' for player " + owner.getName() + '.', exception);
            return CaravanCreationResult.failure(CaravanCreationResult.FailureReason.STORAGE_ERROR, caravanLimit);
        }

        caravans.add(caravanRecord);
        sortCaravans(caravans);
        return CaravanCreationResult.success(caravanRecord, caravanLimit);
    }

    @Override
    public synchronized List<CaravanRecord> getCaravans(UUID ownerId) {
        List<CaravanRecord> caravans = caravansByOwner.get(ownerId);
        if (caravans == null) {
            return List.of();
        }
        return List.copyOf(caravans);
    }

    @Override
    public int getCaravanLimit(Player player) {
        int limit = configManager.getDefaultCaravanLimit();
        for (Map.Entry<String, Integer> entry : configManager.getPermissionLimits().entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                limit = Math.max(limit, entry.getValue());
            }
        }
        return limit;
    }

    private void sortCaravans(List<CaravanRecord> caravans) {
        caravans.sort(Comparator.comparing(CaravanRecord::createdAt));
    }
}
