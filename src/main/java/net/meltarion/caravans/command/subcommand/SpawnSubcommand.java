package net.meltarion.caravans.command.subcommand;

import java.util.Map;
import net.meltarion.caravans.command.CaravanSubcommand;
import net.meltarion.caravans.command.CommandContext;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.service.CaravanLookupResult;
import net.meltarion.caravans.service.PhysicalSpawnFailureReason;
import net.meltarion.caravans.service.PhysicalSpawnResult;
import net.meltarion.caravans.service.TownyRequirementResult;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class SpawnSubcommand implements CaravanSubcommand {

    @Override
    public String getName() {
        return "spawn";
    }

    @Override
    public String getPermission() {
        return "meltarion.caravans.use";
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public void execute(CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            context.messages().send(context.sender(), "player-only");
            return;
        }
        if (context.args().length < 2) {
            context.messages().send(context.sender(), "spawn-usage");
            return;
        }

        CaravanLookupResult lookupResult = context.caravans().findCaravan(context.args()[1]);
        if (!lookupResult.success()) {
            if (lookupResult.failureReason() == CaravanLookupResult.FailureReason.AMBIGUOUS) {
                context.messages().send(player, "ambiguous-id");
            } else {
                context.messages().send(player, "caravan-not-found");
            }
            return;
        }

        CaravanRecord caravan = lookupResult.caravan();
        boolean admin = player.hasPermission("meltarion.caravans.admin");
        if (!admin && !caravan.ownerId().equals(player.getUniqueId())) {
            context.messages().send(player, "physical-not-owner");
            return;
        }

        if (!admin) {
            TownyRequirementResult townyResult = context.towny().checkOwnTownTerritory(player);
            switch (townyResult) {
                case OK -> {
                }
                case TOWNY_NOT_FOUND -> {
                    context.messages().send(player, "physical-towny-not-found");
                    return;
                }
                case NOT_IN_TOWN -> {
                    context.messages().send(player, "physical-not-in-town");
                    return;
                }
                case NOT_YOUR_TOWN -> {
                    context.messages().send(player, "physical-not-your-town");
                    return;
                }
                case ERROR -> {
                    context.messages().send(player, "physical-towny-not-found");
                    return;
                }
            }
        }

        PhysicalSpawnResult result = context.entities().spawnCaravan(caravan, getSpawnLocation(context, player));
        if (!result.success()) {
            switch (result.failureReason()) {
                case DISABLED -> context.messages().send(player, "physical-spawn-disabled");
                case ALREADY_SPAWNED -> context.messages().send(player, "physical-already-spawned", placeholders(context, caravan));
                case ERROR -> context.messages().send(player, "storage-error");
            }
            return;
        }

        if (!context.movement().setManualPosition(caravan, getSpawnLocation(context, player), true, true).success()) {
            context.entities().despawnCaravan(caravan.id());
            context.messages().send(player, "storage-error");
            return;
        }

        context.messages().send(player, "physical-spawned", placeholders(context, caravan));
    }

    private Location getSpawnLocation(CommandContext context, Player player) {
        int radius = Math.max(1, context.config().getPhysicalCaravanSpawnRadius());
        return player.getLocation().clone().add(Math.min(radius, 4), 0.0D, 0.0D);
    }

    private Map<String, String> placeholders(CommandContext context, CaravanRecord caravan) {
        return Map.of(
            "id", context.caravans().getShortId(caravan),
            "name", caravan.name(),
            "player", caravan.ownerName(),
            "hp", String.valueOf(caravan.hp()),
            "max_hp", String.valueOf(caravan.maxHp())
        );
    }
}
