package net.meltarion.caravans.listener;

import java.util.Map;
import net.meltarion.caravans.MeltarionCaravansPlugin;
import net.meltarion.caravans.inventory.CaravanInventoryHolder;
import net.meltarion.caravans.inventory.CaravanTradeManagementHolder;
import net.meltarion.caravans.model.TradeOperationRecord;
import net.meltarion.caravans.service.TradeOperationMutationResult;
import net.meltarion.caravans.storage.StorageException;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class CaravanInventoryListener implements Listener {

    private final MeltarionCaravansPlugin plugin;

    public CaravanInventoryListener(MeltarionCaravansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof CaravanInventoryHolder holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof CommandSender sender)) {
            return;
        }

        try {
            plugin.getInventoryService().handleInventoryClose(event.getInventory());
            plugin.getMessageService().send(sender, "inventory-saved", Map.of(
                "id", holder.getCaravanId().toString().substring(0, 8),
                "name", holder.getCaravanName()
            ));
        } catch (StorageException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to save caravan inventory " + holder.getCaravanId() + '.', exception);
            plugin.getMessageService().send(sender, "inventory-save-failed", Map.of(
                "id", holder.getCaravanId().toString().substring(0, 8),
                "name", holder.getCaravanName()
            ));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof CaravanTradeManagementHolder holder) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof CommandSender sender)) {
                return;
            }
            if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
                return;
            }

            TradeOperationRecord tradeOperation = plugin.getTradeOperationService().getTradeOperation(holder.getCaravanId(), event.getRawSlot());
            if (tradeOperation == null) {
                return;
            }

            TradeOperationMutationResult result = event.isShiftClick()
                ? plugin.getTradeOperationService().deleteOperation(tradeOperation.id())
                : plugin.getTradeOperationService().toggleActive(tradeOperation.id());

            if (!result.success()) {
                plugin.getMessageService().send(sender, "storage-error");
                return;
            }

            plugin.getTradeOperationService().populateTradeManagementInventory(event.getView().getTopInventory(), holder.getCaravanId());
            if (event.isShiftClick()) {
                plugin.getMessageService().send(sender, "trade-deleted", Map.of("id", result.tradeOperation().id().toString().substring(0, 8)));
            } else {
                plugin.getMessageService().send(sender, "trade-toggled", Map.of(
                    "id", result.tradeOperation().id().toString().substring(0, 8),
                    "status", result.tradeOperation().active() ? "active" : "inactive"
                ));
            }
            return;
        }

        if (!(event.getView().getTopInventory().getHolder(false) instanceof CaravanInventoryHolder holder)) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        if (!plugin.getTradeOperationService().isReservedSlot(holder.getCaravanId(), event.getRawSlot())) {
            return;
        }

        event.setCancelled(true);
        if (event.getWhoClicked() instanceof CommandSender sender) {
            plugin.getMessageService().send(sender, "trade-slot-reserved", Map.of(
                "id", holder.getCaravanId().toString().substring(0, 8),
                "name", holder.getCaravanName()
            ));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof CaravanInventoryHolder holder)) {
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < event.getView().getTopInventory().getSize()
                && plugin.getTradeOperationService().isReservedSlot(holder.getCaravanId(), rawSlot)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof CommandSender sender) {
                    plugin.getMessageService().send(sender, "trade-slot-reserved", Map.of(
                        "id", holder.getCaravanId().toString().substring(0, 8),
                        "name", holder.getCaravanName()
                    ));
                }
                return;
            }
        }
    }
}
