package net.meltarion.caravans.listener;

import java.util.Map;
import net.meltarion.caravans.MeltarionCaravansPlugin;
import net.meltarion.caravans.inventory.CaravanInventoryHolder;
import net.meltarion.caravans.storage.StorageException;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

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
}
