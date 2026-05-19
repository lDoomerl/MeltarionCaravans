package net.meltarion.caravans.service;

import org.bukkit.Material;

public enum TradeCatalogCategory {
    BUILDING_BLOCKS("Building Blocks", Material.BRICKS),
    NATURAL_BLOCKS("Natural Blocks", Material.GRASS_BLOCK),
    ORES_INGOTS("Ores & Ingots", Material.IRON_INGOT),
    WOOD("Wood", Material.OAK_LOG),
    FARMING("Farming", Material.WHEAT),
    FOOD("Food", Material.BREAD),
    MOB_DROPS("Mob Drops", Material.BONE),
    REDSTONE("Redstone", Material.REDSTONE),
    TOOLS("Tools", Material.IRON_PICKAXE),
    COMBAT("Combat", Material.IRON_SWORD),
    MISC("Misc", Material.CHEST);

    private final String displayName;
    private final Material icon;

    TradeCatalogCategory(String displayName, Material icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }
}
