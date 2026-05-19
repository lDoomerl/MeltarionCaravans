package net.meltarion.caravans.listener;

import java.util.Map;
import java.util.UUID;
import net.meltarion.caravans.MeltarionCaravansPlugin;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.model.CaravanStatus;
import net.meltarion.caravans.service.CaravanMutationResult;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public final class CaravanPhysicalEntityDamageListener implements Listener {

    private final MeltarionCaravansPlugin plugin;

    public CaravanPhysicalEntityDamageListener(MeltarionCaravansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        UUID caravanId = plugin.getCaravanEntityService().findCaravanId(event.getEntity());
        if (caravanId == null) {
            return;
        }
        if (!plugin.getConfigManager().isPhysicalDamageEnabled()) {
            return;
        }
        if (!plugin.getConfigManager().shouldSyncPhysicalDamageToCaravanHp()) {
            return;
        }

        CaravanRecord caravan = plugin.getCaravanService().getCaravan(caravanId);
        if (caravan == null) {
            return;
        }

        int damage = Math.max(1, (int) Math.ceil(event.getFinalDamage()));
        int updatedHp = Math.max(0, caravan.hp() - damage);
        CaravanMutationResult mutationResult = plugin.getCaravanService().updateCaravanHealthAndStatus(
            caravan,
            updatedHp,
            CaravanStatus.ATTACKED
        );
        if (!mutationResult.success()) {
            plugin.getLogger().warning("Failed to persist caravan damage state for " + caravan.id() + '.');
            return;
        }

        CaravanRecord updatedCaravan = mutationResult.caravan();
        notifyDamager(event, updatedCaravan, damage);
        notifyOwner(updatedCaravan);

        if (updatedHp <= 0) {
            plugin.getCaravanEntityService().despawnCaravan(updatedCaravan.id());
            Player owner = Bukkit.getPlayer(updatedCaravan.ownerId());
            if (owner != null && owner.isOnline()) {
                plugin.getMessageService().send(owner, "physical-destroyed", placeholders(updatedCaravan, damage));
            }
        }
    }

    private void notifyDamager(EntityDamageEvent event, CaravanRecord caravan, int damage) {
        if (event instanceof EntityDamageByEntityEvent damageByEntityEvent
            && damageByEntityEvent.getDamager() instanceof CommandSender sender) {
            plugin.getMessageService().send(sender, "physical-damaged", placeholders(caravan, damage));
        }
    }

    private void notifyOwner(CaravanRecord caravan) {
        Player owner = Bukkit.getPlayer(caravan.ownerId());
        if (owner != null && owner.isOnline()) {
            plugin.getMessageService().send(owner, "physical-owner-notified-attacked", placeholders(caravan, 0));
        }
    }

    private Map<String, String> placeholders(CaravanRecord caravan, int damage) {
        return Map.of(
            "id", plugin.getCaravanService().getShortId(caravan),
            "name", caravan.name(),
            "player", caravan.ownerName(),
            "hp", String.valueOf(caravan.hp()),
            "max_hp", String.valueOf(caravan.maxHp()),
            "damage", String.valueOf(damage)
        );
    }
}
