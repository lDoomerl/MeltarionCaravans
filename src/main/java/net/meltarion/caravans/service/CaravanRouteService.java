package net.meltarion.caravans.service;

import java.time.Duration;
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
import net.meltarion.caravans.model.CaravanRouteStopRecord;
import net.meltarion.caravans.model.CaravanStatus;
import net.meltarion.caravans.storage.CaravanRouteStorage;
import net.meltarion.caravans.storage.StorageException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

public final class CaravanRouteService {

    private final org.bukkit.plugin.Plugin plugin;
    private final ConfigManager configManager;
    private final CaravanService caravanService;
    private final CaravanMovementService movementService;
    private final NotificationService notificationService;
    private final CaravanRouteStorage storage;
    private final Logger logger;
    private final Map<UUID, List<CaravanRouteStopRecord>> stopsByCaravan = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> lastAttackAt = new ConcurrentHashMap<>();

    private BukkitTask routeTask;

    public CaravanRouteService(
        org.bukkit.plugin.Plugin plugin,
        ConfigManager configManager,
        CaravanService caravanService,
        CaravanMovementService movementService,
        NotificationService notificationService,
        CaravanRouteStorage storage,
        Logger logger
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.caravanService = caravanService;
        this.movementService = movementService;
        this.notificationService = notificationService;
        this.storage = storage;
        this.logger = logger;
    }

    public void initialize() throws StorageException {
        stopsByCaravan.clear();
        for (CaravanRouteStopRecord stop : storage.loadAllRouteStops()) {
            stopsByCaravan.computeIfAbsent(stop.caravanId(), ignored -> new ArrayList<>()).add(stop);
        }
        stopsByCaravan.values().forEach(this::sortStops);

        if (configManager.isRouteEnabled()) {
            routeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        }
    }

    public void shutdown() {
        if (routeTask != null) {
            routeTask.cancel();
            routeTask = null;
        }
        lastAttackAt.clear();
    }

    public List<CaravanRouteStopRecord> getRouteStops(UUID caravanId) {
        return List.copyOf(stopsByCaravan.getOrDefault(caravanId, List.of()));
    }

    public CaravanRouteResult addRouteStop(CaravanRecord caravan, RoutePlotTarget target, int stopDurationSeconds) {
        if (!configManager.isRouteEnabled()) {
            return CaravanRouteResult.failure(CaravanRouteFailureReason.DISABLED);
        }
        if (target == null) {
            return CaravanRouteResult.failure(CaravanRouteFailureReason.NO_SHOP_PLOTS);
        }
        if (stopDurationSeconds < configManager.getRouteMinStopMinutes() * 60
            || stopDurationSeconds > configManager.getRouteMaxStopMinutes() * 60) {
            return CaravanRouteResult.failure(CaravanRouteFailureReason.INVALID_DURATION);
        }

        List<CaravanRouteStopRecord> currentStops = new ArrayList<>(stopsByCaravan.getOrDefault(caravan.id(), List.of()));
        if (currentStops.size() >= configManager.getRouteMaxStopsPerCaravan()) {
            return CaravanRouteResult.failure(CaravanRouteFailureReason.MAX_STOPS_REACHED);
        }

        Instant now = Instant.now();
        CaravanRouteStopRecord routeStop = new CaravanRouteStopRecord(
            UUID.randomUUID(),
            caravan.id(),
            currentStops.size(),
            target.townName(),
            target.worldName(),
            target.x(),
            target.y(),
            target.z(),
            stopDurationSeconds,
            now,
            now
        );

        try {
            storage.insertRouteStop(routeStop);
        } catch (StorageException exception) {
            logger.log(Level.SEVERE, "Failed to add route stop for caravan " + caravan.id() + '.', exception);
            return CaravanRouteResult.failure(CaravanRouteFailureReason.STORAGE_ERROR);
        }

        currentStops.add(routeStop);
        sortStops(currentStops);
        stopsByCaravan.put(caravan.id(), currentStops);
        return CaravanRouteResult.success(caravan, routeStop);
    }

