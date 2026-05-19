package net.meltarion.caravans.command.subcommand;

import java.util.List;
import java.util.Map;
import net.meltarion.caravans.command.CaravanSubcommand;
import net.meltarion.caravans.command.CommandContext;
import net.meltarion.caravans.model.CaravanRecord;
import org.bukkit.entity.Player;

public final class ListSubcommand implements CaravanSubcommand {

    @Override
    public String getName() {
        return "list";
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
        List<CaravanRecord> caravans = context.caravans().getCaravans(player.getUniqueId());
        if (caravans.isEmpty()) {
            context.messages().send(context.sender(), "list-empty");
            return;
        }

        context.messages().send(context.sender(), "list-header");
        for (CaravanRecord caravan : caravans) {
            context.messages().send(context.sender(), "list-entry", Map.of("name", caravan.name()));
        }
    }
}
