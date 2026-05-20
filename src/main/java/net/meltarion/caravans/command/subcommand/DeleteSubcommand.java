package net.meltarion.caravans.command.subcommand;

import java.util.Map;
import net.meltarion.caravans.command.CaravanSubcommand;
import net.meltarion.caravans.command.CommandContext;
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
        var lookupResult = context.identifiers().resolveForPlayer(player, joinArgs(context.args(), 1));
        if (!lookupResult.success()) {
            context.identifiers().sendFailure(context.sender(), lookupResult);
            return;
        }

        context.entities().despawnCaravan(lookupResult.caravan().id());
        CaravanMutationResult mutationResult = context.caravans().deleteCaravan(lookupResult.caravan());
        if (!mutationResult.success()) {
            context.messages().send(context.sender(), "storage-error");
            return;
        }

        context.movement().removeRuntimeCaravan(lookupResult.caravan().id());
        context.routes().discardCaravanState(lookupResult.caravan().id());

        context.messages().send(context.sender(), "deleted", Map.of(
            "id", context.caravans().getShortId(mutationResult.caravan()),
            "name", mutationResult.caravan().name()
        ));
    }

    private String joinArgs(String[] args, int startIndex) {
        return String.join(" ", java.util.Arrays.copyOfRange(args, startIndex, args.length));
    }
}
