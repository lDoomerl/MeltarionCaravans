package net.meltarion.caravans.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.model.CaravanStatus;
import net.meltarion.caravans.storage.CaravanStorage;
import net.meltarion.caravans.storage.StorageException;
import org.bukkit.entity.Player;

public final class PersistentCaravanService implements CaravanService {

    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("[A-Za-z0-9 _-]+");
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("(?i)(?:&|\u00A7)[0-9A-FK-OR]");

    private final ConfigManager configManager;
    private final CaravanStorage storage;
    private final CaravanInventoryService inventoryService;
    private final TradeOperationService tradeOperationService;
    private final CaravanLicenseService caravanLicenseService;
    private final Logger logger;
    private final Map<UUID, List<CaravanRecord>> caravansByOwner = new ConcurrentHashMap<>();

    public PersistentCaravanService(
        ConfigManager configManager,
        CaravanStorage storage,
        CaravanInventoryService inventoryService,
        TradeOperationService tradeOperationService,
        CaravanLicenseService caravanLicenseService,
        Logger logger
    ) {
        this.configManager = configManager;
        this.storage = storage;
        this.inventoryService = inventoryService;
        this.tradeOperationService = tradeOperationService;
        this.caravanLicenseService = caravanLicenseService;
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
    public synchronized CaravanCreationResult createDefaultCaravan(Player owner) {
        return createCaravan(owner, null);
    }

    @Override
    public synchronized CaravanCreationResult createNamedCaravan(Player owner, String requestedName) {
        return createCaravan(owner, requestedName);
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
    public synchronized List<CaravanRecord> getCaravansByOwnerName(String ownerName) {
        if (ownerName == null || ownerName.isBlank()) {
            return List.of();
        }

        String normalized = ownerName.trim();
        List<CaravanRecord> caravans = new ArrayList<>();
        for (List<CaravanRecord> records : caravansByOwner.values()) {
            for (CaravanRecord record : records) {
                if (record.ownerName().equalsIgnoreCase(normalized)) {
                    caravans.add(record);
                }
            }
        }
        caravans.sort(Comparator.comparing(CaravanRecord::createdAt));
        return List.copyOf(caravans);
    }

    @Override
    public synchronized List<CaravanRecord> getAllCaravans() {
        return caravansByOwner.values().stream()
            .flatMap(List::stream)
            .sorted(Comparator.comparing(CaravanRecord::createdAt))
            .toList();
    }

    @Override
    public synchronized CaravanLookupResult findCaravanForOwner(UUID ownerId, String reference) {
        return findCaravanInternal(getCaravans(ownerId), reference);
    }

    @Override
    public synchronized CaravanLookupResult findCaravan(String reference) {
        List<CaravanRecord> allCaravans = caravansByOwner.values().stream()
            .flatMap(List::stream)
            .toList();
        return findCaravanInternal(allCaravans, reference);
    }

    @Override
    public synchronized CaravanRecord getCaravan(UUID caravanId) {
        return caravansByOwner.values().stream()
            .flatMap(List::stream)
            .filter(caravan -> caravan.id().equals(caravanId))
            .findFirst()
            .orElse(null);
    }

    @Override
    public synchronized CaravanMutationResult renameCaravan(CaravanRecord caravan, String requestedName) {
        String sanitizedName = sanitizeName(requestedName);
        if (!isValidName(sanitizedName)) {
            return CaravanMutationResult.failure(CaravanMutationResult.FailureReason.INVALID_NAME);
        }

        List<CaravanRecord> ownerCaravans = caravansByOwner.getOrDefault(caravan.ownerId(), List.of());
        boolean duplicateExists = ownerCaravans.stream()
            .filter(existing -> !existing.id().equals(caravan.id()))
            .anyMatch(existing -> existing.name().equalsIgnoreCase(sanitizedName));
        if (duplicateExists) {
            return CaravanMutationResult.failure(CaravanMutationResult.FailureReason.DUPLICATE_NAME);
        }

        Instant updatedAt = Instant.now();
        CaravanRecord updatedRecord = caravan.withName(sanitizedName, updatedAt);

        try {
            storage.renameCaravan(caravan.id(), sanitizedName, updatedAt.toString());
        } catch (StorageException exception) {
            logger.log(Level.SEVERE, "Failed to rename caravan " + caravan.id() + '.', exception);
            return CaravanMutationResult.failure(CaravanMutationResult.FailureReason.STORAGE_ERROR);
        }

        replaceCachedCaravan(updatedRecord);
        return CaravanMutationResult.success(updatedRecord);
    }

    @Override
    public synchronized CaravanMutationResult deleteCaravan(CaravanRecord caravan) {
        try {
            storage.deleteCaravanData(caravan.id());
            inventoryService.discardOpenInventory(caravan.id());
            tradeOperationService.discardCaravanState(caravan.id());
        } catch (StorageException exception) {
            logger.log(Level.SEVERE, "Failed to delete caravan " + caravan.id() + '.', exception);
            return CaravanMutationResult.failure(CaravanMutationResult.FailureReason.STORAGE_ERROR);
        }

        removeCachedCaravan(caravan);
        return CaravanMutationResult.success(caravan);
    }

    @Override
    public synchronized CaravanMutationResult updateCaravanRecord(CaravanRecord caravan) {
        try {
            storage.updateCaravan(caravan);
        } catch (StorageException exception) {
            logger.log(Level.SEVERE, "Failed to update caravan " + caravan.id() + '.', exception);
            return CaravanMutationResult.failure(CaravanMutationResult.FailureReason.STORAGE_ERROR);
        }

        replaceCachedCaravan(caravan);
        return CaravanMutationResult.success(caravan);
    }

    @Override
    public synchronized CaravanMutationResult updateCaravanHealthAndStatus(CaravanRecord caravan, int hp, CaravanStatus status) {
        int normalizedHp = Math.max(0, Math.min(hp, caravan.maxHp()));
        CaravanStatus normalizedStatus = status == null ? caravan.status() : status;
        Instant updatedAt = Instant.now();
        CaravanRecord updatedRecord = caravan.withHealthAndStatus(normalizedHp, normalizedStatus, updatedAt);

        try {
            storage.updateCaravanState(caravan.id(), normalizedStatus.name(), normalizedHp, updatedAt.toString());
        } catch (StorageException exception) {
            logger.log(Level.SEVERE, "Failed to update caravan state for " + caravan.id() + '.', exception);
            return CaravanMutationResult.failure(CaravanMutationResult.FailureReason.STORAGE_ERROR);
        }

        replaceCachedCaravan(updatedRecord);
        return CaravanMutationResult.success(updatedRecord);
    }

    @Override
    public synchronized boolean caravanExists(UUID caravanId) {
        return caravansByOwner.values().stream()
            .flatMap(List::stream)
            .anyMatch(caravan -> caravan.id().equals(caravanId));
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

    @Override
    public String getShortId(CaravanRecord caravan) {
        return caravan.id().toString().substring(0, 8);
    }

    private CaravanCreationResult createCaravan(Player owner, String requestedName) {
        if (!caravanLicenseService.isEnabled()) {
            return CaravanCreationResult.failure(CaravanCreationResult.FailureReason.LICENSE_DISABLED, getCaravanLimit(owner));
        }

        UUID ownerId = owner.getUniqueId();
        List<CaravanRecord> ownerCaravans = caravansByOwner.computeIfAbsent(ownerId, ignored -> new ArrayList<>());

        String resolvedName = requestedName == null ? generateDefaultName(ownerCaravans) : sanitizeName(requestedName);
        if (requestedName != null && !isValidName(resolvedName)) {
            return CaravanCreationResult.failure(CaravanCreationResult.FailureReason.INVALID_NAME, getCaravanLimit(owner));
        }

        int caravanLimit = getCaravanLimit(owner);
        if (ownerCaravans.size() >= caravanLimit) {
            return CaravanCreationResult.failure(CaravanCreationResult.FailureReason.LIMIT_REACHED, caravanLimit);
        }

        if (!caravanLicenseService.hasLicense(owner)) {
            return CaravanCreationResult.failure(CaravanCreationResult.FailureReason.MISSING_LICENSE, caravanLimit);
        }

        boolean duplicateExists = ownerCaravans.stream()
            .anyMatch(record -> record.name().equalsIgnoreCase(resolvedName));
        if (duplicateExists) {
            return CaravanCreationResult.failure(CaravanCreationResult.FailureReason.DUPLICATE_NAME, caravanLimit);
        }

        Instant now = Instant.now();
        int maxHp = configManager.getCaravanMaxHp();
        CaravanRecord caravanRecord = new CaravanRecord(
            UUID.randomUUID(),
            ownerId,
            owner.getName() == null ? ownerId.toString() : owner.getName(),
            resolvedName,
            CaravanStatus.IDLE,
            maxHp,
            maxHp,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            configManager.getDefaultMovementSpeedBlocksPerSecond(),
            null,
            false,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            null,
            false,
            false,
            now,
            now
        );

        try {
            storage.insertCaravan(caravanRecord);
            inventoryService.initializeInventory(caravanRecord);
        } catch (StorageException exception) {
            logger.log(Level.SEVERE, "Failed to persist caravan '" + resolvedName + "' for player " + owner.getName() + '.', exception);
            try {
                storage.deleteCaravanData(caravanRecord.id());
            } catch (StorageException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            return CaravanCreationResult.failure(CaravanCreationResult.FailureReason.STORAGE_ERROR, caravanLimit);
        }

        ownerCaravans.add(caravanRecord);
        sortCaravans(ownerCaravans);

        if (caravanLicenseService.shouldConsumeOnCreate() && !caravanLicenseService.consumeOneLicense(owner)) {
            logger.warning("Failed to consume Caravan License after creating caravan " + caravanRecord.id() + ". Rolling back.");
            CaravanMutationResult rollbackResult = deleteCaravan(caravanRecord);
            if (!rollbackResult.success()) {
                logger.severe("Rollback failed for caravan " + caravanRecord.id() + ". Manual cleanup may be required.");
                return CaravanCreationResult.failure(CaravanCreationResult.FailureReason.STORAGE_ERROR, caravanLimit);
            }
            return CaravanCreationResult.failure(CaravanCreationResult.FailureReason.LICENSE_CONSUME_FAILED, caravanLimit);
        }

        return CaravanCreationResult.success(caravanRecord, caravanLimit);
    }

    private CaravanLookupResult findCaravanInternal(List<CaravanRecord> candidates, String reference) {
        if (reference == null || reference.isBlank()) {
            return CaravanLookupResult.failure(CaravanLookupResult.FailureReason.NOT_FOUND);
        }

        String normalizedReference = reference.trim().toLowerCase(Locale.ROOT);
        List<CaravanRecord> matches = candidates.stream()
            .filter(caravan -> matchesReference(caravan, normalizedReference))
            .toList();

        if (matches.isEmpty()) {
            return CaravanLookupResult.failure(CaravanLookupResult.FailureReason.NOT_FOUND);
        }
        if (matches.size() > 1) {
            return CaravanLookupResult.failure(CaravanLookupResult.FailureReason.AMBIGUOUS);
        }
        return CaravanLookupResult.success(matches.getFirst());
    }

    private boolean matchesReference(CaravanRecord caravan, String reference) {
        String fullId = caravan.id().toString().toLowerCase(Locale.ROOT);
        if (fullId.equals(reference)) {
            return true;
        }
        return reference.length() == 8 && fullId.substring(0, 8).equals(reference);
    }

    private String generateDefaultName(List<CaravanRecord> ownerCaravans) {
        int number = 1;
        while (true) {
            String candidate = "Caravan " + number;
            boolean exists = ownerCaravans.stream()
                .anyMatch(caravan -> caravan.name().equalsIgnoreCase(candidate));
            if (!exists) {
                return candidate;
            }
            number++;
        }
    }

    private String sanitizeName(String requestedName) {
        String stripped = COLOR_CODE_PATTERN.matcher(requestedName == null ? "" : requestedName).replaceAll("");
        return stripped.trim();
    }

    private boolean isValidName(String name) {
        return !name.isEmpty()
            && name.length() <= configManager.getMaxCaravanNameLength()
            && VALID_NAME_PATTERN.matcher(name).matches();
    }

    private void replaceCachedCaravan(CaravanRecord updatedRecord) {
        List<CaravanRecord> ownerCaravans = caravansByOwner.get(updatedRecord.ownerId());
        if (ownerCaravans == null) {
            return;
        }

        for (int index = 0; index < ownerCaravans.size(); index++) {
            if (ownerCaravans.get(index).id().equals(updatedRecord.id())) {
                ownerCaravans.set(index, updatedRecord);
                break;
            }
        }
        sortCaravans(ownerCaravans);
    }

    private void removeCachedCaravan(CaravanRecord caravan) {
        List<CaravanRecord> ownerCaravans = caravansByOwner.get(caravan.ownerId());
        if (ownerCaravans == null) {
            return;
        }

        ownerCaravans.removeIf(existing -> existing.id().equals(caravan.id()));
        if (ownerCaravans.isEmpty()) {
            caravansByOwner.remove(caravan.ownerId());
        }
    }

    private void sortCaravans(List<CaravanRecord> caravans) {
        caravans.sort(Comparator.comparing(CaravanRecord::createdAt));
    }
}
