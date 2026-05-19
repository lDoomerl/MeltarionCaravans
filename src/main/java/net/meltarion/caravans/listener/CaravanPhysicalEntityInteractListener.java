package net.meltarion.caravans.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.meltarion.caravans.MeltarionCaravansPlugin;
import net.meltarion.caravans.model.CaravanRecord;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class CaravanPhysicalEntityInteractListener implements Listener {

    private final MeltarionCaravansPlugin plugin;
    private final ConcurrentHashMap<UUID, Long> interactionCooldowns = new ConcurrentHashMap<>();

    public CaravanPhysicalEntityInteractListener(MeltarionCaravansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        UUID caravanId = plugin.getCaravanEntityService().findCaravanId(event.getRightClicked());
        if (caravanId == null) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (isOnCooldown(player.getUniqueId())) {
            return;
        }
        interactionCooldowns.put(player.getUniqueId(), System.currentTimeMillis());

        CaravanRecord caravan = plugin.getCaravanService().getCaravan(caravanId);
        if (caravan == null) {
            return;
        }

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

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        interactionCooldowns.remove(event.getPlayer().getUniqueId());
    }

    private boolean isOnCooldown(UUID playerId) {
        Long lastInteraction = interactionCooldowns.get(playerId);
        if (lastInteraction == null) {
            return false;
        }

        long cooldownMillis = plugin.getConfigManager().getInteractionCooldownMillis();
        long elapsed = System.currentTimeMillis() - lastInteraction;
        if (elapsed >= cooldownMillis) {
            interactionCooldowns.remove(playerId, lastInteraction);
            return false;
        }
        return true;
    }
}
