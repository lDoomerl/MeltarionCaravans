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
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class CaravanRouteService {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final CaravanService caravanService;
    private final CaravanMovementService movementService;
    private final TownyIntegrationService townyIntegrationService;
    private final MessageService messageService;
    private final CaravanRouteStorage storage;
    private final Logger logger;
    private final Map<UUID, List<CaravanRouteStopRecord>> stopsByCaravan = new ConcurrentHashMap<>();

    private BukkitTask routeTask;

    public CaravanRouteService(
        Plugin plugin,
        ConfigManager configManager,
        CaravanService caravanService,
        CaravanMovementService movementService,
        TownyIntegrationService townyIntegrationService,
        MessageService messageService,
        CaravanRouteStorage storage,
        Logger logger
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.caravanService = caravanService;
        this.movementService = movementService;
        this.townyIntegrationService = townyIntegrationService;
        this.messageService = messageService;
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
            Player owner = Bukkit.getPlayer(finished.ownerId());
            if (owner != null && owner.isOnline()) {
                messageService.send(owner, "route-finished", basePlaceholders(finished));
            }
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

        Player owner = Bukkit.getPlayer(updated.ownerId());
        if (owner != null && owner.isOnline()) {
            Map<String, String> placeholders = routePlaceholders(updated, stop);
            messageService.send(owner, "route-arrived-stop", placeholders);
            messageService.send(owner, "route-stop-timer-started", placeholders);
        }
    }

    public boolean isRouteRunning(UUID caravanId) {
        CaravanRecord caravan = caravanService.getCaravan(caravanId);
        return caravan != null && caravan.routeRunning();
    }

    public void discardCaravanState(UUID caravanId) {
        stopsByCaravan.remove(caravanId);
    }

    private void tick() {
        Instant now = Instant.now();
        for (CaravanRecord caravan : caravanService.getAllCaravans()) {
            if (!caravan.routeRunning() || caravan.currentStopEndsAt() == null) {
                continue;
            }
            if (caravan.status() == CaravanStatus.ATTACKED) {
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
        Player owner = Bukkit.getPlayer(caravan.ownerId());
        if (owner != null && owner.isOnline()) {
            messageService.send(owner, "route-stop-timer-finished", routePlaceholders(caravan, currentStop));
        }

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
            if (movementResult.success() && owner != null && owner.isOnline()) {
                messageService.send(owner, "route-started", routePlaceholders(movementResult.caravan(), nextStop));
            }
            return;
        }

        CaravanRecord updated = caravan.withRouteState(currentIndex, true, null, null, true, Instant.now());
        CaravanMutationResult persisted = caravanService.updateCaravanRecord(updated);
        if (!persisted.success()) {
            return;
        }

        if (owner != null && owner.isOnline()) {
            messageService.send(owner, "route-returning-home", basePlaceholders(persisted.caravan()));
        }

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
            "name", caravan.name()
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
            "eta", caravan.etaSeconds() == null ? "0" : String.valueOf(caravan.etaSeconds()),
            "time_left", String.valueOf(timeLeft)
        );
    }
}
