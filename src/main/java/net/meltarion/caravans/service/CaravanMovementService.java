package net.meltarion.caravans.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.model.CaravanStatus;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class CaravanMovementService {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final CaravanService caravanService;
    private final CaravanEntityService caravanEntityService;
    private final MessageService messageService;
    private final Logger logger;
    private final Map<UUID, CaravanRecord> runtimeCaravans = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyCaravans = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Instant> lastProjectionSpawnAt = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> lastProjectionDespawnAt = new ConcurrentHashMap<>();

    private BukkitTask movementTask;
    private Instant lastPeriodicSaveAt;

    public CaravanMovementService(
        Plugin plugin,
        ConfigManager configManager,
        CaravanService caravanService,
        CaravanEntityService caravanEntityService,
        MessageService messageService,
        Logger logger
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.caravanService = caravanService;
        this.caravanEntityService = caravanEntityService;
        this.messageService = messageService;
        this.logger = logger;
    }

    public void initialize() {
        runtimeCaravans.clear();
        for (CaravanRecord caravan : caravanService.getAllCaravans()) {
            runtimeCaravans.put(caravan.id(), normalizeLoadedCaravan(caravan));
        }
        this.lastPeriodicSaveAt = Instant.now();

        if (!configManager.isMovementEnabled()) {
            logger.info("Caravan movement is disabled in config.");
            return;
        }

        long periodTicks = configManager.getMovementTickIntervalSeconds() * 20L;
        this.movementTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, periodTicks, periodTicks);
    }

    public void shutdown() {
        if (movementTask != null) {
            movementTask.cancel();
            movementTask = null;
        }
        if (configManager.shouldDespawnPhysicalCaravansOnDisable()) {
            Instant now = Instant.now();
            for (Map.Entry<UUID, CaravanRecord> entry : runtimeCaravans.entrySet()) {
                if (entry.getValue().physicalSpawned()) {
                    CaravanRecord updated = entry.getValue().withPhysicalSpawned(false, now);
                    runtimeCaravans.put(entry.getKey(), updated);
                    dirtyCaravans.add(entry.getKey());
                }
            }
        }
        saveDirtyCaravans(true);
        lastProjectionSpawnAt.clear();
        lastProjectionDespawnAt.clear();
    }

    public CaravanRecord getRuntimeCaravan(UUID caravanId) {
        CaravanRecord runtime = runtimeCaravans.get(caravanId);
        return runtime == null ? caravanService.getCaravan(caravanId) : runtime;
    }

    public CaravanMovementResult setManualPosition(CaravanRecord caravan, Location location, boolean assignHomeIfMissing, boolean markPhysicalSpawned) {
        World world = location.getWorld();
        if (world == null) {
            return CaravanMovementResult.failure(CaravanMovementResult.FailureReason.INVALID_TARGET);
        }

        CaravanRecord current = getRuntimeCaravan(caravan.id());
        if (current == null) {
            return CaravanMovementResult.failure(CaravanMovementResult.FailureReason.INVALID_TARGET);
        }

        double y = resolveSafeY(world, location.getX(), location.getZ(), location.getY());
        Instant now = Instant.now();
        CaravanStatus status = current.status() == CaravanStatus.TRAVELING || current.status() == CaravanStatus.RETURNING
            ? CaravanStatus.STOPPED
            : current.status();

        CaravanRecord updated = current.withMovement(
            status,
            world.getName(),
            location.getX(),
            y,
            location.getZ(),
            null,
            null,
            null,
            null,
            null,
            now,
            current.speedBlocksPerSecond() > 0.0D ? current.speedBlocksPerSecond() : configManager.getDefaultMovementSpeedBlocksPerSecond(),
            null,
            markPhysicalSpawned,
            now
        );

        if (assignHomeIfMissing && !updated.hasHomePosition()) {
            updated = updated.withHomePosition(world.getName(), location.getX(), y, location.getZ(), now);
        }

        if (!persistRuntime(updated)) {
            return CaravanMovementResult.failure(CaravanMovementResult.FailureReason.STORAGE_ERROR);
        }
        return CaravanMovementResult.success(updated);
    }

    public CaravanMovementResult startMovement(CaravanRecord caravan, World targetWorld, double targetX, double targetZ, CaravanStatus status) {
        if (!configManager.isMovementEnabled()) {
            return CaravanMovementResult.failure(CaravanMovementResult.FailureReason.DISABLED);
        }
        if (targetWorld == null || !Double.isFinite(targetX) || !Double.isFinite(targetZ)) {
            return CaravanMovementResult.failure(CaravanMovementResult.FailureReason.INVALID_TARGET);
        }

        CaravanRecord current = getRuntimeCaravan(caravan.id());
        if (current == null || !current.hasVirtualPosition()) {
            return CaravanMovementResult.failure(CaravanMovementResult.FailureReason.NO_POSITION);
        }

        String currentWorldName = current.worldName();
        Double currentX = current.virtualX();
        Double currentY = current.virtualY();
        Double currentZ = current.virtualZ();
        if (currentWorldName == null || currentX == null || currentY == null || currentZ == null) {
            return CaravanMovementResult.failure(CaravanMovementResult.FailureReason.NO_POSITION);
        }

        if (!targetWorld.getName().equals(currentWorldName)) {
            currentWorldName = targetWorld.getName();
        }

        double resolvedTargetY = resolveTargetY(targetWorld, targetX, targetZ, currentY);
        double speed = configManager.getDefaultMovementSpeedBlocksPerSecond();
        Instant now = Instant.now();
        CaravanRecord updated = current.withMovement(
            status,
            currentWorldName,
            currentX,
            currentY,
            currentZ,
            targetWorld.getName(),
            targetX,
            resolvedTargetY,
            targetZ,
            now,
            now,
            speed,
            calculateEtaSeconds(currentX, currentY, currentZ, targetX, resolvedTargetY, targetZ, speed),
            false,
            now
        );

        caravanEntityService.despawnCaravan(current.id());
        runtimeCaravans.put(updated.id(), updated);
        dirtyCaravans.add(updated.id());

        if (!persistRuntime(updated)) {
            return CaravanMovementResult.failure(CaravanMovementResult.FailureReason.STORAGE_ERROR);
        }
        return CaravanMovementResult.success(updated);
    }

    public CaravanMovementResult stopMovement(CaravanRecord caravan) {
        CaravanRecord current = getRuntimeCaravan(caravan.id());
        if (current == null || !current.hasVirtualPosition()) {
            return CaravanMovementResult.failure(CaravanMovementResult.FailureReason.NO_POSITION);
        }

        Instant now = Instant.now();
        CaravanRecord updated = current.withMovement(
            CaravanStatus.STOPPED,
            current.worldName(),
            current.virtualX(),
            current.virtualY(),
            current.virtualZ(),
            null,
            null,
            null,
            null,
            current.movementStartedAt(),
            now,
            current.speedBlocksPerSecond() > 0.0D ? current.speedBlocksPerSecond() : configManager.getDefaultMovementSpeedBlocksPerSecond(),
            null,
            current.physicalSpawned(),
            now
        );

        if (!persistRuntime(updated)) {
            return CaravanMovementResult.failure(CaravanMovementResult.FailureReason.STORAGE_ERROR);
        }
        return CaravanMovementResult.success(updated);
    }

    public CaravanMovementResult returnHome(CaravanRecord caravan) {
        CaravanRecord current = getRuntimeCaravan(caravan.id());
        if (current == null || !current.hasHomePosition()) {
            return CaravanMovementResult.failure(CaravanMovementResult.FailureReason.HOME_MISSING);
        }

        World homeWorld = Bukkit.getWorld(current.homeWorldName());
        if (homeWorld == null) {
            return CaravanMovementResult.failure(CaravanMovementResult.FailureReason.INVALID_TARGET);
        }

        return startMovement(current, homeWorld, current.homeX(), current.homeZ(), CaravanStatus.RETURNING);
    }

    public CaravanRecord handleAttacked(CaravanRecord caravan) {
        CaravanRecord current = getRuntimeCaravan(caravan.id());
        if (current == null) {
            return caravan;
        }

        Instant now = Instant.now();
        CaravanRecord updated = new CaravanRecord(
            caravan.id(),
            caravan.ownerId(),
            caravan.ownerName(),
            caravan.name(),
            CaravanStatus.ATTACKED,
            caravan.hp(),
            caravan.maxHp(),
            current.worldName(),
            current.virtualX(),
            current.virtualY(),
            current.virtualZ(),
            current.targetWorldName(),
            current.targetX(),
            current.targetY(),
            current.targetZ(),
            current.movementStartedAt(),
            now,
            current.speedBlocksPerSecond(),
            current.etaSeconds(),
            current.physicalSpawned(),
            current.homeWorldName(),
            current.homeX(),
            current.homeY(),
            current.homeZ(),
            caravan.createdAt(),
            now
        );

        persistRuntime(updated);
        return updated;
    }

    public CaravanRecord markPhysicalProjection(CaravanRecord caravan, boolean physicalSpawned) {
        CaravanRecord current = getRuntimeCaravan(caravan.id());
        if (current == null) {
            return caravan;
        }

        CaravanRecord updated = current.withPhysicalSpawned(physicalSpawned, Instant.now());
        persistRuntime(updated);
        return updated;
    }

    public void removeRuntimeCaravan(UUID caravanId) {
        runtimeCaravans.remove(caravanId);
        dirtyCaravans.remove(caravanId);
        lastProjectionSpawnAt.remove(caravanId);
        lastProjectionDespawnAt.remove(caravanId);
    }

    private void tick() {
        Instant now = Instant.now();
        double deltaSeconds = configManager.getMovementTickIntervalSeconds();
        List<CaravanRecord> snapshot = new ArrayList<>(runtimeCaravans.values());
        caravanEntityService.cleanupAllTrackedEntities();

        for (CaravanRecord caravan : snapshot) {
            CaravanRecord updated = caravan;
            if ((caravan.status() == CaravanStatus.TRAVELING || caravan.status() == CaravanStatus.RETURNING)
                && caravan.hasVirtualPosition()
                && caravan.hasTargetPosition()) {
                updated = advanceMovement(caravan, deltaSeconds, now);
                runtimeCaravans.put(updated.id(), updated);
                dirtyCaravans.add(updated.id());
            }

            CaravanRecord projected = updateProjection(updated, now);
            if (projected != updated) {
                runtimeCaravans.put(projected.id(), projected);
                dirtyCaravans.add(projected.id());
            }
        }

        if (Duration.between(lastPeriodicSaveAt, now).getSeconds() >= configManager.getMovementDatabaseSaveIntervalSeconds()) {
            saveDirtyCaravans(false);
            lastPeriodicSaveAt = now;
        }
    }

    private CaravanRecord advanceMovement(CaravanRecord caravan, double deltaSeconds, Instant now) {
        World world = Bukkit.getWorld(caravan.worldName());
        if (world == null || caravan.virtualX() == null || caravan.virtualY() == null || caravan.virtualZ() == null
            || caravan.targetX() == null || caravan.targetY() == null || caravan.targetZ() == null) {
            return caravan;
        }

        Vector current = new Vector(caravan.virtualX(), caravan.virtualY(), caravan.virtualZ());
        Vector target = new Vector(caravan.targetX(), caravan.targetY(), caravan.targetZ());
        Vector direction = target.clone().subtract(current);
        double distance = direction.length();
        if (distance <= configManager.getMovementArrivalDistance()) {
            return arrive(caravan, now);
        }

        double step = Math.max(0.01D, caravan.speedBlocksPerSecond()) * deltaSeconds;
        if (step >= distance) {
            return arrive(caravan, now);
        }

        Vector normalized = direction.normalize().multiply(step);
        Vector updatedPosition = current.clone().add(normalized);
        double updatedY = adjustY(world, updatedPosition.getX(), updatedPosition.getY(), updatedPosition.getZ());
        return caravan.withMovement(
            caravan.status(),
            world.getName(),
            updatedPosition.getX(),
            updatedY,
            updatedPosition.getZ(),
            caravan.targetWorldName(),
            caravan.targetX(),
            caravan.targetY(),
            caravan.targetZ(),
            caravan.movementStartedAt(),
            now,
            caravan.speedBlocksPerSecond(),
            calculateEtaSeconds(updatedPosition.getX(), updatedY, updatedPosition.getZ(), caravan.targetX(), caravan.targetY(), caravan.targetZ(), caravan.speedBlocksPerSecond()),
            caravan.physicalSpawned(),
            now
        );
    }

    private CaravanRecord arrive(CaravanRecord caravan, Instant now) {
        CaravanRecord arrived = caravan.withMovement(
            CaravanStatus.STOPPED,
            caravan.targetWorldName(),
            caravan.targetX(),
            caravan.targetY(),
            caravan.targetZ(),
            null,
            null,
            null,
            null,
            caravan.movementStartedAt(),
            now,
            caravan.speedBlocksPerSecond(),
            0,
            caravan.physicalSpawned(),
            now
        );
        persistRuntime(arrived);
        Player owner = Bukkit.getPlayer(arrived.ownerId());
        if (owner != null && owner.isOnline()) {
            messageService.send(owner, "movement-arrived", placeholders(arrived));
        }
        return arrived;
    }

    private CaravanRecord updateProjection(CaravanRecord caravan, Instant now) {
        if (!caravan.hasVirtualPosition() || caravan.worldName() == null) {
            return caravan;
        }
        World world = Bukkit.getWorld(caravan.worldName());
        if (world == null) {
            return caravan;
        }

        Location location = new Location(world, caravan.virtualX(), caravan.virtualY(), caravan.virtualZ());
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        boolean chunkLoaded = world.isChunkLoaded(chunkX, chunkZ);

        double spawnDistanceSquared = Math.pow(configManager.getPhysicalProjectionRadius(), 2);
        double despawnDistanceSquared = Math.pow(configManager.getPhysicalDespawnRadius(), 2);
        boolean hasNearbyPlayers = world.getPlayers().stream()
            .filter(Player::isOnline)
            .anyMatch(player -> player.getLocation().distanceSquared(location) <= spawnDistanceSquared);
        boolean hasDistantPlayers = world.getPlayers().stream()
            .filter(Player::isOnline)
            .anyMatch(player -> player.getLocation().distanceSquared(location) <= despawnDistanceSquared);

        boolean actuallySpawned = caravanEntityService.isSpawned(caravan.id());
        if (hasNearbyPlayers && chunkLoaded && !actuallySpawned && canSpawnProjection(caravan.id(), now)) {
            PhysicalSpawnResult result = caravanEntityService.spawnCaravan(caravan, location);
            if (result.success()) {
                lastProjectionSpawnAt.put(caravan.id(), now);
                CaravanRecord updated = caravan.withPhysicalSpawned(true, now);
                if (configManager.isMovementDebugEnabled()) {
                    Player owner = Bukkit.getPlayer(updated.ownerId());
                    if (owner != null && owner.isOnline()) {
                        messageService.send(owner, "projection-spawned", placeholders(updated));
                    }
                }
                return updated;
            }
        }

        if (actuallySpawned && !hasDistantPlayers && canDespawnProjection(caravan.id(), now)) {
            caravanEntityService.despawnCaravan(caravan.id());
            lastProjectionDespawnAt.put(caravan.id(), now);
            CaravanRecord updated = caravan.withPhysicalSpawned(false, now);
            if (configManager.isMovementDebugEnabled()) {
                Player owner = Bukkit.getPlayer(updated.ownerId());
                if (owner != null && owner.isOnline()) {
                    messageService.send(owner, "projection-despawned", placeholders(updated));
                }
            }
            return updated;
        }

        if (actuallySpawned && hasDistantPlayers && chunkLoaded) {
            caravanEntityService.syncCaravanProjection(caravan.id(), location);
            if (!caravan.physicalSpawned()) {
                return caravan.withPhysicalSpawned(true, now);
            }
        }

        if (!actuallySpawned && caravan.physicalSpawned()) {
            return caravan.withPhysicalSpawned(false, now);
        }

        return caravan;
    }

    private boolean canSpawnProjection(UUID caravanId, Instant now) {
        Instant lastDespawn = lastProjectionDespawnAt.get(caravanId);
        return lastDespawn == null
            || Duration.between(lastDespawn, now).getSeconds() >= configManager.getProjectionSpawnCooldownSeconds();
    }

    private boolean canDespawnProjection(UUID caravanId, Instant now) {
        Instant lastSpawn = lastProjectionSpawnAt.get(caravanId);
        return lastSpawn == null
            || Duration.between(lastSpawn, now).getSeconds() >= configManager.getProjectionDespawnCooldownSeconds();
    }

    private double resolveTargetY(World world, double targetX, double targetZ, double fallbackY) {
        if (!configManager.shouldAdjustMovementYToHighestBlock()) {
            return fallbackY;
        }
        int chunkX = ((int) Math.floor(targetX)) >> 4;
        int chunkZ = ((int) Math.floor(targetZ)) >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return fallbackY;
        }
        return world.getHighestBlockYAt((int) Math.floor(targetX), (int) Math.floor(targetZ)) + 1.0D;
    }

    private double resolveSafeY(World world, double x, double z, double fallbackY) {
        return resolveTargetY(world, x, z, fallbackY);
    }

    private double adjustY(World world, double x, double currentY, double z) {
        if (!configManager.shouldAdjustMovementYToHighestBlock()) {
            return currentY;
        }

        int chunkX = ((int) Math.floor(x)) >> 4;
        int chunkZ = ((int) Math.floor(z)) >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return currentY;
        }

        double targetY = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1.0D;
        double difference = targetY - currentY;
        double maxAdjust = configManager.getMovementMaxYAdjustPerUpdate();
        if (Math.abs(difference) <= maxAdjust) {
            return targetY;
        }
        return currentY + Math.copySign(maxAdjust, difference);
    }

    private Integer calculateEtaSeconds(double currentX, double currentY, double currentZ, double targetX, double targetY, double targetZ, double speed) {
        if (speed <= 0.0D) {
            return null;
        }
        double distance = new Vector(targetX - currentX, targetY - currentY, targetZ - currentZ).length();
        return Math.max(0, (int) Math.ceil(distance / speed));
    }

    private boolean persistRuntime(CaravanRecord caravan) {
        runtimeCaravans.put(caravan.id(), caravan);
        dirtyCaravans.remove(caravan.id());
        CaravanMutationResult result = caravanService.updateCaravanRecord(caravan);
        if (!result.success()) {
            logger.warning("Failed to persist caravan movement state for " + caravan.id() + '.');
            dirtyCaravans.add(caravan.id());
            return false;
        }
        runtimeCaravans.put(result.caravan().id(), result.caravan());
        return true;
    }

    private void saveDirtyCaravans(boolean saveAll) {
        List<UUID> toSave = saveAll ? new ArrayList<>(runtimeCaravans.keySet()) : new ArrayList<>(dirtyCaravans);
        for (UUID caravanId : toSave) {
            CaravanRecord caravan = runtimeCaravans.get(caravanId);
            if (caravan != null) {
                persistRuntime(caravan);
            }
        }
    }

    private CaravanRecord normalizeLoadedCaravan(CaravanRecord caravan) {
        if (caravan.speedBlocksPerSecond() <= 0.0D) {
            return caravan.withMovement(
                caravan.status(),
                caravan.worldName(),
                caravan.virtualX(),
                caravan.virtualY(),
                caravan.virtualZ(),
                caravan.targetWorldName(),
                caravan.targetX(),
                caravan.targetY(),
                caravan.targetZ(),
                caravan.movementStartedAt(),
                caravan.movementUpdatedAt(),
                configManager.getDefaultMovementSpeedBlocksPerSecond(),
                caravan.etaSeconds(),
                false,
                caravan.updatedAt()
            );
        }
        if (caravan.physicalSpawned()) {
            return caravan.withPhysicalSpawned(false, caravan.updatedAt());
        }
        return caravan;
    }

    private Map<String, String> placeholders(CaravanRecord caravan) {
        return Map.ofEntries(
            Map.entry("id", caravan.id().toString().substring(0, 8)),
            Map.entry("name", caravan.name()),
            Map.entry("world", caravan.worldName() == null ? "unknown" : caravan.worldName()),
            Map.entry("x", caravan.virtualX() == null ? "?" : String.format(java.util.Locale.US, "%.1f", caravan.virtualX())),
            Map.entry("y", caravan.virtualY() == null ? "?" : String.format(java.util.Locale.US, "%.1f", caravan.virtualY())),
            Map.entry("z", caravan.virtualZ() == null ? "?" : String.format(java.util.Locale.US, "%.1f", caravan.virtualZ())),
            Map.entry("target_x", caravan.targetX() == null ? "?" : String.format(java.util.Locale.US, "%.1f", caravan.targetX())),
            Map.entry("target_y", caravan.targetY() == null ? "?" : String.format(java.util.Locale.US, "%.1f", caravan.targetY())),
            Map.entry("target_z", caravan.targetZ() == null ? "?" : String.format(java.util.Locale.US, "%.1f", caravan.targetZ())),
            Map.entry("speed", String.format(java.util.Locale.US, "%.2f", caravan.speedBlocksPerSecond())),
            Map.entry("eta", caravan.etaSeconds() == null ? "?" : String.valueOf(caravan.etaSeconds())),
            Map.entry("status", caravan.status().name())
        );
    }
}
