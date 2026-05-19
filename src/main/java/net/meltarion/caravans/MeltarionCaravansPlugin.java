package net.meltarion.caravans;

import net.meltarion.caravans.command.CaravanCommand;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.service.CaravanService;
import net.meltarion.caravans.service.MessageService;
import net.meltarion.caravans.service.PersistentCaravanService;
import net.meltarion.caravans.storage.CaravanStorage;
import net.meltarion.caravans.storage.SQLiteCaravanStorage;
import net.meltarion.caravans.storage.StorageException;
import java.nio.file.Path;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MeltarionCaravansPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessageService messageService;
    private CaravanStorage caravanStorage;
    private CaravanService caravanService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.messageService = new MessageService(configManager);

        try {
            initializeStorage();
        } catch (StorageException exception) {
            getLogger().log(Level.SEVERE, "Failed to enable MeltarionCaravans because storage initialization failed.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        registerCommands();
        getLogger().info("MeltarionCaravans enabled.");
    }

    @Override
    public void onDisable() {
        if (caravanStorage != null) {
            try {
                caravanStorage.close();
            } catch (StorageException exception) {
                getLogger().log(Level.SEVERE, "Failed to close caravan storage cleanly.", exception);
            }
        }
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

    private void initializeStorage() throws StorageException {
        Path databasePath = getDataFolder().toPath().resolve("caravans.db");
        this.caravanStorage = new SQLiteCaravanStorage(databasePath, getLogger());
        caravanStorage.initialize();

        PersistentCaravanService persistentCaravanService = new PersistentCaravanService(configManager, caravanStorage, getLogger());
        persistentCaravanService.loadCaravans();
        this.caravanService = persistentCaravanService;
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