    public CaravanRouteResult removeRouteStop(CaravanRecord caravan, UUID routeStopId) {
        CaravanRecord latest = caravanService.getCaravan(caravan.id());
        if (latest != null && latest.routeRunning()) {
            return CaravanRouteResult.failure(CaravanRouteFailureReason.INVALID_STATE);
        }

        List<CaravanRouteStopRecord> currentStops = new ArrayList<>(stopsByCaravan.getOrDefault(caravan.id(), List.of()));
        CaravanRouteStopRecord removed = currentStops.stream()
            .filter(stop -> stop.id().equals(routeStopId))
            .findFirst()
            .orElse(null);
        if (removed == null) {
            return CaravanRouteResult.failure(CaravanRouteFailureReason.INVALID_STATE);
        }

        try {
            storage.deleteRouteStop(routeStopId);
            currentStops.removeIf(stop -> stop.id().equals(routeStopId));
            persistStopOrder(currentStops);
        } catch (StorageException exception) {
            logger.log(Level.SEVERE, "Failed to remove route stop " + routeStopId + " for caravan " + caravan.id() + '.', exception);
            return CaravanRouteResult.failure(CaravanRouteFailureReason.STORAGE_ERROR);
        }

        if (currentStops.isEmpty()) {
            stopsByCaravan.remove(caravan.id());
        } else {
            stopsByCaravan.put(caravan.id(), currentStops);
        }
        return CaravanRouteResult.success(latest == null ? caravan : latest, removed);
    }

    public CaravanRouteResult clearRoute(CaravanRecord caravan) {
        CaravanRecord latest = caravanService.getCaravan(caravan.id());
        if (latest == null) {
            latest = caravan;
        }

        if (latest.routeRunning()) {
            CaravanRouteResult stopResult = stopRoute(latest);
            if (!stopResult.success()) {
                return stopResult;
            }
            latest = stopResult.caravan();
        }

        try {
            storage.deleteRouteStopsByCaravan(caravan.id());
        } catch (StorageException exception) {
            logger.log(Level.SEVERE, "Failed to clear route for caravan " + caravan.id() + '.', exception);
            return CaravanRouteResult.failure(CaravanRouteFailureReason.STORAGE_ERROR);
        }

        stopsByCaravan.remove(caravan.id());
        CaravanRecord cleared = clearRouteRuntime(latest, false);
        return CaravanRouteResult.success(cleared);
    }

    public CaravanRouteResult toggleLoopMode(CaravanRecord caravan) {
        if (!configManager.isRouteLoopAllowed()) {
            return CaravanRouteResult.failure(CaravanRouteFailureReason.INVALID_STATE);
        }

        CaravanRecord latest = caravanService.getCaravan(caravan.id());
        if (latest == null) {
            return CaravanRouteResult.failure(CaravanRouteFailureReason.INVALID_STATE);
        }

        CaravanRecord updated = latest.withRouteLoopEnabled(!latest.routeLoopEnabled(), Instant.now());
        CaravanMutationResult persisted = caravanService.updateCaravanRecord(updated);
        if (!persisted.success()) {
            return CaravanRouteResult.failure(CaravanRouteFailureReason.STORAGE_ERROR);
        }
        return CaravanRouteResult.success(persisted.caravan());
    }

    public CaravanRouteResult startRoute(CaravanRecord caravan) {
        if (!configManager.isRouteEnabled()) {
            return CaravanRouteResult.failure(CaravanRouteFailureReason.DISABLED);
        }

        CaravanRecord latest = caravanService.getCaravan(caravan.id());
        if (latest == null) {
            return CaravanRouteResult.failure(CaravanRouteFailureReason.INVALID_STATE);
        }
        if (latest.routeRunning()) {
            return CaravanRouteResult.failure(CaravanRouteFailureReason.ALREADY_RUNNING);
        }

        List<CaravanRouteStopRecord> stops = getRouteStops(caravan.id());
        if (stops.isEmpty()) {
            return CaravanRouteResult.failure(CaravanRouteFailureReason.NO_STOPS);
        }

        CaravanRecord prepared = latest.withRouteState(0, true, null, null, false, Instant.now());
        CaravanMutationResult persisted = caravanService.updateCaravanRecord(prepared);
        if (!persisted.success()) {
            return CaravanRouteResult.failure(CaravanRouteFailureReason.STORAGE_ERROR);
        }

        CaravanRouteStopRecord firstStop = stops.getFirst();
        World world = Bukkit.getWorld(firstStop.worldName());
        if (world == null) {
            clearRouteRuntime(persisted.caravan(), false);
            return CaravanRouteResult.failure(CaravanRouteFailureReason.INVALID_STATE);
        }

        CaravanMovementResult movementResult = movementService.startMovement(persisted.caravan(), world, firstStop.x(), firstStop.z(), CaravanStatus.TRAVELING);
        if (!movementResult.success()) {
            clearRouteRuntime(persisted.caravan(), false);
            return CaravanRouteResult.failure(mapMovementFailure(movementResult.failureReason()));
        }

        return CaravanRouteResult.success(movementResult.caravan(), firstStop);
    }

