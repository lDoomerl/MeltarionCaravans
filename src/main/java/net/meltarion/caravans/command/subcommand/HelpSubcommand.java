package net.meltarion.caravans.command.subcommand;

import net.meltarion.caravans.command.CaravanSubcommand;
import net.meltarion.caravans.command.CommandContext;

public final class HelpSubcommand implements CaravanSubcommand {

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getPermission() {
        return "meltarion.caravans.use";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public void execute(CommandContext context) {
        context.messages().sendList(context.sender(), "help");
    }
}
