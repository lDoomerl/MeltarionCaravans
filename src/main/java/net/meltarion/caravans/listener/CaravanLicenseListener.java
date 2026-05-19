package net.meltarion.caravans.listener;

import java.util.Map;
import net.meltarion.caravans.MeltarionCaravansPlugin;
import net.meltarion.caravans.service.CaravanCreationResult;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class CaravanLicenseListener implements Listener {

    private final MeltarionCaravansPlugin plugin;

    public CaravanLicenseListener(MeltarionCaravansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (!plugin.getLicenseService().isEnabled()) {
            return;
        }
        if (!plugin.getLicenseService().isRightClickCreateAllowed()) {
            return;
        }
        if (!plugin.getLicenseService().isLicenseItem(event.getItem())) {
            return;
        }

        event.setCancelled(true);

        if (!event.getPlayer().hasPermission("meltarion.caravans.use")) {
            plugin.getMessageService().send(event.getPlayer(), "no-permission");
            return;
        }

        CaravanCreationResult result = plugin.getCaravanService().createDefaultCaravan(event.getPlayer());
        if (!result.success()) {
            switch (result.failureReason()) {
                case LICENSE_DISABLED -> plugin.getMessageService().send(event.getPlayer(), "license-disabled");
                case LIMIT_REACHED -> plugin.getMessageService().send(event.getPlayer(), "limit-reached", Map.of("limit", String.valueOf(result.currentLimit())));
                case MISSING_LICENSE -> plugin.getMessageService().send(event.getPlayer(), "missing-license");
                default -> plugin.getMessageService().send(event.getPlayer(), "right-click-failed");
            }
            return;
        }

        if (plugin.getLicenseService().shouldConsumeOnCreate()) {
            plugin.getMessageService().send(event.getPlayer(), "license-consumed");
        }
        plugin.getMessageService().send(event.getPlayer(), "right-click-created", Map.of("name", result.caravan().name()));
    }
}
