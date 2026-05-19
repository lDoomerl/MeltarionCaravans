package net.meltarion.caravans.command;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import net.meltarion.caravans.MeltarionCaravansPlugin;
import net.meltarion.caravans.command.subcommand.AdminSubcommand;
import net.meltarion.caravans.command.subcommand.CreateSubcommand;
import net.meltarion.caravans.command.subcommand.DeleteSubcommand;
import net.meltarion.caravans.command.subcommand.HelpSubcommand;
import net.meltarion.caravans.command.subcommand.InfoSubcommand;
import net.meltarion.caravans.command.subcommand.ListSubcommand;
import net.meltarion.caravans.command.subcommand.RenameSubcommand;
import net.meltarion.caravans.command.subcommand.ReloadSubcommand;
import net.meltarion.caravans.command.subcommand.SpawnSubcommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class CaravanCommand implements CommandExecutor, TabCompleter {

    private final MeltarionCaravansPlugin plugin;
    private final Map<String, CaravanSubcommand> subcommands = new LinkedHashMap<>();

    public CaravanCommand(MeltarionCaravansPlugin plugin) {
        this.plugin = plugin;

        register(new HelpSubcommand());
        register(new ReloadSubcommand());
        register(new CreateSubcommand());
        register(new ListSubcommand());
        register(new InfoSubcommand());
        register(new RenameSubcommand());
        register(new DeleteSubcommand());
        register(new SpawnSubcommand());
        register(new AdminSubcommand());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CaravanSubcommand subcommand = resolveSubcommand(args);
        if (subcommand == null) {
            plugin.getMessageService().send(sender, "unknown-subcommand");
            return true;
        }

        if (!sender.hasPermission(subcommand.getPermission())) {
            plugin.getMessageService().send(sender, "no-permission");
            return true;
        }

        if (subcommand.isPlayerOnly() && !(sender instanceof Player)) {
            plugin.getMessageService().send(sender, "player-only");
            return true;
        }

        subcommand.execute(new CommandContext(plugin, sender, args));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            String current = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return subcommands.values().stream()
                .filter(subcommand -> sender.hasPermission(subcommand.getPermission()))
                .map(CaravanSubcommand::getName)
                .filter(name -> name.startsWith(current))
                .collect(Collectors.toList());
        }

        CaravanSubcommand subcommand = subcommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subcommand == null || !sender.hasPermission(subcommand.getPermission())) {
            return List.of();
        }

        String[] subcommandArgs = Arrays.copyOfRange(args, 1, args.length);
        return subcommand.tabComplete(new CommandContext(plugin, sender, subcommandArgs));
    }

    private CaravanSubcommand resolveSubcommand(String[] args) {
        if (args.length == 0) {
            return subcommands.get("help");
        }
        return subcommands.get(args[0].toLowerCase(Locale.ROOT));
    }

    private void register(CaravanSubcommand subcommand) {
        subcommands.put(subcommand.getName().toLowerCase(Locale.ROOT), subcommand);
    }
}