    public CaravanRouteResult stopRoute(CaravanRecord caravan) {
        CaravanRecord latest = caravanService.getCaravan(caravan.id());
        if (latest == null) {
            return CaravanRouteResult.failure(CaravanRouteFailureReason.INVALID_STATE);
        }
        if (!latest.routeRunning()) {
            return CaravanRouteResult.failure(CaravanRouteFailureReason.NOT_RUNNING);
        }

        CaravanRecord working = latest;
        if (latest.status() == CaravanStatus.TRAVELING || latest.status() == CaravanStatus.RETURNING) {
            CaravanMovementResult stopMovementResult = movementService.stopMovement(latest);
            if (!stopMovementResult.success()) {
                return CaravanRouteResult.failure(mapMovementFailure(stopMovementResult.failureReason()));
            }
            working = stopMovementResult.caravan();
        }

        CaravanRecord cleared = clearRouteRuntime(working, false);
        return CaravanRouteResult.success(cleared);
    }

    public void handleMovementArrival(CaravanRecord caravan) {
        if (caravan == null || !caravan.routeRunning()) {
            return;
        }

        if (caravan.returningHomeAfterRoute()) {
            CaravanRecord finished = clearRouteRuntime(caravan, true);
            notificationService.sendPlayerMessage(finished.ownerId(), "route-arrived-home", basePlaceholders(finished));
            notificationService.sendPlayerMessage(finished.ownerId(), "route-finished", basePlaceholders(finished));
            return;
        }

        List<CaravanRouteStopRecord> stops = getRouteStops(caravan.id());
        if (stops.isEmpty()) {
            clearRouteRuntime(caravan, false);
            return;
        }

        int index = caravan.currentRouteStopIndex() == null ? 0 : caravan.currentRouteStopIndex();
        if (index < 0 || index >= stops.size()) {
            clearRouteRuntime(caravan, false);
            return;
        }

        CaravanRouteStopRecord stop = stops.get(index);
        Instant now = Instant.now();
        Instant endsAt = now.plusSeconds(stop.stopDurationSeconds());
        CaravanRecord updated = caravan.withRouteState(index, true, now, endsAt, false, now);
        CaravanMutationResult persisted = caravanService.updateCaravanRecord(updated);
        if (!persisted.success()) {
            return;
        }

        playStopEffects(updated);
        Map<String, String> placeholders = routePlaceholders(updated, stop);
        notificationService.sendPlayerMessage(updated.ownerId(), "route-arrived-stop", placeholders);
        notificationService.sendPlayerMessage(updated.ownerId(), "route-stop-timer-started", placeholders);
    }

    public void handleAttacked(CaravanRecord caravan) {
        lastAttackAt.put(caravan.id(), Instant.now());
        if (caravan.routeRunning() && configManager.shouldAutoResumeAfterAttack()) {
            notificationService.sendPlayerMessage(caravan.ownerId(), "route-resume-scheduled", basePlaceholders(caravan));
        }
    }

    public boolean isRouteRunning(UUID caravanId) {
        CaravanRecord caravan = caravanService.getCaravan(caravanId);
        return caravan != null && caravan.routeRunning();
    }

    public void discardCaravanState(UUID caravanId) {
        stopsByCaravan.remove(caravanId);
        lastAttackAt.remove(caravanId);
    }

    private void tick() {
        Instant now = Instant.now();
        for (CaravanRecord caravan : caravanService.getAllCaravans()) {
            if (caravan.routeRunning() && caravan.status() == CaravanStatus.ATTACKED) {
                attemptAutoResume(caravan, now);
                continue;
            }
            if (!caravan.routeRunning() || caravan.currentStopEndsAt() == null) {
                continue;
            }
            if (now.isBefore(caravan.currentStopEndsAt())) {
                continue;
            }

            handleStopTimerFinished(caravan);
        }
    }

