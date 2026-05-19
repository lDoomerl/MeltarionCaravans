package net.meltarion.caravans.listener;

import java.util.Map;
import net.meltarion.caravans.MeltarionCaravansPlugin;
import net.meltarion.caravans.inventory.CaravanPublicBuyHolder;
import net.meltarion.caravans.inventory.CaravanPublicSellHolder;
import net.meltarion.caravans.inventory.CaravanPublicTradeHolder;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.model.TradeOperationRecord;
import net.meltarion.caravans.service.PublicTradeResult;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class CaravanPublicTradeListener implements Listener {

    private static final int BUY_FROM_CARAVAN_SLOT = 10;
    private static final int SELL_TO_CARAVAN_SLOT = 12;
    private static final int INFO_SLOT = 14;
    private static final int CLOSE_SLOT = 16;
    private static final int BACK_SLOT = 45;
    private static final int MENU_CLOSE_SLOT = 53;

    private final MeltarionCaravansPlugin plugin;

    public CaravanPublicTradeListener(MeltarionCaravansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof CaravanPublicTradeHolder holder) {
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
                case BUY_FROM_CARAVAN_SLOT -> {
                    if (plugin.getTradeOperationService().getActiveOperations(caravan.id(), net.meltarion.caravans.model.TradeOperationType.SELL).isEmpty()) {
                        plugin.getMessageService().send(player, "public-trade-no-sell-offers");
                        return;
                    }
                    plugin.getPublicTradeGuiService().openSellOffers(player, caravan);
                }
                case SELL_TO_CARAVAN_SLOT -> {
                    if (plugin.getTradeOperationService().getActiveOperations(caravan.id(), net.meltarion.caravans.model.TradeOperationType.BUY).isEmpty()) {
                        plugin.getMessageService().send(player, "public-trade-no-buy-orders");
                        return;
                    }
                    plugin.getPublicTradeGuiService().openBuyOrders(player, caravan);
                }
                case INFO_SLOT -> plugin.getCaravanSetupGuiService().openInfo(player, caravan);
                case CLOSE_SLOT -> player.closeInventory();
                default -> {
                }
            }
            return;
        }

        if (event.getView().getTopInventory().getHolder(false) instanceof CaravanPublicSellHolder holder) {
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

            if (event.getRawSlot() == BACK_SLOT) {
                plugin.getPublicTradeGuiService().openMainMenu(player, caravan);
                return;
            }
            if (event.getRawSlot() == MENU_CLOSE_SLOT) {
                player.closeInventory();
                return;
            }
            if (event.getRawSlot() < 0 || event.getRawSlot() >= 45) {
                return;
            }

            TradeOperationRecord operation = plugin.getPublicTradeGuiService().getDisplayedSellOperation(caravan, event.getRawSlot());
            if (operation == null) {
                plugin.getMessageService().send(player, "public-trade-operation-inactive");
                plugin.getPublicTradeGuiService().populateSellOffers(event.getView().getTopInventory(), caravan);
                return;
            }

            PublicTradeResult result = plugin.getPublicTradeService().attemptBuyFromCaravan(player, caravan, operation);
            plugin.getMessageService().send(player, result.messageKey(), result.placeholders());
            plugin.getPublicTradeGuiService().populateSellOffers(event.getView().getTopInventory(), caravan);
            return;
        }

        if (event.getView().getTopInventory().getHolder(false) instanceof CaravanPublicBuyHolder holder) {
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

            if (event.getRawSlot() == BACK_SLOT) {
                plugin.getPublicTradeGuiService().openMainMenu(player, caravan);
                return;
            }
            if (event.getRawSlot() == MENU_CLOSE_SLOT) {
                player.closeInventory();
                return;
            }
            if (event.getRawSlot() < 0 || event.getRawSlot() >= 45) {
                return;
            }

            TradeOperationRecord operation = plugin.getPublicTradeGuiService().getDisplayedBuyOperation(caravan, event.getRawSlot());
            if (operation == null) {
                plugin.getMessageService().send(player, "public-trade-operation-inactive");
                plugin.getPublicTradeGuiService().populateBuyOrders(event.getView().getTopInventory(), caravan);
                return;
            }

            PublicTradeResult result = plugin.getPublicTradeService().attemptSellToCaravan(player, caravan, operation);
            plugin.getMessageService().send(player, result.messageKey(), result.placeholders());
            plugin.getPublicTradeGuiService().populateBuyOrders(event.getView().getTopInventory(), caravan);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof CaravanPublicTradeHolder
            || event.getView().getTopInventory().getHolder(false) instanceof CaravanPublicSellHolder
            || event.getView().getTopInventory().getHolder(false) instanceof CaravanPublicBuyHolder) {
            event.setCancelled(true);
        }
    }
}
