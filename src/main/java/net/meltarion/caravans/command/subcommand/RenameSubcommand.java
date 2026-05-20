package net.meltarion.caravans.command.subcommand;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.meltarion.caravans.command.CaravanSubcommand;
import net.meltarion.caravans.command.CommandContext;
import net.meltarion.caravans.service.CaravanMutationResult;
import org.bukkit.entity.Player;

public final class RenameSubcommand implements CaravanSubcommand {

    @Override
    public String getName() {
        return "rename";
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
        if (context.args().length < 3) {
            context.messages().send(context.sender(), "rename-usage");
            return;
        }

        Player player = (Player) context.sender();
        var lookupResult = context.identifiers().resolveForPlayer(player, context.args()[1]);
        if (!lookupResult.success()) {
            context.identifiers().sendFailure(context.sender(), lookupResult);
            return;
        }

        String newName = Stream.of(context.args()).skip(2).collect(Collectors.joining(" "));
        CaravanMutationResult mutationResult = context.caravans().renameCaravan(lookupResult.caravan(), newName);
        if (!mutationResult.success()) {
            switch (mutationResult.failureReason()) {
                case INVALID_NAME -> context.messages().send(context.sender(), "invalid-name");
                case DUPLICATE_NAME -> context.messages().send(context.sender(), "duplicate-name", Map.of("name", newName));
                case STORAGE_ERROR -> context.messages().send(context.sender(), "storage-error");
            }
            return;
        }

        context.messages().send(context.sender(), "renamed", Map.of(
            "id", context.caravans().getShortId(mutationResult.caravan()),
            "name", mutationResult.caravan().name()
        ));
    }
}
