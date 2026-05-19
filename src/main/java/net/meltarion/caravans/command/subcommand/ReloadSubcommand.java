package net.meltarion.caravans.command.subcommand;

import net.meltarion.caravans.command.CaravanSubcommand;
import net.meltarion.caravans.command.CommandContext;

public final class ReloadSubcommand implements CaravanSubcommand {

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getPermission() {
        return "meltarion.caravans.reload";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public void execute(CommandContext context) {
        context.plugin().reloadPlugin();
        context.messages().send(context.sender(), "reloaded");
    }
}
