package net.meltarion.caravans.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.model.CaravanRecord;
import org.bukkit.entity.Player;

public final class InMemoryCaravanService implements CaravanService {

    private final ConfigManager configManager;
    private final Map<UUID, List<CaravanRecord>> caravansByOwner = new ConcurrentHashMap<>();

    public InMemoryCaravanService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public CaravanCreationResult createCaravan(Player owner, String requestedName) {
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

        CaravanRecord caravanRecord = new CaravanRecord(UUID.randomUUID(), ownerId, normalizedName, Instant.now());
        caravans.add(caravanRecord);
        caravans.sort(Comparator.comparing(CaravanRecord::createdAt));
        return CaravanCreationResult.success(caravanRecord, caravanLimit);
    }

    @Override
    public List<CaravanRecord> getCaravans(UUID ownerId) {
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
}
