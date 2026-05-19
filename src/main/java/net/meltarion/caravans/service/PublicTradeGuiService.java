package net.meltarion.caravans.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.config.GuiConfigManager;
import net.meltarion.caravans.inventory.CaravanPublicBuyHolder;
import net.meltarion.caravans.inventory.CaravanPublicSellHolder;
import net.meltarion.caravans.inventory.CaravanPublicTradeHolder;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.model.TradeOperationRecord;
import net.meltarion.caravans.model.TradeOperationType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class PublicTradeGuiService {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private final ConfigManager configManager;
    private final GuiConfigManager guiConfigManager;
    private final CaravanInventoryService inventoryService;
    private final TradeOperationService tradeOperationService;

    public PublicTradeGuiService(
        ConfigManager configManager,
        GuiConfigManager guiConfigManager,
        CaravanInventoryService inventoryService,
        TradeOperationService tradeOperationService
    ) {
        this.configManager = configManager;
        this.guiConfigManager = guiConfigManager;
        this.inventoryService = inventoryService;
        this.tradeOperationService = tradeOperationService;
    }

    public void openMainMenu(Player player, CaravanRecord caravan) {
        CaravanPublicTradeHolder holder = new CaravanPublicTradeHolder(caravan.id(), caravan.name());
        Inventory inventory = Bukkit.createInventory(holder, 27, LEGACY_SERIALIZER.deserialize(guiConfigManager.getString("public-trade.main.title", "&8Caravan Trade")));
        holder.setInventory(inventory);

        inventory.setItem(10, createMenuItem(
            guiConfigManager.getMaterial("public-trade.main.buttons.buy-from-caravan.material", Material.EMERALD),
            guiConfigManager.getString("public-trade.main.buttons.buy-from-caravan.name", "&aBuy From Caravan"),
            guiConfigManager.getStringList("public-trade.main.buttons.buy-from-caravan.lore", List.of("&7Browse active SELL offers."))
        ));
        inventory.setItem(12, createMenuItem(
            guiConfigManager.getMaterial("public-trade.main.buttons.sell-to-caravan.material", Material.CHEST),
            guiConfigManager.getString("public-trade.main.buttons.sell-to-caravan.name", "&6Sell To Caravan"),
            guiConfigManager.getStringList("public-trade.main.buttons.sell-to-caravan.lore", List.of("&7Browse active BUY orders."))
        ));
        inventory.setItem(14, createMenuItem(
            guiConfigManager.getMaterial("public-trade.main.buttons.info.material", Material.COMPASS),
            guiConfigManager.getString("public-trade.main.buttons.info.name", "&dInfo"),
            guiConfigManager.getStringList("public-trade.main.buttons.info.lore", List.of("&7View caravan details."))
        ));
        inventory.setItem(16, createMenuItem(
            guiConfigManager.getMaterial("public-trade.main.buttons.close.material", Material.BARRIER),
            guiConfigManager.getString("public-trade.main.buttons.close.name", "&cClose"),
            guiConfigManager.getStringList("public-trade.main.buttons.close.lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inventory);
    }

    public void openSellOffers(Player player, CaravanRecord caravan) {
        CaravanPublicSellHolder holder = new CaravanPublicSellHolder(caravan.id(), caravan.name());
        Inventory inventory = Bukkit.createInventory(holder, 54, LEGACY_SERIALIZER.deserialize(guiConfigManager.getString("public-trade.sell-offers.title", "&8Buy From Caravan")));
        holder.setInventory(inventory);
        populateSellOffers(inventory, caravan);
        player.openInventory(inventory);
    }

    public void openBuyOrders(Player player, CaravanRecord caravan) {
        CaravanPublicBuyHolder holder = new CaravanPublicBuyHolder(caravan.id(), caravan.name());
        Inventory inventory = Bukkit.createInventory(holder, 54, LEGACY_SERIALIZER.deserialize(guiConfigManager.getString("public-trade.buy-orders.title", "&8Sell To Caravan")));
        holder.setInventory(inventory);
        populateBuyOrders(inventory, caravan);
        player.openInventory(inventory);
    }

    public void populateSellOffers(Inventory inventory, CaravanRecord caravan) {
        inventory.clear();
        List<TradeOperationRecord> operations = tradeOperationService.getActiveOperations(caravan.id(), TradeOperationType.SELL);
        for (int slot = 0; slot < Math.min(45, operations.size()); slot++) {
            inventory.setItem(slot, createSellOfferItem(caravan, operations.get(slot)));
        }
        inventory.setItem(45, createMenuItem(Material.ARROW, guiConfigManager.getString("public-trade.shared.back.name", "&eBack"), guiConfigManager.getStringList("public-trade.shared.back.lore", List.of("&7Return to public trade menu."))));
        inventory.setItem(53, createMenuItem(Material.BARRIER, guiConfigManager.getString("public-trade.shared.close.name", "&cClose"), guiConfigManager.getStringList("public-trade.shared.close.lore", List.of("&7Close this menu."))));
    }

    public void populateBuyOrders(Inventory inventory, CaravanRecord caravan) {
        inventory.clear();
        List<TradeOperationRecord> operations = tradeOperationService.getActiveOperations(caravan.id(), TradeOperationType.BUY);
        for (int slot = 0; slot < Math.min(45, operations.size()); slot++) {
            inventory.setItem(slot, createBuyOrderItem(operations.get(slot)));
        }
        inventory.setItem(45, createMenuItem(Material.ARROW, guiConfigManager.getString("public-trade.shared.back.name", "&eBack"), guiConfigManager.getStringList("public-trade.shared.back.lore", List.of("&7Return to public trade menu."))));
        inventory.setItem(53, createMenuItem(Material.BARRIER, guiConfigManager.getString("public-trade.shared.close.name", "&cClose"), guiConfigManager.getStringList("public-trade.shared.close.lore", List.of("&7Close this menu."))));
    }

    public TradeOperationRecord getDisplayedSellOperation(CaravanRecord caravan, int slot) {
        List<TradeOperationRecord> operations = tradeOperationService.getActiveOperations(caravan.id(), TradeOperationType.SELL);
        return slot >= 0 && slot < operations.size() && slot < 45 ? operations.get(slot) : null;
    }

    public TradeOperationRecord getDisplayedBuyOperation(CaravanRecord caravan, int slot) {
        List<TradeOperationRecord> operations = tradeOperationService.getActiveOperations(caravan.id(), TradeOperationType.BUY);
        return slot >= 0 && slot < operations.size() && slot < 45 ? operations.get(slot) : null;
    }

    private ItemStack createSellOfferItem(CaravanRecord caravan, TradeOperationRecord operation) {
        ItemStack displayItem = operation.itemStack().clone();
        ItemMeta meta = displayItem.getItemMeta();
        if (meta == null) {
            return displayItem;
        }

        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY_SERIALIZER.deserialize("&7Type: &fSELL"));
        lore.add(LEGACY_SERIALIZER.deserialize("&7Amount: &f" + operation.amountPerTransaction()));
        lore.add(LEGACY_SERIALIZER.deserialize("&7Price: &f" + operation.priceCurrencyAmount() + " " + configManager.getCurrencyItem().name()));
        lore.add(LEGACY_SERIALIZER.deserialize("&7Remaining stock: &f" + resolveSellRemaining(caravan, operation)));
        lore.add(LEGACY_SERIALIZER.deserialize("&7Active: &f" + (operation.active() ? "yes" : "no")));
        lore.add(LEGACY_SERIALIZER.deserialize("&eClick to purchase."));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        displayItem.setItemMeta(meta);
        return displayItem;
    }

    private ItemStack createBuyOrderItem(TradeOperationRecord operation) {
        ItemStack displayItem = operation.itemStack().clone();
        ItemMeta meta = displayItem.getItemMeta();
        if (meta == null) {
            return displayItem;
        }

        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY_SERIALIZER.deserialize("&7Type: &fBUY"));
        lore.add(LEGACY_SERIALIZER.deserialize("&7Amount per sale: &f" + operation.amountPerTransaction()));
        lore.add(LEGACY_SERIALIZER.deserialize("&7Price: &f" + operation.priceCurrencyAmount() + " " + configManager.getCurrencyItem().name()));
        lore.add(LEGACY_SERIALIZER.deserialize("&7Remaining wanted: &f" + Math.max(0, (operation.maxTotalAmount() == null ? 0 : operation.maxTotalAmount() - operation.fulfilledAmount()))));
        lore.add(LEGACY_SERIALIZER.deserialize("&7Active: &f" + (operation.active() ? "yes" : "no")));
        lore.add(LEGACY_SERIALIZER.deserialize("&eClick to sell items."));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());
        displayItem.setItemMeta(meta);
        return displayItem;
    }

    private int resolveSellRemaining(CaravanRecord caravan, TradeOperationRecord operation) {
        try {
            Inventory inventory = inventoryService.getInventory(caravan);
            Integer slot = operation.reservedInventorySlot();
            if (slot == null || slot < 0 || slot >= inventory.getSize()) {
                return 0;
            }
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir() || !current.isSimilar(operation.itemStack())) {
                return 0;
            }
            return current.getAmount();
        } catch (Exception exception) {
            return 0;
        }
    }

    private ItemStack createMenuItem(Material material, String name, List<String> loreLines) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return itemStack;
        }

        itemMeta.displayName(LEGACY_SERIALIZER.deserialize(name));
        itemMeta.lore(loreLines.stream().map(LEGACY_SERIALIZER::deserialize).collect(Collectors.toList()));
        itemMeta.addItemFlags(ItemFlag.values());
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }
}
