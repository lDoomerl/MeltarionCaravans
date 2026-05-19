package net.meltarion.caravans.command.subcommand;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.meltarion.caravans.command.CaravanSubcommand;
import net.meltarion.caravans.command.CommandContext;
import net.meltarion.caravans.service.CaravanCreationResult;
import org.bukkit.entity.Player;

public final class CreateSubcommand implements CaravanSubcommand {

    @Override
    public String getName() {
        return "create";
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
        if (context.args().length == 0) {
            context.messages().send(context.sender(), "create-usage");
            return;
        }

        Player player = (Player) context.sender();
        String requestedName = Stream.of(context.args()).collect(Collectors.joining(" "));
        CaravanCreationResult result = context.caravans().createCaravan(player, requestedName);
        if (!result.success()) {
            switch (result.failureReason()) {
                case INVALID_NAME -> context.messages().send(context.sender(), "create-usage");
                case DUPLICATE_NAME -> context.messages().send(context.sender(), "duplicate-name", Map.of("name", requestedName));
                case LIMIT_REACHED -> context.messages().send(context.sender(), "limit-reached", Map.of("limit", String.valueOf(result.currentLimit())));
            }
            return;
        }

        context.messages().send(context.sender(), "created", Map.of("name", result.caravan().name()));
    }
}
