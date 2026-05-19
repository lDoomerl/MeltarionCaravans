package net.meltarion.caravans.service;

import java.lang.reflect.Method;
import org.bukkit.Location;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.meltarion.caravans.config.ConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

public final class TownyIntegrationService {

    private final PluginManager pluginManager;
    private final ConfigManager configManager;
    private final Logger logger;
    private boolean warnedAboutMissingTowny;

    public TownyIntegrationService(PluginManager pluginManager, ConfigManager configManager, Logger logger) {
        this.pluginManager = pluginManager;
        this.configManager = configManager;
        this.logger = logger;
    }

    public TownyRequirementResult checkOwnTownTerritory(Player player) {
        Plugin townyPlugin = pluginManager.getPlugin("Towny");
        if (townyPlugin == null || !townyPlugin.isEnabled()) {
            if (!warnedAboutMissingTowny) {
                logger.warning("Towny was not found. Physical caravan spawning is disabled until Towny is installed and enabled.");
                warnedAboutMissingTowny = true;
            }
            return TownyRequirementResult.TOWNY_NOT_FOUND;
        }

        try {
            Class<?> townyApiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Object townyApi = townyApiClass.getMethod("getInstance").invoke(null);

            Object townAtLocation = invokeMethod(townyApi, "getTown", new Class<?>[]{org.bukkit.Location.class}, player.getLocation());
            if (townAtLocation == null) {
                return TownyRequirementResult.NOT_IN_TOWN;
            }

            Object resident = invokeMethod(townyApi, "getResident", new Class<?>[]{org.bukkit.entity.Player.class}, player);
            if (resident == null) {
                return TownyRequirementResult.NOT_YOUR_TOWN;
            }

            Method hasTownMethod = resident.getClass().getMethod("hasTown");
            boolean hasTown = (boolean) hasTownMethod.invoke(resident);
            if (!hasTown) {
                return TownyRequirementResult.NOT_YOUR_TOWN;
            }

            Object residentTown = resident.getClass().getMethod("getTownOrNull").invoke(resident);
            if (residentTown == null || !residentTown.equals(townAtLocation)) {
                return TownyRequirementResult.NOT_YOUR_TOWN;
            }

            return TownyRequirementResult.OK;
        } catch (ReflectiveOperationException exception) {
            logger.log(Level.SEVERE, "Failed to query Towny API for physical caravan spawn validation.", exception);
            return TownyRequirementResult.ERROR;
        }
    }

    public boolean isShopPlot(Location location) {
        Plugin townyPlugin = pluginManager.getPlugin("Towny");
        if (townyPlugin == null || !townyPlugin.isEnabled()) {
            debug("Towny missing while checking Shop Plot at " + location + '.');
            return false;
        }

        try {
            Class<?> townyApiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Object townyApi = townyApiClass.getMethod("getInstance").invoke(null);
            Object townBlock = invokeMethod(townyApi, "getTownBlock", new Class<?>[]{org.bukkit.Location.class}, location);
            if (townBlock == null) {
                debug("No TownBlock at " + location + ';');
                return false;
            }

            Object type = townBlock.getClass().getMethod("getType").invoke(townBlock);
            if (type == null) {
                debug("TownBlock type was null at " + location + ';');
                return false;
            }

            String normalized = type.toString().toUpperCase(java.util.Locale.ROOT);
            boolean result = normalized.contains("SHOP") || normalized.contains("COMMERCIAL");
            debug("Shop Plot check at " + location + " -> " + normalized + " => " + result);
            return result;
        } catch (ReflectiveOperationException exception) {
            logger.log(Level.SEVERE, "Failed to query Towny API for Shop Plot validation.", exception);
            return false;
        }
    }

    private Object invokeMethod(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws ReflectiveOperationException {
        return target.getClass().getMethod(methodName, parameterTypes).invoke(target, args);
    }

    private void debug(String message) {
        if (configManager.isDebugEnabled()) {
            logger.info("[TownyDebug] " + message);
        }
    }
}
