package net.meltarion.caravans.command;

import java.util.List;

public interface CaravanSubcommand {

    String getName();

    String getPermission();

    boolean isPlayerOnly();

    void execute(CommandContext context);

    default List<String> tabComplete(CommandContext context) {
        return List.of();
    }
}
