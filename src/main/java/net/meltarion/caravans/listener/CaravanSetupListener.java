package net.meltarion.caravans.listener;

import java.util.List;
import java.util.Map;
import net.meltarion.caravans.MeltarionCaravansPlugin;
import net.meltarion.caravans.inventory.CaravanBuyCategoryHolder;
import net.meltarion.caravans.inventory.CaravanBuyMaterialHolder;
import net.meltarion.caravans.inventory.CaravanInfoHolder;
import net.meltarion.caravans.inventory.CaravanSellSetupHolder;
import net.meltarion.caravans.inventory.CaravanSetupHolder;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.service.TradeCatalogCategory;
import net.meltarion.caravans.storage.StorageException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class CaravanSetupListener implements Listener {

    private static final int STORAGE_SLOT = 10;
    private static final int SELL_SLOT = 11;
    private static final int BUY_SLOT = 12;
    private static final int TRADES_SLOT = 13;
    private static final int INFO_SLOT = 14;
    private static final int CLOSE_SLOT = 16;
    private static final int BUY_BACK_SLOT = 45;
    private static final int BUY_PREVIOUS_SLOT = 48;
    private static final int BUY_NEXT_SLOT = 50;
    private static final int BUY_CLOSE_SLOT = 53;

    private final MeltarionCaravansPlugin plugin;

    public CaravanSetupListener(MeltarionCaravansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof CaravanSetupHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            CaravanRecord caravan = plugin.getCaravanService().getCaravan(holder.getCaravanId());
            if (caravan == null) {
                player.closeInventory();
                plugin.getMessageService().send(player, "caravan-not-found");
                return;
            }

            switch (event.getRawSlot()) {
                case STORAGE_SLOT -> openStorage(player, caravan);
                case SELL_SLOT -> openSellSetup(player, caravan);
                case BUY_SLOT -> openBuyCategories(player, caravan);
                case TRADES_SLOT -> {
                    plugin.getTradeOperationService().openTradeManagementInventory(player, caravan);
                    plugin.getMessageService().send(player, "trade-management-opened", Map.of(
                        "id", plugin.getCaravanService().getShortId(caravan),
                        "name", caravan.name()
                    ));
                }
                case INFO_SLOT -> plugin.getCaravanSetupGuiService().openInfo(player, caravan);
                case CLOSE_SLOT -> player.closeInventory();
                default -> {
                }
            }
            return;
        }

        if (event.getView().getTopInventory().getHolder(false) instanceof CaravanSellSetupHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
                return;
            }

            CaravanRecord caravan = plugin.getCaravanService().getCaravan(holder.getCaravanId());
            if (caravan == null) {
                player.closeInventory();
                plugin.getMessageService().send(player, "caravan-not-found");
                return;
            }

            if (event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) {
                plugin.getMessageService().send(player, "setup-no-item-selected");
                return;
            }
            if (plugin.getTradeOperationService().isReservedSlot(holder.getCaravanId(), event.getRawSlot())) {
                plugin.getMessageService().send(player, "setup-slot-already-reserved");
                return;
            }

            player.closeInventory();
            plugin.getTradeSetupSessionService().beginSellSession(player, caravan, event.getRawSlot());
            return;
        }

        if (event.getView().getTopInventory().getHolder(false) instanceof CaravanBuyCategoryHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            CaravanRecord caravan = plugin.getCaravanService().getCaravan(holder.getCaravanId());
            if (caravan == null) {
                player.closeInventory();
                plugin.getMessageService().send(player, "caravan-not-found");
                return;
            }

            if (event.getRawSlot() == 26) {
                player.closeInventory();
                return;
            }

            List<TradeCatalogCategory> categories = plugin.getCaravanSetupGuiService().getAvailableCategories();
            int[] categorySlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22};
            for (int index = 0; index < Math.min(categorySlots.length, categories.size()); index++) {
                if (event.getRawSlot() == categorySlots[index]) {
                    plugin.getCaravanSetupGuiService().openBuyMaterialCatalog(player, caravan, categories.get(index), 0);
                    return;
                }
            }
            return;
        }

        if (event.getView().getTopInventory().getHolder(false) instanceof CaravanBuyMaterialHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            CaravanRecord caravan = plugin.getCaravanService().getCaravan(holder.getCaravanId());
            if (caravan == null) {
                player.closeInventory();
                plugin.getMessageService().send(player, "caravan-not-found");
                return;
            }

            if (event.getRawSlot() == BUY_BACK_SLOT) {
                plugin.getCaravanSetupGuiService().openBuyCategoryCatalog(player, caravan);
                return;
            }
            if (event.getRawSlot() == BUY_PREVIOUS_SLOT && holder.getPage() > 0) {
                plugin.getCaravanSetupGuiService().openBuyMaterialCatalog(player, caravan, holder.getCategory(), holder.getPage() - 1);
                return;
            }
            if (event.getRawSlot() == BUY_NEXT_SLOT) {
                plugin.getCaravanSetupGuiService().openBuyMaterialCatalog(player, caravan, holder.getCategory(), holder.getPage() + 1);
                return;
            }
            if (event.getRawSlot() == BUY_CLOSE_SLOT) {
                player.closeInventory();
                return;
            }
            if (event.getRawSlot() < 0 || event.getRawSlot() >= 45) {
                return;
            }

            List<org.bukkit.Material> materials = plugin.getCaravanSetupGuiService().getMaterialsForCategory(holder.getCategory());
            int index = holder.getPage() * 45 + event.getRawSlot();
            if (index >= materials.size()) {
                plugin.getMessageService().send(player, "setup-category-empty");
                return;
            }

            org.bukkit.Material material = materials.get(index);
            if (plugin.getConfigManager().getTradeBuyCatalogBlacklist().contains(material)) {
                plugin.getMessageService().send(player, "setup-material-blacklisted", Map.of("material", material.name()));
                return;
            }

            player.closeInventory();
            plugin.getTradeSetupSessionService().beginBuySession(player, caravan, material);
            return;
        }

        if (event.getView().getTopInventory().getHolder(false) instanceof CaravanInfoHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof CaravanSetupHolder
            || event.getView().getTopInventory().getHolder(false) instanceof CaravanSellSetupHolder
            || event.getView().getTopInventory().getHolder(false) instanceof CaravanBuyCategoryHolder
            || event.getView().getTopInventory().getHolder(false) instanceof CaravanBuyMaterialHolder
            || event.getView().getTopInventory().getHolder(false) instanceof CaravanInfoHolder) {
            event.setCancelled(true);
        }
    }

    private void openStorage(Player player, CaravanRecord caravan) {
        try {
            plugin.getInventoryService().openInventoryForAdmin(player, caravan);
            plugin.getMessageService().send(player, "inventory-opened", Map.of(
                "id", plugin.getCaravanService().getShortId(caravan),
                "name", caravan.name()
            ));
        } catch (StorageException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to open caravan storage " + caravan.id() + '.', exception);
            plugin.getMessageService().send(player, "storage-error");
        }
    }

    private void openSellSetup(Player player, CaravanRecord caravan) {
        try {
            plugin.getCaravanSetupGuiService().openSellSetup(player, caravan);
        } catch (StorageException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to open sell setup for caravan " + caravan.id() + '.', exception);
            plugin.getMessageService().send(player, "storage-error");
        }
    }

    private void openBuyCategories(Player player, CaravanRecord caravan) {
        List<TradeCatalogCategory> categories = plugin.getCaravanSetupGuiService().getAvailableCategories();
        if (categories.isEmpty()) {
            plugin.getMessageService().send(player, "setup-catalog-empty");
            return;
        }
        plugin.getCaravanSetupGuiService().openBuyCategoryCatalog(player, caravan);
    }
}
