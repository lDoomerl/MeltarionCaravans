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
import net.meltarion.caravans.config.GuiConfigManager;
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
    private final GuiConfigManager guiConfigManager;
    private final CaravanInventoryService inventoryService;
    private final TradeOperationService tradeOperationService;
    private final CaravanRouteService caravanRouteService;
    private final TownyIntegrationService townyIntegrationService;

    public CaravanSetupGuiService(
        ConfigManager configManager,
        GuiConfigManager guiConfigManager,
        CaravanInventoryService inventoryService,
        TradeOperationService tradeOperationService,
        CaravanRouteService caravanRouteService,
        TownyIntegrationService townyIntegrationService
    ) {
        this.configManager = configManager;
        this.guiConfigManager = guiConfigManager;
        this.inventoryService = inventoryService;
        this.tradeOperationService = tradeOperationService;
        this.caravanRouteService = caravanRouteService;
        this.townyIntegrationService = townyIntegrationService;
    }

    public void openMainSetup(Player player, CaravanRecord caravan) {
        CaravanSetupHolder holder = new CaravanSetupHolder(caravan.id(), caravan.name());
        Inventory inventory = Bukkit.createInventory(holder, 27, LEGACY_SERIALIZER.deserialize(guiConfigManager.getString("setup.title", "&8Caravan Setup")));
        holder.setInventory(inventory);

        inventory.setItem(10, createMenuItem(guiConfigManager.getMaterial("setup.buttons.storage.material", Material.CHEST), guiConfigManager.getString("setup.buttons.storage.name", "&6Storage"), guiConfigManager.getStringList("setup.buttons.storage.lore", List.of("&7Open caravan storage."))));
        inventory.setItem(11, createMenuItem(guiConfigManager.getMaterial("setup.buttons.sell-items.material", Material.EMERALD), guiConfigManager.getString("setup.buttons.sell-items.name", "&aSell Items"), guiConfigManager.getStringList("setup.buttons.sell-items.lore", List.of("&7Select caravan items to sell."))));
        inventory.setItem(12, createMenuItem(guiConfigManager.getMaterial("setup.buttons.buy-orders.material", Material.WRITABLE_BOOK), guiConfigManager.getString("setup.buttons.buy-orders.name", "&bBuy Orders"), guiConfigManager.getStringList("setup.buttons.buy-orders.lore", List.of("&7Create caravan buy orders."))));
        inventory.setItem(13, createMenuItem(guiConfigManager.getMaterial("setup.buttons.existing-trades.material", Material.BOOK), guiConfigManager.getString("setup.buttons.existing-trades.name", "&eExisting Trades"), guiConfigManager.getStringList("setup.buttons.existing-trades.lore", List.of("&7Manage active trade operations."))));
        inventory.setItem(14, createMenuItem(guiConfigManager.getMaterial("setup.buttons.info.material", Material.COMPASS), guiConfigManager.getString("setup.buttons.info.name", "&dInfo"), guiConfigManager.getStringList("setup.buttons.info.lore", List.of("&7View caravan details."))));
        inventory.setItem(15, createMenuItem(guiConfigManager.getMaterial("setup.buttons.route.material", Material.MAP), guiConfigManager.getString("setup.buttons.route.name", "&9Route"), guiConfigManager.getStringList("setup.buttons.route.lore", List.of("&7Configure caravan route stops."))));
        inventory.setItem(16, createMenuItem(guiConfigManager.getMaterial("setup.buttons.close.material", Material.BARRIER), guiConfigManager.getString("setup.buttons.close.name", "&cClose"), guiConfigManager.getStringList("setup.buttons.close.lore", List.of("&7Close this menu."))));

        player.openInventory(inventory);
    }

    public void openSellSetup(Player player, CaravanRecord caravan) throws StorageException {
        CaravanSellSetupHolder holder = new CaravanSellSetupHolder(caravan.id(), caravan.name());
        Inventory inventory = Bukkit.createInventory(
            holder,
            configManager.getCaravanInventorySize(),
            LEGACY_SERIALIZER.deserialize(guiConfigManager.getString("sell-setup.title", "&8Sell Setup: &6%name%").replace("%name%", caravan.name()))
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
        Inventory inventory = Bukkit.createInventory(holder, 27, LEGACY_SERIALIZER.deserialize(guiConfigManager.getString("buy-catalog.categories.title", "&8Buy Categories")));
        holder.setInventory(inventory);

        List<TradeCatalogCategory> categories = getAvailableCategories();
        if (categories.isEmpty()) {
            inventory.setItem(13, createMenuItem(Material.BARRIER, guiConfigManager.getString("buy-catalog.categories.empty.name", "&cCatalog Empty"), guiConfigManager.getStringList("buy-catalog.categories.empty.lore", List.of("&7No buy catalog entries are available."))));
        } else {
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22};
            for (int index = 0; index < Math.min(categories.size(), slots.length); index++) {
                TradeCatalogCategory category = categories.get(index);
                inventory.setItem(slots[index], createMenuItem(
                    category.getIcon(),
                    guiConfigManager.getString("buy-catalog.categories.button.name", "&6%category%").replace("%category%", category.getDisplayName()),
                    replacePlaceholders(guiConfigManager.getStringList("buy-catalog.categories.button.lore", List.of("&7Open materials in this category.")), Map.of("category", category.getDisplayName()))
                ));
            }
        }

        inventory.setItem(26, createMenuItem(Material.BARRIER, guiConfigManager.getString("buy-catalog.shared.close.name", "&cClose"), guiConfigManager.getStringList("buy-catalog.shared.close.lore", List.of("&7Close this menu."))));
        player.openInventory(inventory);
    }

    public void openBuyMaterialCatalog(Player player, CaravanRecord caravan, TradeCatalogCategory category, int page) {
        CaravanBuyMaterialHolder holder = new CaravanBuyMaterialHolder(caravan.id(), caravan.name(), category, page);
        Inventory inventory = Bukkit.createInventory(
            holder,
            54,
            LEGACY_SERIALIZER.deserialize(guiConfigManager.getString("buy-catalog.materials.title", "&8Buy: &6%category%").replace("%category%", category.getDisplayName()))
        );
        holder.setInventory(inventory);

        List<Material> materials = getMaterialsForCategory(category);
        if (materials.isEmpty()) {
            inventory.setItem(22, createMenuItem(Material.BARRIER, guiConfigManager.getString("buy-catalog.materials.empty.name", "&cEmpty Category"), guiConfigManager.getStringList("buy-catalog.materials.empty.lore", List.of("&7No materials are available in this category."))));
        } else {
            int start = page * BUY_MATERIALS_PER_PAGE;
            int end = Math.min(start + BUY_MATERIALS_PER_PAGE, materials.size());
            for (int slot = 0; slot < end - start; slot++) {
                Material material = materials.get(start + slot);
                inventory.setItem(slot, createMenuItem(material, guiConfigManager.getString("buy-catalog.materials.button.name", "&f%material%").replace("%material%", prettifyMaterial(material)), replacePlaceholders(guiConfigManager.getStringList("buy-catalog.materials.button.lore", List.of("&7Click to create a buy order.")), Map.of("material", prettifyMaterial(material)))));
            }
        }

        inventory.setItem(45, createMenuItem(Material.ARROW, guiConfigManager.getString("buy-catalog.shared.back.name", "&eBack"), guiConfigManager.getStringList("buy-catalog.shared.back.lore", List.of("&7Return to categories."))));
        if (page > 0) {
            inventory.setItem(48, createMenuItem(Material.SPECTRAL_ARROW, guiConfigManager.getString("buy-catalog.shared.previous.name", "&ePrevious"), guiConfigManager.getStringList("buy-catalog.shared.previous.lore", List.of("&7Go to previous page."))));
        }
        if ((page + 1) * BUY_MATERIALS_PER_PAGE < materials.size()) {
            inventory.setItem(50, createMenuItem(Material.ARROW, guiConfigManager.getString("buy-catalog.shared.next.name", "&eNext"), guiConfigManager.getStringList("buy-catalog.shared.next.lore", List.of("&7Go to next page."))));
        }
        inventory.setItem(53, createMenuItem(Material.BARRIER, guiConfigManager.getString("buy-catalog.shared.close.name", "&cClose"), guiConfigManager.getStringList("buy-catalog.shared.close.lore", List.of("&7Close this menu."))));

        player.openInventory(inventory);
    }

    public void openInfo(Player player, CaravanRecord caravan) {
        CaravanInfoHolder holder = new CaravanInfoHolder(caravan.id());
        Inventory inventory = Bukkit.createInventory(holder, 27, LEGACY_SERIALIZER.deserialize(guiConfigManager.getString("info.title", "&8Caravan Info")));
        holder.setInventory(inventory);

        inventory.setItem(13, createMenuItem(
            Material.COMPASS,
            guiConfigManager.getString("info.main.name", "&6%name%").replace("%name%", caravan.name()),
            replacePlaceholders(guiConfigManager.getStringList("info.main.lore", List.of(
                "&7ID: &f%id%",
                "&7Owner: &f%player%",
                "&7Status: &f%status%",
                "&7HP: &f%hp%&7/&f%max_hp%"
            )), Map.of(
                "id", caravan.id().toString().substring(0, 8),
                "player", caravan.ownerName(),
                "status", caravan.status().name(),
                "hp", String.valueOf(caravan.hp()),
                "max_hp", String.valueOf(caravan.maxHp())
            ))
        ));
        inventory.setItem(26, createMenuItem(Material.BARRIER, guiConfigManager.getString("info.close.name", "&cClose"), guiConfigManager.getStringList("info.close.lore", List.of("&7Close this menu."))));

        player.openInventory(inventory);
    }

    public void openRouteSetup(Player player, CaravanRecord caravan, int page) {
        CaravanRouteSetupHolder holder = new CaravanRouteSetupHolder(caravan.id(), page);
        Inventory inventory = Bukkit.createInventory(holder, 54, LEGACY_SERIALIZER.deserialize(guiConfigManager.getString("route.title", "&8Route Setup")));
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
            inventory.setItem(slot, createRouteStopItem(caravan, stops.get(start + slot)));
        }

        inventory.setItem(45, createMenuItem(Material.ARROW, guiConfigManager.getString("route.buttons.back.name", "&eBack"), guiConfigManager.getStringList("route.buttons.back.lore", List.of("&7Return to caravan setup."))));
        inventory.setItem(46, createMenuItem(Material.EMERALD, guiConfigManager.getString("route.buttons.add-stop.name", "&aAdd Route Stop"), guiConfigManager.getStringList("route.buttons.add-stop.lore", List.of("&7Choose a Towny town and stop time."))));
        inventory.setItem(47, createMenuItem(
            caravan.routeLoopEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
            (caravan.routeLoopEnabled()
                ? guiConfigManager.getString("route.buttons.loop.enabled-name", "&aLoop Route: ON")
                : guiConfigManager.getString("route.buttons.loop.disabled-name", "&cLoop Route: OFF")),
            replacePlaceholders(guiConfigManager.getStringList("route.buttons.loop.lore", List.of(
                "&7Loop allowed: &f%allowed%",
                "&7Estimated route duration: &f%duration% min",
                "&eClick to toggle."
            )), Map.of(
                "allowed", String.valueOf(configManager.isRouteLoopAllowed()),
                "duration", String.valueOf(estimateRouteDurationMinutes(caravan))
            ))
        ));
        inventory.setItem(48, createMenuItem(Material.MINECART, guiConfigManager.getString("route.buttons.start.name", "&aStart Route"), guiConfigManager.getStringList("route.buttons.start.lore", List.of("&7Begin running this caravan route."))));
        inventory.setItem(49, createMenuItem(Material.REDSTONE, guiConfigManager.getString("route.buttons.stop.name", "&cStop Route"), guiConfigManager.getStringList("route.buttons.stop.lore", List.of("&7Stop the running route."))));
        inventory.setItem(50, createMenuItem(Material.LAVA_BUCKET, guiConfigManager.getString("route.buttons.clear.name", "&cClear Route"), guiConfigManager.getStringList("route.buttons.clear.lore", List.of("&7Remove all configured route stops."))));
        if (page > 0) {
            inventory.setItem(51, createMenuItem(Material.SPECTRAL_ARROW, guiConfigManager.getString("route.buttons.previous.name", "&ePrevious"), guiConfigManager.getStringList("route.buttons.previous.lore", List.of("&7Go to previous page."))));
        }
        if ((page + 1) * ROUTE_STOPS_PER_PAGE < stops.size()) {
            inventory.setItem(52, createMenuItem(Material.ARROW, guiConfigManager.getString("route.buttons.next.name", "&eNext"), guiConfigManager.getStringList("route.buttons.next.lore", List.of("&7Go to next page."))));
        }
        inventory.setItem(53, createMenuItem(Material.BARRIER, guiConfigManager.getString("route.buttons.close.name", "&cClose"), guiConfigManager.getStringList("route.buttons.close.lore", List.of("&7Close this menu."))));
    }

    public void openTownSelection(Player player, CaravanRecord caravan, int page) {
        CaravanTownSelectHolder holder = new CaravanTownSelectHolder(caravan.id(), page);
        Inventory inventory = Bukkit.createInventory(holder, 54, LEGACY_SERIALIZER.deserialize(guiConfigManager.getString("route.town-select.title", "&8Route Towns")));
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

        inventory.setItem(45, createMenuItem(Material.ARROW, guiConfigManager.getString("route.town-select.back.name", "&eBack"), guiConfigManager.getStringList("route.town-select.back.lore", List.of("&7Return to route setup."))));
        if (page > 0) {
            inventory.setItem(48, createMenuItem(Material.SPECTRAL_ARROW, guiConfigManager.getString("route.town-select.previous.name", "&ePrevious"), guiConfigManager.getStringList("route.town-select.previous.lore", List.of("&7Go to previous page."))));
        }
        if ((page + 1) * TOWNS_PER_PAGE < towns.size()) {
            inventory.setItem(50, createMenuItem(Material.ARROW, guiConfigManager.getString("route.town-select.next.name", "&eNext"), guiConfigManager.getStringList("route.town-select.next.lore", List.of("&7Go to next page."))));
        }
        inventory.setItem(53, createMenuItem(Material.BARRIER, guiConfigManager.getString("route.town-select.close.name", "&cClose"), guiConfigManager.getStringList("route.town-select.close.lore", List.of("&7Close this menu."))));
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

    private ItemStack createRouteStopItem(CaravanRecord caravan, CaravanRouteStopRecord stop) {
        boolean active = caravan.routeRunning()
            && caravan.currentRouteStopIndex() != null
            && caravan.currentRouteStopIndex() == stop.stopOrder()
            && !caravan.returningHomeAfterRoute();
        return createMenuItem(
            active ? Material.COMPASS : Material.FILLED_MAP,
            (active ? "&a" : "&6") + "Stop #" + (stop.stopOrder() + 1) + " &7- &f" + stop.townName(),
            List.of(
                "&7Town: &f" + stop.townName(),
                "&7World: &f" + stop.worldName(),
                "&7Position: &f" + String.format(Locale.US, "%.1f %.1f %.1f", stop.x(), stop.y(), stop.z()),
                "&7Duration: &f" + Duration.ofSeconds(stop.stopDurationSeconds()).toMinutes() + " min",
                "&7Active Stop: &f" + (active ? "yes" : "no"),
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

    private long estimateRouteDurationMinutes(CaravanRecord caravan) {
        return caravanRouteService.getRouteStops(caravan.id()).stream()
            .mapToLong(stop -> Duration.ofSeconds(stop.stopDurationSeconds()).toMinutes())
            .sum();
    }

    private String prettifyMaterial(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        return java.util.Arrays.stream(parts)
            .map(part -> part.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1))
            .collect(Collectors.joining(" "));
    }

    private List<String> replacePlaceholders(List<String> lines, Map<String, String> placeholders) {
        return lines.stream().map(line -> {
            String output = line;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                output = output.replace('%' + entry.getKey() + '%', entry.getValue());
            }
            return output;
        }).toList();
    }
}
