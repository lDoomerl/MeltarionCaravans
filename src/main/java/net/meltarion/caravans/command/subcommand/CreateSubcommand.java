package net.meltarion.caravans.command.subcommand;

import java.util.List;
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
        Player player = (Player) context.sender();
        String requestedName = context.args().length <= 1 ? null : Stream.of(context.args()).skip(1).collect(Collectors.joining(" "));

        CaravanCreationResult result = requestedName == null
            ? context.caravans().createDefaultCaravan(player)
            : context.caravans().createNamedCaravan(player, requestedName);

        if (!result.success()) {
            switch (result.failureReason()) {
                case LICENSE_DISABLED -> context.messages().send(context.sender(), "license-disabled");
                case MISSING_LICENSE -> context.messages().send(context.sender(), "missing-license");
                case INVALID_NAME -> context.messages().send(context.sender(), "invalid-name");
                case DUPLICATE_NAME -> context.messages().send(context.sender(), "duplicate-name", Map.of("name", requestedName == null ? "" : requestedName));
                case LIMIT_REACHED -> context.messages().send(context.sender(), "limit-reached", Map.of("limit", String.valueOf(result.currentLimit())));
                case STORAGE_ERROR, LICENSE_CONSUME_FAILED -> context.messages().send(context.sender(), "storage-error");
            }
            return;
        }

        if (context.licenses().shouldConsumeOnCreate()) {
            context.messages().send(context.sender(), "license-consumed");
        }
        context.messages().send(context.sender(), "created", Map.of("name", result.caravan().name()));
    }

    @Override
    public List<String> tabComplete(CommandContext context) {
        return List.of();
    }
}
