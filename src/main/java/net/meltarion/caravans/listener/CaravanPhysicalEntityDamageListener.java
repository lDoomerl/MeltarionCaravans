package net.meltarion.caravans.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.meltarion.caravans.MeltarionCaravansPlugin;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.model.CaravanStatus;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
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

        CaravanRecord caravan = plugin.getCaravanMovementService().getRuntimeCaravan(caravanId);
        if (caravan == null) {
            return;
        }

        int damage = Math.max(1, (int) Math.ceil(event.getFinalDamage()));
        CaravanRecord updatedCaravan = caravan;
        if (plugin.getConfigManager().shouldSyncPhysicalDamageToCaravanHp()) {
            updatedCaravan = plugin.getCaravanHealthService().applyDamage(caravan, event.getFinalDamage(), CaravanStatus.ATTACKED);
        } else if (plugin.getConfigManager().shouldPauseMovementWhenAttacked()) {
            updatedCaravan = plugin.getCaravanHealthService().markStatus(caravan, CaravanStatus.ATTACKED);
        }
        plugin.getCaravanMovementService().syncRuntimeCaravan(updatedCaravan);

        preventVisualDeath(event, updatedCaravan);

        if (plugin.getConfigManager().shouldPauseMovementWhenAttacked()) {
            updatedCaravan = plugin.getCaravanMovementService().handleAttacked(updatedCaravan);
            plugin.getCaravanRouteService().handleAttacked(updatedCaravan);
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

    private void preventVisualDeath(EntityDamageEvent event, CaravanRecord caravan) {
        if (caravan.hp() <= 0 || !(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }

        if (livingEntity.getHealth() - event.getFinalDamage() <= 0.0D) {
            event.setDamage(Math.max(0.0D, livingEntity.getHealth() - 1.0D));
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
        } else {
            ownerNotificationCooldowns.put(caravan.id(), now);
            plugin.getNotificationService().sendPlayerMessage(caravan.ownerId(), "physical-owner-notified-attacked", placeholders(caravan, 0));
            if (plugin.getConfigManager().shouldPauseMovementWhenAttacked()) {
                plugin.getNotificationService().sendPlayerMessage(caravan.ownerId(), "movement-paused-attacked", placeholders(caravan, 0));
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
