package net.meltarion.caravans.listener;

import java.util.Map;
import java.util.UUID;
import net.meltarion.caravans.MeltarionCaravansPlugin;
import net.meltarion.caravans.model.CaravanRecord;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public final class CaravanPhysicalEntityInteractListener implements Listener {

    private final MeltarionCaravansPlugin plugin;

    public CaravanPhysicalEntityInteractListener(MeltarionCaravansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        UUID caravanId = plugin.getCaravanEntityService().findCaravanId(event.getRightClicked());
        if (caravanId == null) {
            return;
        }

        event.setCancelled(true);

        CaravanRecord caravan = plugin.getCaravanService().getCaravan(caravanId);
        if (caravan == null) {
            return;
        }

        Player player = event.getPlayer();
        boolean owner = caravan.ownerId().equals(player.getUniqueId());
        boolean admin = player.hasPermission("meltarion.caravans.admin");
        if ((owner || admin) && plugin.getConfigManager().isPhysicalOwnerOpensSetupEnabled()) {
            plugin.getCaravanSetupGuiService().openMainSetup(player, caravan);
            plugin.getMessageService().send(player, "setup-opened", Map.of(
                "id", plugin.getCaravanService().getShortId(caravan),
                "name", caravan.name()
            ));
            return;
        }

        if (!owner && !admin) {
            if (caravan.status() == net.meltarion.caravans.model.CaravanStatus.ATTACKED) {
                if (plugin.getConfigManager().isPhysicalStrangerMessageEnabled()) {
                    plugin.getMessageService().send(player, "physical-trading-unavailable");
                }
                return;
            }
            if (plugin.getTownyIntegrationService().isShopPlot(event.getRightClicked().getLocation())) {
                plugin.getPublicTradeGuiService().openMainMenu(player, caravan);
                plugin.getMessageService().send(player, "public-trade-opened", Map.of(
                    "id", plugin.getCaravanService().getShortId(caravan),
                    "name", caravan.name()
                ));
                return;
            }

            if (plugin.getConfigManager().isPhysicalStrangerMessageEnabled()) {
                plugin.getMessageService().send(player, "public-trade-shop-plot-required");
            }
        }
    }
}
