package net.meltarion.caravans.service;

import java.util.Map;
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
import org.bukkit.entity.Player;
import org.bukkit.entity.TraderLlama;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class CaravanEntityService {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

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

    public synchronized boolean isSpawned(UUID caravanId) {
        cleanupInvalidState(caravanId);
        return spawnedCaravans.containsKey(caravanId);
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

    private void configureTrader(WanderingTrader trader, CaravanRecord caravan) {
        configureLivingEntity(trader, caravan, CaravanEntityRole.TRADER, configManager.getPhysicalTraderNameFormat(), configManager.getPhysicalTraderHealth());
        trader.setRecipes(java.util.List.of());
        trader.setCanDrinkMilk(false);
        trader.setCanDrinkPotion(false);
        trader.setDespawnDelay(-1);
    }

    private void configureLlama(TraderLlama llama, CaravanRecord caravan, CaravanEntityRole role, String nameFormat) {
        configureLivingEntity(llama, caravan, role, nameFormat, configManager.getPhysicalLlamaHealth());
        llama.setStrength(1);
    }

    private void configureLivingEntity(LivingEntity entity, CaravanRecord caravan, CaravanEntityRole role, String nameFormat, double maxHealth) {
        entity.setPersistent(true);
        entity.setRemoveWhenFarAway(false);
        entity.setAI(false);
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
            entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealth);
        }
        entity.setHealth(Math.min(maxHealth, entity.getAttribute(Attribute.MAX_HEALTH) == null ? maxHealth : entity.getAttribute(Attribute.MAX_HEALTH).getValue()));

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(caravanIdKey, PersistentDataType.STRING, caravan.id().toString());
        pdc.set(caravanEntityRoleKey, PersistentDataType.STRING, role.name());
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

    private record SpawnedCaravanEntities(
        UUID traderId,
        UUID llamaOneId,
        UUID llamaTwoId
    ) {
    }
}
