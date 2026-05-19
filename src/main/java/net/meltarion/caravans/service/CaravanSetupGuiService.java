package net.meltarion.caravans.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.inventory.CaravanBuyCategoryHolder;
import net.meltarion.caravans.inventory.CaravanBuyMaterialHolder;
import net.meltarion.caravans.inventory.CaravanInfoHolder;
import net.meltarion.caravans.inventory.CaravanRouteSetupHolder;
import net.meltarion.caravans.inventory.CaravanSellSetupHolder;
import net.meltarion.caravans.inventory.CaravanSetupHolder;
import net.meltarion.caravans.inventory.CaravanTownSelectHolder;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.model.CaravanRouteStopRecord;
import net.meltarion.caravans.storage.StorageException;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class CaravanSetupGuiService {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static final int BUY_MATERIALS_PER_PAGE = 45;
    private static final int ROUTE_STOPS_PER_PAGE = 45;
    private static final int TOWNS_PER_PAGE = 45;

    private final ConfigManager configManager;
    private final CaravanInventoryService inventoryService;
    private final TradeOperationService tradeOperationService;
    private final CaravanRouteService caravanRouteService;
    private final TownyIntegrationService townyIntegrationService;

    public CaravanSetupGuiService(
        ConfigManager configManager,
        CaravanInventoryService inventoryService,
        TradeOperationService tradeOperationService,
        CaravanRouteService caravanRouteService,
        TownyIntegrationService townyIntegrationService
    ) {
        this.configManager = configManager;
        this.inventoryService = inventoryService;
        this.tradeOperationService = tradeOperationService;
        this.caravanRouteService = caravanRouteService;
        this.townyIntegrationService = townyIntegrationService;
    }

    public void openMainSetup(Player player, CaravanRecord caravan) {
        CaravanSetupHolder holder = new CaravanSetupHolder(caravan.id(), caravan.name());
        Inventory inventory = Bukkit.createInventory(holder, 27, LEGACY_SERIALIZER.deserialize("&8Caravan Setup"));
        holder.setInventory(inventory);

        inventory.setItem(10, createMenuItem(Material.CHEST, "&6Storage", List.of("&7Open caravan storage.")));
        inventory.setItem(11, createMenuItem(Material.EMERALD, "&aSell Items", List.of("&7Select caravan items to sell.")));
        inventory.setItem(12, createMenuItem(Material.WRITABLE_BOOK, "&bBuy Orders", List.of("&7Create caravan buy orders.")));
        inventory.setItem(13, createMenuItem(Material.BOOK, "&eExisting Trades", List.of("&7Manage active trade operations.")));
        inventory.setItem(14, createMenuItem(Material.COMPASS, "&dInfo", List.of("&7View caravan details.")));
        inventory.setItem(15, createMenuItem(Material.MAP, "&9Route", List.of("&7Configure caravan route stops.")));
        inventory.setItem(16, createMenuItem(Material.BARRIER, "&cClose", List.of("&7Close this menu.")));

        player.openInventory(inventory);
    }

    public void openSellSetup(Player player, CaravanRecord caravan) throws StorageException {
        CaravanSellSetupHolder holder = new CaravanSellSetupHolder(caravan.id(), caravan.name());
        Inventory inventory = Bukkit.createInventory(
            holder,
            configManager.getCaravanInventorySize(),
            LEGACY_SERIALIZER.deserialize("&8Sell Setup: &6" + caravan.name())
        );
        holder.setInventory(inventory);

        ItemStack[] snapshot = inventoryService.getInventorySnapshot(caravan);
        ItemStack[] resized = new ItemStack[inventory.getSize()];
        System.arraycopy(snapshot, 0, resized, 0, Math.min(snapshot.length, resized.length));
        inventory.setContents(resized);
        player.openInventory(inventory);
    }

    public void openBuyCategoryCatalog(Player player, CaravanRecord caravan) {
        CaravanBuyCategoryHolder holder = new CaravanBuyCategoryHolder(caravan.id(), caravan.name());
        Inventory inventory = Bukkit.createInventory(holder, 27, LEGACY_SERIALIZER.deserialize("&8Buy Categories"));
        holder.setInventory(inventory);

        List<TradeCatalogCategory> categories = getAvailableCategories();
        if (categories.isEmpty()) {
            inventory.setItem(13, createMenuItem(Material.BARRIER, "&cCatalog Empty", List.of("&7No buy catalog entries are available.")));
        } else {
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22};
            for (int index = 0; index < Math.min(categories.size(), slots.length); index++) {
                TradeCatalogCategory category = categories.get(index);
                inventory.setItem(slots[index], createMenuItem(
                    category.getIcon(),
                    "&6" + category.getDisplayName(),
                    List.of("&7Open materials in this category.")
                ));
            }
        }

        inventory.setItem(26, createMenuItem(Material.BARRIER, "&cClose", List.of("&7Close this menu.")));
        player.openInventory(inventory);
    }

    public void openBuyMaterialCatalog(Player player, CaravanRecord caravan, TradeCatalogCategory category, int page) {
        CaravanBuyMaterialHolder holder = new CaravanBuyMaterialHolder(caravan.id(), caravan.name(), category, page);
        Inventory inventory = Bukkit.createInventory(
            holder,
            54,
            LEGACY_SERIALIZER.deserialize("&8Buy: &6" + category.getDisplayName())
        );
        holder.setInventory(inventory);

        List<Material> materials = getMaterialsForCategory(category);
        if (materials.isEmpty()) {
            inventory.setItem(22, createMenuItem(Material.BARRIER, "&cEmpty Category", List.of("&7No materials are available in this category.")));
        } else {
            int start = page * BUY_MATERIALS_PER_PAGE;
            int end = Math.min(start + BUY_MATERIALS_PER_PAGE, materials.size());
            for (int slot = 0; slot < end - start; slot++) {
                Material material = materials.get(start + slot);
                inventory.setItem(slot, createMenuItem(material, "&f" + prettifyMaterial(material), List.of("&7Click to create a buy order.")));
            }
        }

        inventory.setItem(45, createMenuItem(Material.ARROW, "&eBack", List.of("&7Return to categories.")));
        if (page > 0) {
            inventory.setItem(48, createMenuItem(Material.SPECTRAL_ARROW, "&ePrevious", List.of("&7Go to previous page.")));
        }
        if ((page + 1) * BUY_MATERIALS_PER_PAGE < materials.size()) {
            inventory.setItem(50, createMenuItem(Material.ARROW, "&eNext", List.of("&7Go to next page.")));
        }
        inventory.setItem(53, createMenuItem(Material.BARRIER, "&cClose", List.of("&7Close this menu.")));

        player.openInventory(inventory);
    }

    public void openInfo(Player player, CaravanRecord caravan) {
        CaravanInfoHolder holder = new CaravanInfoHolder(caravan.id());
        Inventory inventory = Bukkit.createInventory(holder, 27, LEGACY_SERIALIZER.deserialize("&8Caravan Info"));
        holder.setInventory(inventory);

        inventory.setItem(13, createMenuItem(
            Material.COMPASS,
            "&6" + caravan.name(),
            List.of(
                "&7ID: &f" + caravan.id().toString().substring(0, 8),
                "&7Owner: &f" + caravan.ownerName(),
                "&7Status: &f" + caravan.status().name(),
                "&7HP: &f" + caravan.hp() + "&7/&f" + caravan.maxHp()
            )
        ));
        inventory.setItem(26, createMenuItem(Material.BARRIER, "&cClose", List.of("&7Close this menu.")));

        player.openInventory(inventory);
    }

    public void openRouteSetup(Player player, CaravanRecord caravan, int page) {
        CaravanRouteSetupHolder holder = new CaravanRouteSetupHolder(caravan.id(), page);
        Inventory inventory = Bukkit.createInventory(holder, 54, LEGACY_SERIALIZER.deserialize("&8Route Setup"));
        holder.setInventory(inventory);
        populateRouteSetup(inventory, caravan, page);
        player.openInventory(inventory);
    }

    public void populateRouteSetup(Inventory inventory, CaravanRecord caravan, int page) {
        inventory.clear();
        List<CaravanRouteStopRecord> stops = caravanRouteService.getRouteStops(caravan.id());
        int start = page * ROUTE_STOPS_PER_PAGE;
        int end = Math.min(start + ROUTE_STOPS_PER_PAGE, stops.size());
        for (int slot = 0; slot < end - start; slot++) {
            inventory.setItem(slot, createRouteStopItem(stops.get(start + slot)));
        }

        inventory.setItem(45, createMenuItem(Material.ARROW, "&eBack", List.of("&7Return to caravan setup.")));
        inventory.setItem(46, createMenuItem(Material.EMERALD, "&aAdd Route Stop", List.of("&7Choose a Towny town and stop time.")));
        inventory.setItem(48, createMenuItem(Material.MINECART, "&aStart Route", List.of("&7Begin running this caravan route.")));
        inventory.setItem(49, createMenuItem(Material.REDSTONE, "&cStop Route", List.of("&7Stop the running route.")));
        inventory.setItem(50, createMenuItem(Material.LAVA_BUCKET, "&cClear Route", List.of("&7Remove all configured route stops.")));
        if (page > 0) {
            inventory.setItem(51, createMenuItem(Material.SPECTRAL_ARROW, "&ePrevious", List.of("&7Go to previous page.")));
        }
        if ((page + 1) * ROUTE_STOPS_PER_PAGE < stops.size()) {
            inventory.setItem(52, createMenuItem(Material.ARROW, "&eNext", List.of("&7Go to next page.")));
        }
        inventory.setItem(53, createMenuItem(Material.BARRIER, "&cClose", List.of("&7Close this menu.")));
    }

    public void openTownSelection(Player player, CaravanRecord caravan, int page) {
        CaravanTownSelectHolder holder = new CaravanTownSelectHolder(caravan.id(), page);
        Inventory inventory = Bukkit.createInventory(holder, 54, LEGACY_SERIALIZER.deserialize("&8Route Towns"));
        holder.setInventory(inventory);
        populateTownSelection(inventory, player, page);
        player.openInventory(inventory);
    }

    public void populateTownSelection(Inventory inventory, Player player, int page) {
        inventory.clear();
        List<RouteTownOption> towns = townyIntegrationService.listAvailableRouteTowns(player);
        int start = page * TOWNS_PER_PAGE;
        int end = Math.min(start + TOWNS_PER_PAGE, towns.size());
        for (int slot = 0; slot < end - start; slot++) {
            inventory.setItem(slot, createTownItem(towns.get(start + slot)));
        }

        inventory.setItem(45, createMenuItem(Material.ARROW, "&eBack", List.of("&7Return to route setup.")));
        if (page > 0) {
            inventory.setItem(48, createMenuItem(Material.SPECTRAL_ARROW, "&ePrevious", List.of("&7Go to previous page.")));
        }
        if ((page + 1) * TOWNS_PER_PAGE < towns.size()) {
            inventory.setItem(50, createMenuItem(Material.ARROW, "&eNext", List.of("&7Go to next page.")));
        }
        inventory.setItem(53, createMenuItem(Material.BARRIER, "&cClose", List.of("&7Close this menu.")));
    }

    public List<RouteTownOption> getAvailableRouteTowns(Player player) {
        return townyIntegrationService.listAvailableRouteTowns(player);
    }

    public List<TradeCatalogCategory> getAvailableCategories() {
        return getCatalog().entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .map(Map.Entry::getKey)
            .toList();
    }

    public List<Material> getMaterialsForCategory(TradeCatalogCategory category) {
        return List.copyOf(getCatalog().getOrDefault(category, List.of()));
    }

    private Map<TradeCatalogCategory, List<Material>> getCatalog() {
        Map<TradeCatalogCategory, List<Material>> categorized = new EnumMap<>(TradeCatalogCategory.class);
        for (TradeCatalogCategory category : TradeCatalogCategory.values()) {
            categorized.put(category, new ArrayList<>());
        }

        Set<Material> blacklist = configManager.getTradeBuyCatalogBlacklist();
        for (Material material : Material.values()) {
            if (!material.isItem() || material == Material.AIR || blacklist.contains(material)) {
                continue;
            }
            categorized.get(classify(material)).add(material);
        }

        categorized.values().forEach(list -> list.sort(Comparator.comparing(Material::name)));
        return categorized;
    }

    private TradeCatalogCategory classify(Material material) {
        String name = material.name();

        if (containsAny(name, "ORE", "INGOT", "RAW_", "NUGGET", "DIAMOND", "EMERALD", "LAPIS", "QUARTZ", "AMETHYST")) {
            return TradeCatalogCategory.ORES_INGOTS;
        }
        if (containsAny(name, "LOG", "WOOD", "PLANKS", "SAPLING", "LEAVES", "STICK")) {
            return TradeCatalogCategory.WOOD;
        }
        if (containsAny(name, "WHEAT", "CARROT", "POTATO", "BEETROOT", "SEEDS", "PUMPKIN", "MELON", "SUGAR_CANE", "CACTUS", "COCOA")) {
            return TradeCatalogCategory.FARMING;
        }
        if (material.isEdible() || containsAny(name, "BREAD", "APPLE", "COOKED", "STEAK", "PORKCHOP", "MUTTON", "RABBIT", "SOUP", "PIE")) {
            return TradeCatalogCategory.FOOD;
        }
        if (containsAny(name, "REDSTONE", "REPEATER", "COMPARATOR", "PISTON", "OBSERVER", "HOPPER", "DISPENSER", "DROPPER")) {
            return TradeCatalogCategory.REDSTONE;
        }
        if (containsAny(name, "PICKAXE", "AXE", "SHOVEL", "HOE", "SHEARS", "FISHING_ROD", "FLINT_AND_STEEL", "BRUSH")) {
            return TradeCatalogCategory.TOOLS;
        }
        if (containsAny(name, "SWORD", "BOW", "CROSSBOW", "TRIDENT", "SHIELD", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "ARROW")) {
            return TradeCatalogCategory.COMBAT;
        }
        if (containsAny(name, "BONE", "STRING", "ROTTEN_FLESH", "SPIDER_EYE", "ENDER_PEARL", "BLAZE_ROD", "GHAST_TEAR", "LEATHER", "FEATHER")) {
            return TradeCatalogCategory.MOB_DROPS;
        }
        if (containsAny(name, "DIRT", "GRASS", "SAND", "GRAVEL", "STONE", "DEEPSLATE", "TUFF", "CALCITE", "ICE", "SNOW", "CLAY")) {
            return TradeCatalogCategory.NATURAL_BLOCKS;
        }
        if (material.isBlock()) {
            return TradeCatalogCategory.BUILDING_BLOCKS;
        }
        return TradeCatalogCategory.MISC;
    }

    private boolean containsAny(String name, String... fragments) {
        for (String fragment : fragments) {
            if (name.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private ItemStack createMenuItem(Material material, String name, List<String> loreLines) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return itemStack;
        }

        itemMeta.displayName(LEGACY_SERIALIZER.deserialize(name));
        List<Component> lore = loreLines.stream()
            .map(LEGACY_SERIALIZER::deserialize)
            .collect(Collectors.toList());
        itemMeta.lore(lore);
        itemMeta.addItemFlags(ItemFlag.values());
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    private ItemStack createRouteStopItem(CaravanRouteStopRecord stop) {
        return createMenuItem(
            Material.FILLED_MAP,
            "&6Stop #" + (stop.stopOrder() + 1) + " &7- &f" + stop.townName(),
            List.of(
                "&7Town: &f" + stop.townName(),
                "&7World: &f" + stop.worldName(),
                "&7Position: &f" + String.format(Locale.US, "%.1f %.1f %.1f", stop.x(), stop.y(), stop.z()),
                "&7Duration: &f" + Duration.ofSeconds(stop.stopDurationSeconds()).toMinutes() + " min",
                "&eShift-click to remove."
            )
        );
    }

    private ItemStack createTownItem(RouteTownOption town) {
        Material icon = switch (town.relation()) {
            case OWN -> Material.GREEN_BANNER;
            case ALLIED -> Material.LIGHT_BLUE_BANNER;
            case NEUTRAL -> Material.WHITE_BANNER;
            case ENEMY -> Material.RED_BANNER;
        };
        return createMenuItem(
            icon,
            "&6" + town.townName(),
            List.of(
                "&7Relation: &f" + town.relation().name(),
                "&7Shop Plots: &f" + town.shopPlotCount(),
                "&eClick to pick a random Shop Plot."
            )
        );
    }

    private String prettifyMaterial(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        return java.util.Arrays.stream(parts)
            .map(part -> part.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1))
            .collect(Collectors.joining(" "));
    }
}