    private void handleStopTimerFinished(CaravanRecord caravan) {
        List<CaravanRouteStopRecord> stops = getRouteStops(caravan.id());
        if (stops.isEmpty()) {
            clearRouteRuntime(caravan, false);
            return;
        }

        int currentIndex = caravan.currentRouteStopIndex() == null ? 0 : caravan.currentRouteStopIndex();
        CaravanRouteStopRecord currentStop = currentIndex >= 0 && currentIndex < stops.size() ? stops.get(currentIndex) : null;
        notificationService.sendPlayerMessage(caravan.ownerId(), "route-stop-timer-finished", routePlaceholders(caravan, currentStop));

        if (currentIndex + 1 < stops.size()) {
            CaravanRouteStopRecord nextStop = stops.get(currentIndex + 1);
            World world = Bukkit.getWorld(nextStop.worldName());
            if (world == null) {
                clearRouteRuntime(caravan, false);
                return;
            }

            CaravanRecord updated = caravan.withRouteState(currentIndex + 1, true, null, null, false, Instant.now());
            CaravanMutationResult persisted = caravanService.updateCaravanRecord(updated);
            if (!persisted.success()) {
                return;
            }

            CaravanMovementResult movementResult = movementService.startMovement(persisted.caravan(), world, nextStop.x(), nextStop.z(), CaravanStatus.TRAVELING);
            if (movementResult.success()) {
                notificationService.sendPlayerMessage(caravan.ownerId(), "route-next-stop", routePlaceholders(movementResult.caravan(), nextStop));
            }
            return;
        }

        if (caravan.routeLoopEnabled() && configManager.isRouteLoopAllowed()) {
            CaravanRouteStopRecord firstStop = stops.getFirst();
            World world = Bukkit.getWorld(firstStop.worldName());
            if (world == null) {
                clearRouteRuntime(caravan, false);
                return;
            }

            CaravanRecord loopPrepared = caravan.withRouteState(0, true, null, null, false, Instant.now());
            CaravanMutationResult persisted = caravanService.updateCaravanRecord(loopPrepared);
            if (!persisted.success()) {
                return;
            }

            CaravanMovementResult loopResult = movementService.startMovement(persisted.caravan(), world, firstStop.x(), firstStop.z(), CaravanStatus.TRAVELING);
            if (loopResult.success()) {
                notificationService.sendPlayerMessage(caravan.ownerId(), "route-next-stop", routePlaceholders(loopResult.caravan(), firstStop));
            }
            return;
        }

        CaravanRecord updated = caravan.withRouteState(currentIndex, true, null, null, true, Instant.now());
        CaravanMutationResult persisted = caravanService.updateCaravanRecord(updated);
        if (!persisted.success()) {
            return;
        }

        notificationService.sendPlayerMessage(caravan.ownerId(), "route-returning-home", basePlaceholders(persisted.caravan()));
        CaravanMovementResult result = movementService.returnHome(persisted.caravan());
        if (!result.success()) {
            clearRouteRuntime(persisted.caravan(), false);
        }
    }

    private CaravanRecord clearRouteRuntime(CaravanRecord caravan, boolean keepStoppedStatus) {
        CaravanRecord updated = caravan.withRouteState(null, false, null, null, false, Instant.now());
        if (!keepStoppedStatus && updated.status() == CaravanStatus.RETURNING) {
            updated = updated.withMovement(
                CaravanStatus.STOPPED,
                updated.worldName(),
                updated.virtualX(),
                updated.virtualY(),
                updated.virtualZ(),
                null,
                null,
                null,
                null,
                updated.movementStartedAt(),
                Instant.now(),
                updated.speedBlocksPerSecond(),
                null,
                updated.physicalSpawned(),
                Instant.now()
            );
        }
        CaravanMutationResult result = caravanService.updateCaravanRecord(updated);
        return result.success() ? result.caravan() : caravan;
    }

