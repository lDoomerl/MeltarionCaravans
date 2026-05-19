package net.meltarion.caravans.service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.meltarion.caravans.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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

    public boolean isTownyAvailable() {
        Plugin townyPlugin = pluginManager.getPlugin("Towny");
        return townyPlugin != null && townyPlugin.isEnabled();
    }

    public TownyRequirementResult checkOwnTownTerritory(Player player) {
        Object townyApi = getTownyApiOrWarn();
        if (townyApi == null) {
            return TownyRequirementResult.TOWNY_NOT_FOUND;
        }

        try {
            Object townAtLocation = invokeMethod(townyApi, "getTown", new Class<?>[]{Location.class}, player.getLocation());
            if (townAtLocation == null) {
                return TownyRequirementResult.NOT_IN_TOWN;
            }

            Object resident = getResident(townyApi, player);
            if (resident == null) {
                return TownyRequirementResult.NOT_YOUR_TOWN;
            }

            Object residentTown = invokeMethod(resident, "getTownOrNull");
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
        if (location == null) {
            return false;
        }

        Object townyApi = getTownyApiSilently();
        if (townyApi == null) {
            debug("Towny missing while checking Shop Plot at " + location + '.');
            return false;
        }

        try {
            Object townBlock = invokeMethod(townyApi, "getTownBlock", new Class<?>[]{Location.class}, location);
            boolean result = isShopTownBlock(townBlock);
            debug("Shop Plot check at " + location + " => " + result);
            return result;
        } catch (ReflectiveOperationException exception) {
            logger.log(Level.SEVERE, "Failed to query Towny API for Shop Plot validation.", exception);
            return false;
        }
    }

    public List<RouteTownOption> listAvailableRouteTowns(Player owner) {
        Object townyApi = getTownyApiSilently();
        if (townyApi == null) {
            return List.of();
        }

        try {
            Object resident = getResident(townyApi, owner);
            Object ownerTown = resident == null ? null : invokeMethod(resident, "getTownOrNull");
            List<?> towns = (List<?>) invokeMethod(townyApi, "getTowns");
            List<RouteTownOption> available = new ArrayList<>();

            for (Object town : towns) {
                if (town == null || !townExists(town)) {
                    continue;
                }

                String townName = (String) invokeMethod(town, "getName");
                List<Object> shopPlots = getShopPlots(town);
                if (shopPlots.isEmpty()) {
                    continue;
                }

                TownRelation relation = resolveRelation(ownerTown, town, townyApi);
                if (!isRelationAllowed(relation)) {
                    debug("Skipping route town " + townName + " due to relation " + relation);
                    continue;
                }

                available.add(new RouteTownOption(townName, relation, shopPlots.size()));
            }

            available.sort((left, right) -> {
                int relationCompare = Integer.compare(left.relation().ordinal(), right.relation().ordinal());
                if (relationCompare != 0) {
                    return relationCompare;
                }
                return left.townName().compareToIgnoreCase(right.townName());
            });
            return List.copyOf(available);
        } catch (ReflectiveOperationException exception) {
            logger.log(Level.SEVERE, "Failed to list Towny route towns.", exception);
            return List.of();
        }
    }

    public RoutePlotTarget getRandomShopPlotLocation(String townName) {
        if (townName == null || townName.isBlank()) {
            return null;
        }

        Object townyApi = getTownyApiSilently();
        if (townyApi == null) {
            return null;
        }

        try {
            Object town = invokeMethod(townyApi, "getTown", new Class<?>[]{String.class}, townName);
            if (town == null || !townExists(town)) {
                return null;
            }

            List<Object> shopPlots = getShopPlots(town);
            if (shopPlots.isEmpty()) {
                return null;
            }

            Object selected = shopPlots.get(configManager.shouldRandomlySelectRouteShopPlot()
                ? ThreadLocalRandom.current().nextInt(shopPlots.size())
                : 0);
            return toRoutePlotTarget(town, selected);
        } catch (ReflectiveOperationException exception) {
            logger.log(Level.SEVERE, "Failed to resolve a Towny shop plot route target for town " + townName + '.', exception);
            return null;
        }
    }

    private RoutePlotTarget toRoutePlotTarget(Object town, Object townBlock) throws ReflectiveOperationException {
        Object worldCoord = invokeMethod(townBlock, "getWorldCoord");
        int plotX = (int) invokeMethod(worldCoord, "getX");
        int plotZ = (int) invokeMethod(worldCoord, "getZ");
        String worldName = resolveWorldName(town, worldCoord);
        World world = Bukkit.getWorld(worldName);
        int townBlockSize = getTownBlockSize();
        double x = plotX * townBlockSize + townBlockSize / 2.0D;
        double z = plotZ * townBlockSize + townBlockSize / 2.0D;

        double fallbackY = 64.0D;
        Object spawnPosition = invokeOptionalMethod(town, "getSpawnOrNull");
        if (spawnPosition instanceof Location spawnLocation) {
            fallbackY = spawnLocation.getY();
        } else if (world != null) {
            fallbackY = world.getSpawnLocation().getY();
        }

        double y = fallbackY;
        if (world != null) {
            int chunkX = ((int) Math.floor(x)) >> 4;
            int chunkZ = ((int) Math.floor(z)) >> 4;
            if (world.isChunkLoaded(chunkX, chunkZ)) {
                y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1.0D;
            }
        }

        return new RoutePlotTarget((String) invokeMethod(town, "getName"), worldName, x, y, z);
    }

    private String resolveWorldName(Object town, Object worldCoord) throws ReflectiveOperationException {
        Object townWorld = invokeOptionalMethod(town, "getWorld");
        if (townWorld instanceof World world) {
            return world.getName();
        }

        Object bukkitWorld = invokeOptionalMethod(worldCoord, "getBukkitWorld");
        if (bukkitWorld instanceof World world) {
            return world.getName();
        }

        Object worldName = invokeOptionalMethod(worldCoord, "getWorldName");
        if (worldName instanceof String name && !name.isBlank()) {
            return name;
        }

        throw new NoSuchMethodException("Could not resolve Towny world name for route target.");
    }

    private TownRelation resolveRelation(Object ownerTown, Object candidateTown, Object townyApi) throws ReflectiveOperationException {
        if (ownerTown == null || candidateTown == null) {
            return TownRelation.NEUTRAL;
        }
        if (ownerTown.equals(candidateTown)) {
            return TownRelation.OWN;
        }

        if (hasTownRelation(ownerTown, candidateTown, "hasEnemy")) {
            return TownRelation.ENEMY;
        }
        if (hasTownRelation(ownerTown, candidateTown, "hasAlly")) {
            return TownRelation.ALLIED;
        }

        Object ownerNation = invokeMethod(townyApi, "getTownNationOrNull", new Class<?>[]{ownerTown.getClass()}, ownerTown);
        Object candidateNation = invokeMethod(townyApi, "getTownNationOrNull", new Class<?>[]{candidateTown.getClass()}, candidateTown);
        if (ownerNation != null && candidateNation != null) {
            if (hasNationRelation(ownerNation, candidateNation, "hasEnemy")) {
                return TownRelation.ENEMY;
            }
            if (hasNationRelation(ownerNation, candidateNation, "hasAlly")
                || hasNationRelation(ownerNation, candidateNation, "isAlliedWith")
                || hasNationRelation(ownerNation, candidateNation, "hasMutualAlly")) {
                return TownRelation.ALLIED;
            }
        }

        return TownRelation.NEUTRAL;
    }

    private boolean isRelationAllowed(TownRelation relation) {
        return switch (relation) {
            case OWN -> configManager.isRouteAllowOwnTown();
            case ALLIED -> configManager.isRouteAllowAlliedTowns();
            case NEUTRAL -> configManager.isRouteAllowNeutralTowns();
            case ENEMY -> configManager.isRouteAllowEnemyTowns();
        };
    }

    private boolean hasTownRelation(Object leftTown, Object rightTown, String methodName) throws ReflectiveOperationException {
        try {
            Object direct = invokeMethod(leftTown, methodName, new Class<?>[]{rightTown.getClass()}, rightTown);
            if (direct instanceof Boolean directResult && directResult) {
                return true;
            }
        } catch (NoSuchMethodException ignored) {
        }

        try {
            Object reverse = invokeMethod(rightTown, methodName, new Class<?>[]{leftTown.getClass()}, leftTown);
            return reverse instanceof Boolean reverseResult && reverseResult;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private boolean hasNationRelation(Object leftNation, Object rightNation, String methodName) throws ReflectiveOperationException {
        try {
            Object direct = invokeMethod(leftNation, methodName, new Class<?>[]{rightNation.getClass()}, rightNation);
            return direct instanceof Boolean directResult && directResult;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private List<Object> getShopPlots(Object town) throws ReflectiveOperationException {
        Object blocksObject = invokeMethod(town, "getTownBlocks");
        if (!(blocksObject instanceof Collection<?> collection)) {
            return List.of();
        }

        List<Object> shopPlots = new ArrayList<>();
        for (Object townBlock : collection) {
            if (isShopTownBlock(townBlock)) {
                shopPlots.add(townBlock);
            }
        }
        return List.copyOf(shopPlots);
    }

    private boolean isShopTownBlock(Object townBlock) throws ReflectiveOperationException {
        if (townBlock == null) {
            return false;
        }

        Object type = invokeMethod(townBlock, "getType");
        if (type == null) {
            return false;
        }
        String normalized = type.toString().toUpperCase(Locale.ROOT);
        return normalized.contains("SHOP") || normalized.contains("COMMERCIAL");
    }

    private int getTownBlockSize() {
        try {
            Class<?> townySettingsClass = Class.forName("com.palmergames.bukkit.towny.TownySettings");
            Method method = townySettingsClass.getMethod("getTownBlockSize");
            Object result = method.invoke(null);
            if (result instanceof Integer size && size > 0) {
                return size;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return 16;
    }

    private Object getResident(Object townyApi, Player player) throws ReflectiveOperationException {
        try {
            return invokeMethod(townyApi, "getResident", new Class<?>[]{Player.class}, player);
        } catch (NoSuchMethodException exception) {
            return invokeMethod(townyApi, "getResidentOrThrow", new Class<?>[]{Player.class}, player);
        }
    }

    private boolean townExists(Object town) {
        try {
            Object exists = invokeOptionalMethod(town, "exists");
            return !(exists instanceof Boolean existsValue) || existsValue;
        } catch (ReflectiveOperationException exception) {
            return true;
        }
    }

    private Object getTownyApiOrWarn() {
        Object townyApi = getTownyApiSilently();
        if (townyApi == null && !warnedAboutMissingTowny) {
            logger.warning("Towny was not found. Caravan Towny integration is unavailable until Towny is installed and enabled.");
            warnedAboutMissingTowny = true;
        }
        return townyApi;
    }

    private Object getTownyApiSilently() {
        if (!isTownyAvailable()) {
            return null;
        }

        try {
            Class<?> townyApiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            return townyApiClass.getMethod("getInstance").invoke(null);
        } catch (ReflectiveOperationException exception) {
            logger.log(Level.SEVERE, "Failed to initialize Towny API reflection.", exception);
            return null;
        }
    }

    private Object invokeMethod(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws ReflectiveOperationException {
        return target.getClass().getMethod(methodName, parameterTypes).invoke(target, args);
    }

    private Object invokeMethod(Object target, String methodName) throws ReflectiveOperationException {
        return target.getClass().getMethod(methodName).invoke(target);
    }

    private Object invokeOptionalMethod(Object target, String methodName) throws ReflectiveOperationException {
        try {
            return invokeMethod(target, methodName);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private void debug(String message) {
        if (configManager.isDebugEnabled()) {
            logger.info("[TownyDebug] " + message);
        }
    }
}
