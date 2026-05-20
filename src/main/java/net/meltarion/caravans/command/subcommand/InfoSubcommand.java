package net.meltarion.caravans.command.subcommand;

import java.util.Map;
import net.meltarion.caravans.command.CaravanSubcommand;
import net.meltarion.caravans.command.CommandContext;
import net.meltarion.caravans.service.CaravanLookupResult;
import org.bukkit.entity.Player;

public final class InfoSubcommand implements CaravanSubcommand {

    @Override
    public String getName() {
        return "info";
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
            context.messages().send(context.sender(), "info-usage");
            return;
        }

        Player player = (Player) context.sender();
        String reference = joinArgs(context.args(), 1);
        var result = context.identifiers().resolveForPlayer(player, reference);
        if (!result.success()) {
            context.identifiers().sendFailure(context.sender(), result);
            return;
        }

        context.messages().sendList(context.sender(), "info", Map.of(
            "id", context.caravans().getShortId(result.caravan()),
            "name", result.caravan().name(),
            "player", result.caravan().ownerName(),
            "status", result.caravan().status().name(),
            "hp", String.valueOf(result.caravan().hp()),
            "max_hp", String.valueOf(result.caravan().maxHp())
        ));
    }

    private String joinArgs(String[] args, int startIndex) {
        return String.join(" ", java.util.Arrays.copyOfRange(args, startIndex, args.length));
    }
}
