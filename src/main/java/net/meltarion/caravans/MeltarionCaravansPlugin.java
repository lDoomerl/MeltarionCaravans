package net.meltarion.caravans;

import net.meltarion.caravans.command.CaravanCommand;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.service.CaravanService;
import net.meltarion.caravans.service.InMemoryCaravanService;
import net.meltarion.caravans.service.MessageService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MeltarionCaravansPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessageService messageService;
    private CaravanService caravanService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.messageService = new MessageService(configManager);
        this.caravanService = new InMemoryCaravanService(configManager);

        registerCommands();
        getLogger().info("MeltarionCaravans enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MeltarionCaravans disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        configManager.reload();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public CaravanService getCaravanService() {
        return caravanService;
    }

    private void registerCommands() {
        PluginCommand caravanCommand = getCommand("caravan");
        if (caravanCommand == null) {
            throw new IllegalStateException("Command 'caravan' is not defined in plugin.yml");
        }

        CaravanCommand executor = new CaravanCommand(this);
        caravanCommand.setExecutor(executor);
        caravanCommand.setTabCompleter(executor);
    }
}
