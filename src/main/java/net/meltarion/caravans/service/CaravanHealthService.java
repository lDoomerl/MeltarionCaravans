package net.meltarion.caravans.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.model.CaravanStatus;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class CaravanHealthService {

    private static final long HEALTH_FLUSH_INTERVAL_TICKS = 100L;

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final CaravanService caravanService;
    private final CaravanEntityService caravanEntityService;
    private final Logger logger;
    private final ConcurrentMap<UUID, Boolean> dirtyCaravans = new ConcurrentHashMap<>();

    private BukkitTask flushTask;
    private BukkitTask visualSyncTask;

    public CaravanHealthService(
        Plugin plugin,
        ConfigManager configManager,
        CaravanService caravanService,
        CaravanEntityService caravanEntityService,
        Logger logger
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.caravanService = caravanService;
        this.caravanEntityService = caravanEntityService;
        this.logger = logger;
    }

    public void initialize() {
        shutdown();
        flushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushDirtyCaravans, HEALTH_FLUSH_INTERVAL_TICKS, HEALTH_FLUSH_INTERVAL_TICKS);
        visualSyncTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::syncVisualHealth,
            configManager.getCaravanVisualHealthSyncIntervalTicks(),
            configManager.getCaravanVisualHealthSyncIntervalTicks()
        );
    }

    public void shutdown() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        if (visualSyncTask != null) {
            visualSyncTask.cancel();
            visualSyncTask = null;
        }
        flushDirtyCaravans();
    }

    public CaravanRecord applyDamage(CaravanRecord caravan, double rawDamage, CaravanStatus status) {
        int scaledDamage = Math.max(1, (int) Math.ceil(Math.max(0.0D, rawDamage) * configManager.getCaravanDamageMultiplier()));
        int updatedHp = Math.max(0, caravan.hp() - scaledDamage);
        CaravanRecord updated = updateCached(caravan, updatedHp, status == null ? caravan.status() : status);
        if (updated.hp() <= 0) {
            flushCaravan(updated.id());
        }
        return updated;
    }

    public CaravanRecord markStatus(CaravanRecord caravan, CaravanStatus status) {
        return updateCached(caravan, caravan.hp(), status == null ? caravan.status() : status);
    }

    public void flushCaravan(UUID caravanId) {
        CaravanRecord caravan = caravanService.getCaravan(caravanId);
        if (caravan == null) {
            dirtyCaravans.remove(caravanId);
            return;
        }

        CaravanMutationResult result = caravanService.updateCaravanRecord(caravan);
        if (!result.success()) {
            logger.warning("Failed to flush caravan health for " + caravanId + '.');
            return;
        }

        dirtyCaravans.remove(caravanId);
        caravanEntityService.syncVisualHealth(result.caravan());
    }

    public void flushDirtyCaravans() {
        List<UUID> pending = new ArrayList<>(dirtyCaravans.keySet());
        for (UUID caravanId : pending) {
            flushCaravan(caravanId);
        }
    }

    public void syncVisualHealth() {
        for (CaravanRecord caravan : caravanService.getAllCaravans()) {
            caravanEntityService.syncVisualHealth(caravan);
        }
    }

    private CaravanRecord updateCached(CaravanRecord caravan, int hp, CaravanStatus status) {
        CaravanMutationResult result = caravanService.updateCachedCaravanRecord(
            caravan.withHealthAndStatus(
                Math.max(0, Math.min(hp, caravan.maxHp())),
                status,
                java.time.Instant.now()
            )
        );
        if (!result.success() || result.caravan() == null) {
            logger.warning("Failed to cache caravan health update for " + caravan.id() + '.');
            return caravan;
        }

        dirtyCaravans.put(result.caravan().id(), Boolean.TRUE);
        return result.caravan();
    }
}
