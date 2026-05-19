package net.meltarion.caravans.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private final ConcurrentHashMap<UUID, Long> ownerNotificationCooldowns = new ConcurrentHashMap<>();

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

        CaravanRecord caravan = plugin.getCaravanService().getCaravan(caravanId);
        if (caravan == null) {
            return;
        }

        int damage = Math.max(1, (int) Math.ceil(event.getFinalDamage()));
        CaravanRecord updatedCaravan = caravan;
        if (plugin.getConfigManager().shouldSyncPhysicalDamageToCaravanHp()) {
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
            updatedCaravan = mutationResult.caravan();
        } else if (plugin.getConfigManager().shouldPauseMovementWhenAttacked()) {
            CaravanMutationResult mutationResult = plugin.getCaravanService().updateCaravanHealthAndStatus(
                caravan,
                caravan.hp(),
                CaravanStatus.ATTACKED
            );
            if (!mutationResult.success()) {
                plugin.getLogger().warning("Failed to persist caravan attacked status for " + caravan.id() + '.');
                return;
            }
            updatedCaravan = mutationResult.caravan();
        }

        if (plugin.getConfigManager().shouldPauseMovementWhenAttacked()) {
            updatedCaravan = plugin.getCaravanMovementService().handleAttacked(updatedCaravan);
        }

        notifyDamager(event, updatedCaravan, damage);
        notifyOwner(updatedCaravan);

        if (updatedCaravan.hp() <= 0) {
            plugin.getCaravanEntityService().despawnCaravan(updatedCaravan.id());
            updatedCaravan = plugin.getCaravanMovementService().markPhysicalProjection(updatedCaravan, false);
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
        long now = System.currentTimeMillis();
        long cooldownMillis = plugin.getConfigManager().getMovementAttackedNotificationCooldownSeconds() * 1000L;
        Long lastSent = ownerNotificationCooldowns.get(caravan.id());
        if (lastSent != null && now - lastSent < cooldownMillis) {
            return;
        }

        Player owner = Bukkit.getPlayer(caravan.ownerId());
        if (owner != null && owner.isOnline()) {
            ownerNotificationCooldowns.put(caravan.id(), now);
            plugin.getMessageService().send(owner, "physical-owner-notified-attacked", placeholders(caravan, 0));
            if (plugin.getConfigManager().shouldPauseMovementWhenAttacked()) {
                plugin.getMessageService().send(owner, "movement-paused-attacked", placeholders(caravan, 0));
            }
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
