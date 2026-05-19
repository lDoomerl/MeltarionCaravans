package net.meltarion.caravans.command.subcommand;

import java.util.Map;
import net.meltarion.caravans.command.CaravanSubcommand;
import net.meltarion.caravans.command.CommandContext;
import net.meltarion.caravans.service.CaravanLookupResult;
import net.meltarion.caravans.service.CaravanMutationResult;
import org.bukkit.entity.Player;

public final class DeleteSubcommand implements CaravanSubcommand {

    @Override
    public String getName() {
        return "delete";
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
        if (context.args().length < 2) {
            context.messages().send(context.sender(), "delete-usage");
            return;
        }

        Player player = (Player) context.sender();
        CaravanLookupResult lookupResult = context.caravans().findCaravanForOwner(player.getUniqueId(), context.args()[1]);
        if (!lookupResult.success()) {
            if (lookupResult.failureReason() == CaravanLookupResult.FailureReason.AMBIGUOUS) {
                context.messages().send(context.sender(), "ambiguous-id");
            } else {
                context.messages().send(context.sender(), "caravan-not-found");
            }
            return;
        }

        CaravanMutationResult mutationResult = context.caravans().deleteCaravan(lookupResult.caravan());
        if (!mutationResult.success()) {
            context.messages().send(context.sender(), "storage-error");
            return;
        }

        context.entities().despawnCaravan(lookupResult.caravan().id());
        context.movement().removeRuntimeCaravan(lookupResult.caravan().id());

        context.messages().send(context.sender(), "deleted", Map.of(
            "id", context.caravans().getShortId(mutationResult.caravan()),
            "name", mutationResult.caravan().name()
        ));
    }
}