    private void attemptAutoResume(CaravanRecord caravan, Instant now) {
        if (!configManager.shouldAutoResumeAfterAttack() || caravan.hp() <= 0) {
            return;
        }

        Instant attackedAt = lastAttackAt.get(caravan.id());
        if (attackedAt == null || Duration.between(attackedAt, now).getSeconds() < configManager.getAutoResumeDelaySeconds()) {
            return;
        }

        lastAttackAt.remove(caravan.id());
        CaravanRecord latest = caravanService.getCaravan(caravan.id());
        if (latest == null || latest.hp() <= 0) {
            return;
        }

        if (latest.returningHomeAfterRoute()) {
            CaravanMovementResult result = movementService.returnHome(latest);
            if (result.success()) {
                notificationService.sendPlayerMessage(latest.ownerId(), "route-resumed", basePlaceholders(result.caravan()));
            }
            return;
        }

        if (latest.hasTargetPosition() && latest.targetWorldName() != null && latest.targetX() != null && latest.targetZ() != null) {
            World world = Bukkit.getWorld(latest.targetWorldName());
            if (world == null) {
                return;
            }
            CaravanMovementResult result = movementService.startMovement(latest, world, latest.targetX(), latest.targetZ(), CaravanStatus.TRAVELING);
            if (result.success()) {
                notificationService.sendPlayerMessage(latest.ownerId(), "route-resumed", basePlaceholders(result.caravan()));
            }
            return;
        }

        if (latest.currentStopEndsAt() != null) {
            CaravanRecord resumed = latest.withMovement(
                CaravanStatus.STOPPED,
                latest.worldName(),
                latest.virtualX(),
                latest.virtualY(),
                latest.virtualZ(),
                null,
                null,
                null,
                null,
                latest.movementStartedAt(),
                Instant.now(),
                latest.speedBlocksPerSecond(),
                latest.etaSeconds(),
                latest.physicalSpawned(),
                Instant.now()
            );
            CaravanMutationResult persisted = caravanService.updateCaravanRecord(resumed);
            if (persisted.success()) {
                notificationService.sendPlayerMessage(latest.ownerId(), "route-resumed", basePlaceholders(persisted.caravan()));
            }
        }
    }

    private void persistStopOrder(List<CaravanRouteStopRecord> stops) throws StorageException {
        sortStops(stops);
        Instant now = Instant.now();
        for (int index = 0; index < stops.size(); index++) {
            CaravanRouteStopRecord reordered = stops.get(index).withStopOrder(index, now);
            storage.updateRouteStop(reordered);
            stops.set(index, reordered);
        }
    }

    private void sortStops(List<CaravanRouteStopRecord> stops) {
        stops.sort(Comparator.comparingInt(CaravanRouteStopRecord::stopOrder).thenComparing(CaravanRouteStopRecord::createdAt));
    }

    private CaravanRouteFailureReason mapMovementFailure(CaravanMovementResult.FailureReason failureReason) {
        return switch (failureReason) {
            case DISABLED, INVALID_TARGET, NO_POSITION, HOME_MISSING -> CaravanRouteFailureReason.INVALID_STATE;
            case STORAGE_ERROR -> CaravanRouteFailureReason.STORAGE_ERROR;
        };
    }

    private Map<String, String> basePlaceholders(CaravanRecord caravan) {
        return Map.of(
            "id", caravan.id().toString().substring(0, 8),
            "name", caravan.name(),
            "status", caravan.status().name(),
            "eta", caravan.etaSeconds() == null ? "0" : String.valueOf(caravan.etaSeconds())
        );
    }

    private Map<String, String> routePlaceholders(CaravanRecord caravan, CaravanRouteStopRecord stop) {
        String duration = stop == null ? "0" : String.valueOf(Duration.ofSeconds(stop.stopDurationSeconds()).toMinutes());
        String order = stop == null ? "?" : String.valueOf(stop.stopOrder() + 1);
        String town = stop == null ? "Unknown" : stop.townName();
        long timeLeft = caravan.currentStopEndsAt() == null ? 0L : Math.max(0L, Duration.between(Instant.now(), caravan.currentStopEndsAt()).toSeconds());
        return Map.of(
            "id", caravan.id().toString().substring(0, 8),
            "name", caravan.name(),
            "town", town,
            "duration", duration,
            "order", order,
            "stop", order,
            "eta", caravan.etaSeconds() == null ? "0" : String.valueOf(caravan.etaSeconds()),
            "time_left", String.valueOf(timeLeft),
            "status", caravan.status().name()
        );
    }

    private void playStopEffects(CaravanRecord caravan) {
        if (!configManager.areRouteStopEffectsEnabled() || caravan.worldName() == null || caravan.virtualX() == null || caravan.virtualY() == null || caravan.virtualZ() == null) {
            return;
        }

        World world = Bukkit.getWorld(caravan.worldName());
        if (world == null) {
            return;
        }

        Location location = new Location(world, caravan.virtualX(), caravan.virtualY(), caravan.virtualZ());
        world.spawnParticle(configManager.getRouteStopEffectParticle(), location, 6, 0.4D, 0.6D, 0.4D, 0.01D);
        world.playSound(location, configManager.getRouteStopEffectSound(), 0.7F, 1.0F);
    }
}
