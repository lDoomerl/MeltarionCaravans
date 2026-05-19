package net.meltarion.caravans;

import java.nio.file.Path;
import java.util.logging.Level;
import net.meltarion.caravans.command.CaravanCommand;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.listener.CaravanInventoryListener;
import net.meltarion.caravans.listener.CaravanPhysicalEntityDamageListener;
import net.meltarion.caravans.listener.CaravanPhysicalEntityInteractListener;
import net.meltarion.caravans.listener.CaravanPublicTradeListener;
import net.meltarion.caravans.listener.CaravanLicenseListener;
import net.meltarion.caravans.listener.CaravanSetupListener;
import net.meltarion.caravans.listener.TradeSetupSessionListener;
import net.meltarion.caravans.service.CaravanEntityService;
import net.meltarion.caravans.service.CaravanMovementService;
import net.meltarion.caravans.service.CaravanSetupGuiService;
import net.meltarion.caravans.service.CaravanInventoryService;
import net.meltarion.caravans.service.CaravanService;
import net.meltarion.caravans.service.CaravanLicenseService;
import net.meltarion.caravans.service.MessageService;
import net.meltarion.caravans.service.PersistentCaravanService;
import net.meltarion.caravans.service.PublicTradeGuiService;
import net.meltarion.caravans.service.PublicTradeService;
import net.meltarion.caravans.service.TownyIntegrationService;
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
    private PublicTradeGuiService publicTradeGuiService;
    private TradeSetupSessionService tradeSetupSessionService;
    private PublicTradeService publicTradeService;
    private CaravanEntityService caravanEntityService;
    private CaravanMovementService caravanMovementService;
    private TownyIntegrationService townyIntegrationService;
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
        if (caravanEntityService != null) {
            caravanEntityService.initialize();
        }
        getServer().getPluginManager().registerEvents(new CaravanInventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new CaravanLicenseListener(this), this);
        getServer().getPluginManager().registerEvents(new CaravanSetupListener(this), this);
        getServer().getPluginManager().registerEvents(new TradeSetupSessionListener(this), this);
        getServer().getPluginManager().registerEvents(new CaravanPhysicalEntityInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new CaravanPhysicalEntityDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new CaravanPublicTradeListener(this), this);
        if (caravanMovementService != null) {
            caravanMovementService.initialize();
        }
        getLogger().info("MeltarionCaravans enabled.");
    }

    @Override
    public void onDisable() {
        if (inventoryService != null) {
            inventoryService.saveAllOpenInventories();
        }
        if (caravanMovementService != null) {
            caravanMovementService.shutdown();
        }
        if (caravanEntityService != null) {
            caravanEntityService.handlePluginDisable();
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

    public PublicTradeGuiService getPublicTradeGuiService() {
        return publicTradeGuiService;
    }

    public PublicTradeService getPublicTradeService() {
        return publicTradeService;
    }

    public CaravanEntityService getCaravanEntityService() {
        return caravanEntityService;
    }

    public CaravanMovementService getCaravanMovementService() {
        return caravanMovementService;
    }

    public TownyIntegrationService getTownyIntegrationService() {
        return townyIntegrationService;
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
        this.caravanEntityService = new CaravanEntityService(this, configManager, getLogger());
        this.townyIntegrationService = new TownyIntegrationService(getServer().getPluginManager(), configManager, getLogger());
        this.publicTradeGuiService = new PublicTradeGuiService(configManager, inventoryService, tradeOperationService);
        this.publicTradeService = new PublicTradeService(
            configManager,
            inventoryService,
            tradeOperationService,
            caravanEntityService,
            townyIntegrationService,
            getLogger()
        );
        this.caravanMovementService = new CaravanMovementService(
            this,
            configManager,
            caravanService,
            caravanEntityService,
            messageService,
            getLogger()
        );
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
