package net.meltarion.caravans.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigManager {

    private static final Material FALLBACK_CURRENCY = Material.GOLD_INGOT;

    private final JavaPlugin plugin;

    private FileConfiguration config;
    private int validatedInventorySize;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.validatedInventorySize = resolveInventorySize();
    }

    public void reload() {
        this.config = plugin.getConfig();
        this.validatedInventorySize = resolveInventorySize();
    }

    public Material getCurrencyItem() {
        String configuredMaterial = config.getString("currency-item", FALLBACK_CURRENCY.name());
        Material material = Material.matchMaterial(configuredMaterial);
        if (material != null) {
            return material;
        }

        plugin.getLogger().warning("Invalid currency-item '" + configuredMaterial + "' in config.yml. Falling back to " + FALLBACK_CURRENCY.name() + '.');
        return FALLBACK_CURRENCY;
    }

    public int getDefaultCaravanLimit() {
        return Math.max(0, config.getInt("default-caravan-limit", 1));
    }

    public Map<String, Integer> getPermissionLimits() {
        ConfigurationSection section = config.getConfigurationSection("permission-limits");
        if (section == null) {
            return Collections.emptyMap();
        }

        Map<String, Integer> limits = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            limits.put(key, Math.max(0, section.getInt(key, getDefaultCaravanLimit())));
        }
        return Collections.unmodifiableMap(limits);
    }

    public int getCaravanMaxHp() {
        return Math.max(1, config.getInt("caravan.max-hp", 5000));
    }

    public boolean isVirtualMovementEnabled() {
        return config.getBoolean("caravan.virtual-movement-enabled", true);
    }

    public int getCaravanInventorySize() {
        return validatedInventorySize;
    }

    public String getCaravanInventoryTitle() {
        return config.getString("caravan.inventory-title", "&6Caravan: &e%name%");
    }

    public int getMaxCaravanNameLength() {
        return Math.max(1, config.getInt("caravan.max-name-length", 24));
    }

    public boolean isCaravanLicenseEnabled() {
        return config.getBoolean("caravan-license.enabled", true);
    }

    public Material getCaravanLicenseMaterial() {
        String configuredMaterial = config.getString("caravan-license.material", Material.PAPER.name());
        Material material = Material.matchMaterial(configuredMaterial);
        if (material != null) {
            return material;
        }

        plugin.getLogger().warning("Invalid caravan-license.material '" + configuredMaterial + "' in config.yml. Falling back to PAPER.");
        return Material.PAPER;
    }

    public Integer getCaravanLicenseCustomModelData() {
        if (!config.contains("caravan-license.custom-model-data")) {
            return null;
        }
        return config.getInt("caravan-license.custom-model-data");
    }

    public String getCaravanLicenseDisplayName() {
        return config.getString("caravan-license.display-name", "&6Caravan License");
    }

    public List<String> getCaravanLicenseLore() {
        return config.getStringList("caravan-license.lore");
    }

    public boolean isCaravanLicenseRequireDisplayName() {
        return config.getBoolean("caravan-license.require-display-name", true);
    }

    public boolean isCaravanLicenseRequireLore() {
        return config.getBoolean("caravan-license.require-lore", false);
    }

    public boolean isCaravanLicenseRequireCustomModelData() {
        return config.getBoolean("caravan-license.require-custom-model-data", true);
    }

    public boolean isCaravanLicenseConsumeOnCreate() {
        return config.getBoolean("caravan-license.consume-on-create", true);
    }

    public boolean isCaravanLicenseRightClickCreateAllowed() {
        return config.getBoolean("caravan-license.allow-right-click-create", true);
    }

    public int getTradeSetupTimeoutSeconds() {
        return Math.max(5, config.getInt("trade.setup-timeout-seconds", 60));
    }

    public Set<Material> getTradeBuyCatalogBlacklist() {
        return config.getStringList("trade.buy-catalog.blacklist").stream()
            .map(Material::matchMaterial)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isPhysicalCaravanEnabled() {
        return config.getBoolean("physical-caravan.enabled", true);
    }

    public boolean shouldDespawnPhysicalCaravansOnDisable() {
        return config.getBoolean("physical-caravan.despawn-on-plugin-disable", true);
    }

    public boolean shouldPreventDuplicatePhysicalSpawn() {
        return config.getBoolean("physical-caravan.prevent-duplicate-spawn", true);
    }

    public int getPhysicalCaravanSpawnRadius() {
        return Math.max(0, config.getInt("physical-caravan.spawn-radius", 3));
    }

    public double getPhysicalTraderHealth() {
        return Math.max(1.0D, config.getDouble("physical-caravan.entity-health.trader", 100.0D));
    }

    public double getPhysicalLlamaHealth() {
        return Math.max(1.0D, config.getDouble("physical-caravan.entity-health.llama", 100.0D));
    }

    public String getPhysicalTraderNameFormat() {
        return config.getString("physical-caravan.names.trader", "&6Caravan %player%");
    }

    public String getPhysicalLlamaOneNameFormat() {
        return config.getString("physical-caravan.names.llama-1", "&eCaravan Llama");
    }

    public String getPhysicalLlamaTwoNameFormat() {
        return config.getString("physical-caravan.names.llama-2", "&eCaravan Llama");
    }

    public boolean isPhysicalOwnerOpensSetupEnabled() {
        return config.getBoolean("physical-caravan.interaction.owner-opens-setup", true);
    }

    public boolean isPhysicalStrangerMessageEnabled() {
        return config.getBoolean("physical-caravan.interaction.stranger-message-enabled", true);
    }

    public boolean isPhysicalDamageEnabled() {
        return config.getBoolean("physical-caravan.damage.enabled", true);
    }

    public boolean shouldSyncPhysicalDamageToCaravanHp() {
        return config.getBoolean("physical-caravan.damage.sync-damage-to-caravan-hp", true);
    }

    public boolean isDebugEnabled() {
        return config.getBoolean("debug.enabled", false);
    }

    public String getMessage(String path) {
        return config.getString("messages." + path, "");
    }

    public List<String> getMessageList(String path) {
        return config.getStringList("messages." + path);
    }

    private int resolveInventorySize() {
        int configuredSize = config.getInt("caravan.inventory-size", 27);
        if (configuredSize == 9
            || configuredSize == 18
            || configuredSize == 27
            || configuredSize == 36
            || configuredSize == 45
            || configuredSize == 54) {
            return configuredSize;
        }

        plugin.getLogger().warning("Invalid caravan.inventory-size '" + configuredSize + "' in config.yml. Falling back to 27.");
        return 27;
    }
}
