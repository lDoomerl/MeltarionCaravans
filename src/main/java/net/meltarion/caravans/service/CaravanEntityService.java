package net.meltarion.caravans.service;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.model.CaravanEntityRole;
import net.meltarion.caravans.model.CaravanRecord;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TraderLlama;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

public final class CaravanEntityService {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static final double VISUAL_ENTITY_MAX_HEALTH = 20.0D;

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final Logger logger;
    private final NamespacedKey caravanIdKey;
    private final NamespacedKey caravanEntityRoleKey;
    private final Map<UUID, SpawnedCaravanEntities> spawnedCaravans = new ConcurrentHashMap<>();

    public CaravanEntityService(Plugin plugin, ConfigManager configManager, Logger logger) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.logger = logger;
        this.caravanIdKey = new NamespacedKey(plugin, "caravan_id");
        this.caravanEntityRoleKey = new NamespacedKey(plugin, "caravan_entity_role");
    }

    public synchronized PhysicalSpawnResult spawnCaravan(CaravanRecord caravan, Location baseLocation) {
        cleanupAllTrackedEntities();
        cleanupInvalidState(caravan.id());

        if (!configManager.isPhysicalCaravanEnabled()) {
            return PhysicalSpawnResult.failure(PhysicalSpawnFailureReason.DISABLED);
        }

        if (configManager.shouldPreventDuplicatePhysicalSpawn() && isSpawned(caravan.id())) {
            return PhysicalSpawnResult.failure(PhysicalSpawnFailureReason.ALREADY_SPAWNED);
        }

        if (!configManager.shouldPreventDuplicatePhysicalSpawn() && isSpawned(caravan.id())) {
            despawnCaravan(caravan.id());
        }

        World world = baseLocation.getWorld();
        if (world == null) {
            return PhysicalSpawnResult.failure(PhysicalSpawnFailureReason.ERROR);
        }

        try {
            Location traderLocation = baseLocation.clone().add(0.5D, 0.0D, 0.5D);
            Location llamaOneLocation = baseLocation.clone().add(1.5D, 0.0D, 0.5D);
            Location llamaTwoLocation = baseLocation.clone().add(-0.5D, 0.0D, 0.5D);

            WanderingTrader trader = (WanderingTrader) world.spawnEntity(traderLocation, EntityType.WANDERING_TRADER);
            TraderLlama llamaOne = (TraderLlama) world.spawnEntity(llamaOneLocation, EntityType.TRADER_LLAMA);
            TraderLlama llamaTwo = (TraderLlama) world.spawnEntity(llamaTwoLocation, EntityType.TRADER_LLAMA);

            configureTrader(trader, caravan);
            configureLlama(llamaOne, caravan, CaravanEntityRole.LLAMA_1, configManager.getPhysicalLlamaOneNameFormat());
            configureLlama(llamaTwo, caravan, CaravanEntityRole.LLAMA_2, configManager.getPhysicalLlamaTwoNameFormat());

            spawnedCaravans.put(caravan.id(), new SpawnedCaravanEntities(
                trader.getUniqueId(),
                llamaOne.getUniqueId(),
                llamaTwo.getUniqueId()
            ));

            return PhysicalSpawnResult.successful();
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to spawn physical caravan entities for " + caravan.id() + '.', exception);
            despawnCaravan(caravan.id());
            return PhysicalSpawnResult.failure(PhysicalSpawnFailureReason.ERROR);
        }
    }

    public synchronized boolean despawnCaravan(UUID caravanId) {
        SpawnedCaravanEntities entities = spawnedCaravans.remove(caravanId);
        if (entities == null) {
            return false;
        }

        removeEntity(entities.traderId());
        removeEntity(entities.llamaOneId());
        removeEntity(entities.llamaTwoId());
        return true;
    }

    public synchronized void handlePluginDisable() {
        if (!configManager.shouldDespawnPhysicalCaravansOnDisable()) {
            return;
        }

        for (UUID caravanId : spawnedCaravans.keySet()) {
            despawnCaravan(caravanId);
        }
        spawnedCaravans.clear();
    }

    public synchronized void initialize() {
        cleanupAllTrackedEntities();
        if (configManager.shouldCleanupOrphanedPhysicalEntitiesOnEnable()) {
            cleanupOrphanedEntitiesOnEnable();
        }
    }

    public synchronized boolean isSpawned(UUID caravanId) {
        cleanupInvalidState(caravanId);
        return spawnedCaravans.containsKey(caravanId);
    }

    public synchronized ProjectionDebugInfo getDebugInfo(UUID caravanId) {
        cleanupInvalidState(caravanId);
        SpawnedCaravanEntities entities = spawnedCaravans.get(caravanId);
        if (entities == null) {
            return new ProjectionDebugInfo(false, null, false, null, false, null, false);
        }

        return new ProjectionDebugInfo(
            true,
            entities.traderId(),
            isAlive(entities.traderId()),
            entities.llamaOneId(),
            isAlive(entities.llamaOneId()),
            entities.llamaTwoId(),
            isAlive(entities.llamaTwoId())
        );
    }

    public synchronized void syncCaravanProjection(UUID caravanId, Location baseLocation, Vector direction, boolean stationary) {
        SpawnedCaravanEntities entities = spawnedCaravans.get(caravanId);
        if (entities == null || baseLocation.getWorld() == null) {
            return;
        }

        syncEntity(entities.traderId(), baseLocation.clone().add(0.5D, 0.0D, 0.5D), direction, stationary);
        syncEntity(entities.llamaOneId(), baseLocation.clone().add(1.5D, 0.0D, 0.5D), direction, stationary);
        syncEntity(entities.llamaTwoId(), baseLocation.clone().add(-0.5D, 0.0D, 0.5D), direction, stationary);
    }

    public UUID findCaravanId(Entity entity) {
        String value = entity.getPersistentDataContainer().get(caravanIdKey, PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public CaravanEntityRole findRole(Entity entity) {
        String value = entity.getPersistentDataContainer().get(caravanEntityRoleKey, PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        try {
            return CaravanEntityRole.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public Location getCaravanLocation(UUID caravanId) {
        cleanupInvalidState(caravanId);

        SpawnedCaravanEntities entities = spawnedCaravans.get(caravanId);
        if (entities != null) {
            Entity trader = Bukkit.getEntity(entities.traderId());
            if (trader != null && trader.isValid() && !trader.isDead()) {
                return trader.getLocation();
            }
            Entity llamaOne = Bukkit.getEntity(entities.llamaOneId());
            if (llamaOne != null && llamaOne.isValid() && !llamaOne.isDead()) {
                return llamaOne.getLocation();
            }
            Entity llamaTwo = Bukkit.getEntity(entities.llamaTwoId());
            if (llamaTwo != null && llamaTwo.isValid() && !llamaTwo.isDead()) {
                return llamaTwo.getLocation();
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                UUID found = findCaravanId(entity);
                if (caravanId.equals(found)) {
                    return entity.getLocation();
                }
            }
        }
        return null;
    }

    private void configureTrader(WanderingTrader trader, CaravanRecord caravan) {
        configureLivingEntity(trader, caravan, CaravanEntityRole.TRADER, configManager.getPhysicalTraderNameFormat());
        trader.setRecipes(java.util.List.of());
        trader.setCanDrinkMilk(false);
        trader.setCanDrinkPotion(false);
        trader.setDespawnDelay(-1);
    }

    private void configureLlama(TraderLlama llama, CaravanRecord caravan, CaravanEntityRole role, String nameFormat) {
        configureLivingEntity(llama, caravan, role, nameFormat);
        llama.setStrength(1);
    }

    public synchronized void syncVisualHealth(CaravanRecord caravan) {
        SpawnedCaravanEntities entities = spawnedCaravans.get(caravan.id());
        if (entities == null) {
            return;
        }

        syncVisualHealth(entities.traderId(), caravan);
        syncVisualHealth(entities.llamaOneId(), caravan);
        syncVisualHealth(entities.llamaTwoId(), caravan);
    }

    private void configureLivingEntity(LivingEntity entity, CaravanRecord caravan, CaravanEntityRole role, String nameFormat) {
        entity.setPersistent(true);
        entity.setRemoveWhenFarAway(false);
        entity.setAI(true);
        entity.setCollidable(false);
        entity.customName(LEGACY_SERIALIZER.deserialize(
            nameFormat
                .replace("%player%", caravan.ownerName())
                .replace("%name%", caravan.name())
                .replace("%id%", caravan.id().toString().substring(0, 8))
        ));
        entity.setCustomNameVisible(true);
        entity.getActivePotionEffects().forEach(effect -> entity.removePotionEffect(effect.getType()));

        if (entity.getAttribute(Attribute.MAX_HEALTH) != null) {
            entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(VISUAL_ENTITY_MAX_HEALTH);
        }
        syncVisualHealth(entity, caravan);

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(caravanIdKey, PersistentDataType.STRING, caravan.id().toString());
        pdc.set(caravanEntityRoleKey, PersistentDataType.STRING, role.name());
    }

    private void syncVisualHealth(UUID entityId, CaravanRecord caravan) {
        Entity entity = Bukkit.getEntity(entityId);
        if (entity instanceof LivingEntity livingEntity) {
            syncVisualHealth(livingEntity, caravan);
        }
    }

    private void syncVisualHealth(LivingEntity entity, CaravanRecord caravan) {
        if (caravan.maxHp() <= 0) {
            return;
        }

        double maxHealth = entity.getAttribute(Attribute.MAX_HEALTH) == null
            ? VISUAL_ENTITY_MAX_HEALTH
            : entity.getAttribute(Attribute.MAX_HEALTH).getValue();
        double ratio = Math.max(0.0D, Math.min(1.0D, caravan.hp() / (double) caravan.maxHp()));
        double targetHealth = caravan.hp() > 0
            ? Math.max(1.0D, maxHealth * ratio)
            : 1.0D;
        entity.setHealth(Math.min(maxHealth, targetHealth));
    }

    private void cleanupInvalidState(UUID caravanId) {
        SpawnedCaravanEntities entities = spawnedCaravans.get(caravanId);
        if (entities == null) {
            return;
        }

        if (!isAlive(entities.traderId()) || !isAlive(entities.llamaOneId()) || !isAlive(entities.llamaTwoId())) {
            despawnCaravan(caravanId);
        }
    }

    public synchronized void cleanupAllTrackedEntities() {
        List<UUID> caravanIds = new ArrayList<>(spawnedCaravans.keySet());
        for (UUID caravanId : caravanIds) {
            cleanupInvalidState(caravanId);
        }
    }

    private void cleanupOrphanedEntitiesOnEnable() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (findCaravanId(entity) != null) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            logger.info("Removed " + removed + " orphaned caravan entities during enable cleanup.");
        }
    }

    private boolean isAlive(UUID entityId) {
        Entity entity = Bukkit.getEntity(entityId);
        return entity != null && entity.isValid() && !entity.isDead();
    }

    private void removeEntity(UUID entityId) {
        Entity entity = Bukkit.getEntity(entityId);
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    private void syncEntity(UUID entityId, Location target, Vector direction, boolean stationary) {
        Entity entity = Bukkit.getEntity(entityId);
        if (!(entity instanceof LivingEntity livingEntity) || !entity.isValid() || entity.isDead()) {
            return;
        }

        if (!entity.getWorld().equals(target.getWorld())) {
            entity.teleport(target);
            return;
        }

        if (livingEntity instanceof Mob mob) {
            mob.setAI(!stationary);
        }

        Location current = entity.getLocation();
        Vector offset = target.toVector().subtract(current.toVector());
        double distance = offset.length();
        Vector normalizedDirection = direction == null || direction.lengthSquared() <= 0.0001D
            ? offset.clone()
            : direction.clone();

        if (normalizedDirection.lengthSquared() > 0.0001D) {
            normalizedDirection.normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-normalizedDirection.getX(), normalizedDirection.getZ()));
            entity.setRotation(yaw, 0.0F);
        }

        if (stationary) {
            entity.setVelocity(new Vector());
            if (distance > 1.5D) {
                entity.teleport(target);
            }
            return;
        }

        if (distance >= configManager.getProjectionTeleportCorrectionDistance()) {
            entity.teleport(target);
            return;
        }

        if (distance <= 0.75D) {
            entity.setVelocity(new Vector());
            return;
        }

        if (livingEntity instanceof Mob mob && tryPathfind(mob, target)) {
            if (distance <= configManager.getProjectionSmoothFollowDistance()) {
                entity.setVelocity(offset.normalize().multiply(0.18D));
            }
            return;
        }

        Vector velocity = offset.normalize().multiply(distance <= configManager.getProjectionSmoothFollowDistance() ? 0.22D : 0.35D);
        entity.setVelocity(velocity);
    }

    private boolean tryPathfind(Mob mob, Location target) {
        try {
            Object pathfinder = mob.getClass().getMethod("getPathfinder").invoke(mob);
            if (pathfinder == null) {
                return false;
            }
            pathfinder.getClass().getMethod("moveTo", Location.class, double.class).invoke(pathfinder, target, 1.0D);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private record SpawnedCaravanEntities(
        UUID traderId,
        UUID llamaOneId,
        UUID llamaTwoId
    ) {
    }

    public record ProjectionDebugInfo(
        boolean tracked,
        UUID traderId,
        boolean traderValid,
        UUID llamaOneId,
        boolean llamaOneValid,
        UUID llamaTwoId,
        boolean llamaTwoValid
    ) {
    }
}
