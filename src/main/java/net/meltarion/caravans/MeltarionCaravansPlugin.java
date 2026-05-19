package net.meltarion.caravans;

import java.nio.file.Path;
import java.util.logging.Level;
import net.meltarion.caravans.command.CaravanCommand;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.listener.CaravanInventoryListener;
import net.meltarion.caravans.listener.CaravanLicenseListener;
import net.meltarion.caravans.listener.CaravanSetupListener;
import net.meltarion.caravans.listener.TradeSetupSessionListener;
import net.meltarion.caravans.service.CaravanSetupGuiService;
import net.meltarion.caravans.service.CaravanInventoryService;
import net.meltarion.caravans.service.CaravanService;
import net.meltarion.caravans.service.CaravanLicenseService;
import net.meltarion.caravans.service.MessageService;
import net.meltarion.caravans.service.PersistentCaravanService;
import net.meltarion.caravans.service.TradeOperationService;
import net.meltarion.caravans.service.TradeSetupSessionService;
import net.meltarion.caravans.storage.CaravanInventoryStorage;
import net.meltarion.caravans.storage.CaravanStorage;
import net.meltarion.caravans.storage.SQLiteCaravanStorage;
import net.meltarion.caravans.storage.StorageException;
import net.meltarion.caravans.storage.TradeOperationStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MeltarionCaravansPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessageService messageService;
    private CaravanLicenseService licenseService;
    private CaravanInventoryService inventoryService;
    private TradeOperationService tradeOperationService;
    private CaravanSetupGuiService caravanSetupGuiService;
    private TradeSetupSessionService tradeSetupSessionService;
    private CaravanStorage caravanStorage;
    private CaravanService caravanService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.messageService = new MessageService(configManager);
        this.licenseService = new CaravanLicenseService(configManager);

        try {
            initializeStorage();
        } catch (StorageException exception) {
            getLogger().log(Level.SEVERE, "Failed to enable MeltarionCaravans because storage initialization failed.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        registerCommands();
        getServer().getPluginManager().registerEvents(new CaravanInventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new CaravanLicenseListener(this), this);
        getServer().getPluginManager().registerEvents(new CaravanSetupListener(this), this);
        getServer().getPluginManager().registerEvents(new TradeSetupSessionListener(this), this);
        getLogger().info("MeltarionCaravans enabled.");
    }

    @Override
    public void onDisable() {
        if (inventoryService != null) {
            inventoryService.saveAllOpenInventories();
        }
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

    public CaravanLicenseService getLicenseService() {
        return licenseService;
    }

    public CaravanInventoryService getInventoryService() {
        return inventoryService;
    }

    public TradeOperationService getTradeOperationService() {
        return tradeOperationService;
    }

    public CaravanSetupGuiService getCaravanSetupGuiService() {
        return caravanSetupGuiService;
    }

    public TradeSetupSessionService getTradeSetupSessionService() {
        return tradeSetupSessionService;
    }

    private void initializeStorage() throws StorageException {
        Path databasePath = getDataFolder().toPath().resolve("caravans.db");
        SQLiteCaravanStorage storage = new SQLiteCaravanStorage(databasePath, getLogger());
        this.caravanStorage = storage;
        caravanStorage.initialize();
        this.inventoryService = new CaravanInventoryService(configManager, (CaravanInventoryStorage) storage, getLogger());
        this.tradeOperationService = new TradeOperationService(configManager, inventoryService, (TradeOperationStorage) storage, getLogger());
        tradeOperationService.loadTradeOperations();

        PersistentCaravanService persistentCaravanService = new PersistentCaravanService(
            configManager,
            caravanStorage,
            inventoryService,
            tradeOperationService,
            licenseService,
            getLogger()
        );
        persistentCaravanService.loadCaravans();
        this.caravanService = persistentCaravanService;
        this.caravanSetupGuiService = new CaravanSetupGuiService(configManager, inventoryService, tradeOperationService);
        this.tradeSetupSessionService = new TradeSetupSessionService(
            caravanService,
            tradeOperationService,
            messageService,
            configManager::getTradeSetupTimeoutSeconds
        );
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
