package net.meltarion.caravans.service;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.model.CaravanRecord;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;

public final class DynmapService {

    private final Plugin plugin;
    private final PluginManager pluginManager;
    private final ConfigManager configManager;
    private final CaravanService caravanService;
    private final Logger logger;
    private final Map<UUID, Object> markersByCaravan = new HashMap<>();

    private Object dynmapApi;
    private Object markerApi;
    private Object markerSet;
    private Object defaultMarkerIcon;
    private BukkitTask updateTask;

    public DynmapService(
        Plugin plugin,
        PluginManager pluginManager,
        ConfigManager configManager,
        CaravanService caravanService,
        Logger logger
    ) {
        this.plugin = plugin;
        this.pluginManager = pluginManager;
        this.configManager = configManager;
        this.caravanService = caravanService;
        this.logger = logger;
    }

    public void initialize() {
        if (!configManager.isDynmapEnabled()) {
            return;
        }

        org.bukkit.plugin.Plugin dynmapPlugin = pluginManager.getPlugin("dynmap");
        if (dynmapPlugin == null || !dynmapPlugin.isEnabled()) {
            logger.info("Dynmap integration disabled because Dynmap is not installed.");
            return;
        }

        try {
            Class<?> apiClass = Class.forName("org.dynmap.DynmapCommonAPI");
            if (!apiClass.isInstance(dynmapPlugin)) {
                logger.warning("Dynmap plugin is present but does not expose DynmapCommonAPI directly. Skipping integration.");
                return;
            }

            dynmapApi = dynmapPlugin;
            markerApi = invokeMethod(dynmapApi, "getMarkerAPI");
            if (markerApi == null) {
                logger.warning("Dynmap MarkerAPI is unavailable. Skipping marker integration.");
                return;
            }

            markerSet = invokeMethod(markerApi, "getMarkerSet", new Class<?>[]{String.class}, configManager.getDynmapMarkerSetId());
            if (markerSet == null) {
                markerSet = invokeMethod(
                    markerApi,
                    "createMarkerSet",
                    new Class<?>[]{String.class, String.class, java.util.Set.class, boolean.class},
                    configManager.getDynmapMarkerSetId(),
                    "Meltarion Caravans",
                    null,
                    false
                );
            }
            defaultMarkerIcon = invokeMethod(markerApi, "getMarkerIcon", new Class<?>[]{String.class}, "world");
            long period = configManager.getDynmapUpdateIntervalSeconds() * 20L;
            updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
            logger.info("Dynmap integration enabled.");
        } catch (ReflectiveOperationException exception) {
            logger.log(Level.WARNING, "Failed to enable Dynmap integration.", exception);
            dynmapApi = null;
            markerApi = null;
            markerSet = null;
            defaultMarkerIcon = null;
        }
    }

    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        for (Object marker : markersByCaravan.values()) {
            deleteMarker(marker);
        }
        markersByCaravan.clear();
    }

    private void tick() {
        if (markerSet == null) {
            return;
        }

        for (CaravanRecord caravan : caravanService.getAllCaravans()) {
            boolean active = caravan.hasVirtualPosition() && (caravan.routeRunning() || caravan.physicalSpawned() || caravan.status() != net.meltarion.caravans.model.CaravanStatus.IDLE);
            if (!active) {
                removeMarker(caravan.id());
                continue;
            }
            upsertMarker(caravan);
        }
    }

    private void upsertMarker(CaravanRecord caravan) {
        try {
            Object marker = markersByCaravan.get(caravan.id());
            String markerId = "caravan_" + caravan.id();
            String label = buildLabel(caravan);
            if (marker == null) {
                marker = invokeMethod(
                    markerSet,
                    "createMarker",
                    new Class<?>[]{String.class, String.class, boolean.class, String.class, double.class, double.class, double.class, Class.forName("org.dynmap.markers.MarkerIcon"), boolean.class},
                    markerId,
                    label,
                    false,
                    caravan.worldName(),
                    caravan.virtualX(),
                    caravan.virtualY(),
                    caravan.virtualZ(),
                    defaultMarkerIcon,
                    false
                );
                if (marker != null) {
                    markersByCaravan.put(caravan.id(), marker);
                }
                return;
            }

            invokeMethod(marker, "setLocation", new Class<?>[]{String.class, double.class, double.class, double.class}, caravan.worldName(), caravan.virtualX(), caravan.virtualY(), caravan.virtualZ());
            invokeMethod(marker, "setLabel", new Class<?>[]{String.class}, label);
        } catch (ReflectiveOperationException exception) {
            logger.log(Level.FINE, "Failed to update Dynmap marker for caravan " + caravan.id() + '.', exception);
        }
    }

    private void removeMarker(UUID caravanId) {
        Object marker = markersByCaravan.remove(caravanId);
        if (marker != null) {
            deleteMarker(marker);
        }
    }

    private void deleteMarker(Object marker) {
        try {
            invokeMethod(marker, "deleteMarker");
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private String buildLabel(CaravanRecord caravan) {
        StringBuilder label = new StringBuilder("Caravan ")
            .append(caravan.ownerName())
            .append(" (")
            .append(caravan.status().name())
            .append(')');
        if (configManager.shouldShowDynmapEta() && caravan.etaSeconds() != null) {
            label.append(" ETA: ").append(caravan.etaSeconds()).append('s');
        }
        return label.toString();
    }

    private Object invokeMethod(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private Object invokeMethod(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName, parameterTypes);
        return method.invoke(target, args);
    }
}
