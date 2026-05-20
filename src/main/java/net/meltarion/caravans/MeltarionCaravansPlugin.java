package net.meltarion.caravans;

import java.nio.file.Path;
import java.util.logging.Level;
import net.meltarion.caravans.command.CaravanCommand;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.config.GuiConfigManager;
import net.meltarion.caravans.config.LangManager;
import net.meltarion.caravans.listener.CaravanInventoryListener;
import net.meltarion.caravans.listener.CaravanPhysicalEntityDamageListener;
import net.meltarion.caravans.listener.CaravanPhysicalEntityInteractListener;
import net.meltarion.caravans.listener.CaravanPublicTradeListener;
import net.meltarion.caravans.listener.CaravanLicenseListener;
import net.meltarion.caravans.listener.CaravanSetupListener;
import net.meltarion.caravans.listener.PlayerNotificationListener;
import net.meltarion.caravans.listener.TradeSetupSessionListener;
import net.meltarion.caravans.service.CaravanEntityService;
import net.meltarion.caravans.service.CaravanRouteService;
import net.meltarion.caravans.service.CaravanMovementService;
import net.meltarion.caravans.service.CaravanSetupGuiService;
import net.meltarion.caravans.service.CaravanInventoryService;
import net.meltarion.caravans.service.CaravanService;
import net.meltarion.caravans.service.CaravanHealthService;
import net.meltarion.caravans.service.CaravanLicenseService;
import net.meltarion.caravans.service.DynmapService;
import net.meltarion.caravans.service.MessageService;
import net.meltarion.caravans.service.NotificationService;
import net.meltarion.caravans.service.PersistentCaravanService;
import net.meltarion.caravans.service.PublicTradeGuiService;
import net.meltarion.caravans.service.PublicTradeService;
import net.meltarion.caravans.service.ResourceUpdateService;
import net.meltarion.caravans.service.RouteSetupSessionService;
import net.meltarion.caravans.service.TownyIntegrationService;
import net.meltarion.caravans.service.TradeOperationService;
import net.meltarion.caravans.service.TradeSetupSessionService;
import net.meltarion.caravans.storage.CaravanInventoryStorage;
import net.meltarion.caravans.storage.CaravanRouteStorage;
import net.meltarion.caravans.storage.CaravanStorage;
import net.meltarion.caravans.storage.SQLiteCaravanStorage;
import net.meltarion.caravans.storage.StorageException;
import net.meltarion.caravans.storage.TradeOperationStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MeltarionCaravansPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private LangManager langManager;
    private GuiConfigManager guiConfigManager;
    private MessageService messageService;
    private CaravanLicenseService licenseService;
    private CaravanInventoryService inventoryService;
    private TradeOperationService tradeOperationService;
    private CaravanHealthService caravanHealthService;
    private CaravanSetupGuiService caravanSetupGuiService;
    private PublicTradeGuiService publicTradeGuiService;
    private TradeSetupSessionService tradeSetupSessionService;
    private RouteSetupSessionService routeSetupSessionService;
    private PublicTradeService publicTradeService;
    private NotificationService notificationService;
    private CaravanEntityService caravanEntityService;
    private CaravanMovementService caravanMovementService;
    private CaravanRouteService caravanRouteService;
    private TownyIntegrationService townyIntegrationService;
    private DynmapService dynmapService;
    private ResourceUpdateService resourceUpdateService;
    private CaravanStorage caravanStorage;
    private CaravanService caravanService;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Failed to create plugin data folder: " + getDataFolder());
        }

        this.resourceUpdateService = new ResourceUpdateService(this);
        resourceUpdateService.ensureResourcesUpToDate();
        reloadConfig();

        this.configManager = new ConfigManager(this);
        configManager.logLegacyWarnings();
        this.langManager = new LangManager(this);
        this.guiConfigManager = new GuiConfigManager(this);
        this.messageService = new MessageService(configManager, langManager);
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
        getServer().getPluginManager().registerEvents(new PlayerNotificationListener(this), this);
        if (caravanMovementService != null) {
            caravanMovementService.initialize();
        }
        if (caravanHealthService != null) {
            caravanHealthService.initialize();
        }
        if (caravanRouteService != null) {
            try {
                caravanRouteService.initialize();
            } catch (StorageException exception) {
                getLogger().log(Level.SEVERE, "Failed to initialize caravan routes.", exception);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }
        if (dynmapService != null) {
            dynmapService.initialize();
        }
        getLogger().info("MeltarionCaravans enabled.");
    }

    @Override
    public void onDisable() {
        if (inventoryService != null) {
            inventoryService.saveAllOpenInventories();
        }
        if (caravanRouteService != null) {
            caravanRouteService.shutdown();
        }
        if (caravanHealthService != null) {
            caravanHealthService.shutdown();
        }
        if (caravanMovementService != null) {
            caravanMovementService.shutdown();
        }
        if (dynmapService != null) {
            dynmapService.shutdown();
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
        if (resourceUpdateService != null) {
            resourceUpdateService.ensureResourcesUpToDate();
        }
        reloadConfig();
        configManager.reload();
        configManager.logLegacyWarnings();
        langManager.reload();
        guiConfigManager.reload();
        if (caravanRouteService != null) {
            caravanRouteService.shutdown();
        }
        if (caravanHealthService != null) {
            caravanHealthService.shutdown();
        }
        if (caravanMovementService != null) {
            caravanMovementService.shutdown();
            caravanMovementService.initialize();
        }
        if (caravanHealthService != null) {
            caravanHealthService.initialize();
        }
        if (caravanRouteService != null) {
            try {
                caravanRouteService.initialize();
            } catch (StorageException exception) {
                getLogger().log(Level.SEVERE, "Failed to reinitialize caravan routes during reload.", exception);
            }
        }
        if (dynmapService != null) {
            dynmapService.shutdown();
            dynmapService.initialize();
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public GuiConfigManager getGuiConfigManager() {
        return guiConfigManager;
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

    public CaravanHealthService getCaravanHealthService() {
        return caravanHealthService;
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

    public NotificationService getNotificationService() {
        return notificationService;
    }

    public RouteSetupSessionService getRouteSetupSessionService() {
        return routeSetupSessionService;
    }

    public CaravanEntityService getCaravanEntityService() {
        return caravanEntityService;
    }

    public CaravanMovementService getCaravanMovementService() {
        return caravanMovementService;
    }

    public CaravanRouteService getCaravanRouteService() {
        return caravanRouteService;
    }

    public TownyIntegrationService getTownyIntegrationService() {
        return townyIntegrationService;
    }

    public DynmapService getDynmapService() {
        return dynmapService;
    }

    private void initializeStorage() throws StorageException {
        Path databasePath = getDataFolder().toPath().resolve("caravans.db");
        SQLiteCaravanStorage storage = new SQLiteCaravanStorage(databasePath, getLogger());
        this.caravanStorage = storage;
        caravanStorage.initialize();
        this.inventoryService = new CaravanInventoryService(configManager, guiConfigManager, (CaravanInventoryStorage) storage, getLogger());
        this.tradeOperationService = new TradeOperationService(configManager, guiConfigManager, inventoryService, (TradeOperationStorage) storage, getLogger());
        tradeOperationService.loadTradeOperations();
        this.notificationService = new NotificationService(configManager, messageService);

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
        this.townyIntegrationService = new TownyIntegrationService(getServer().getPluginManager(), configManager, getLogger());
        this.caravanEntityService = new CaravanEntityService(this, configManager, getLogger());
        this.caravanHealthService = new CaravanHealthService(this, configManager, caravanService, caravanEntityService, getLogger());
        this.publicTradeGuiService = new PublicTradeGuiService(configManager, guiConfigManager, inventoryService, tradeOperationService);
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
        this.caravanRouteService = new CaravanRouteService(
            this,
            configManager,
            caravanService,
            caravanMovementService,
            notificationService,
            (CaravanRouteStorage) storage,
            getLogger()
        );
        caravanMovementService.setCaravanRouteService(caravanRouteService);
        this.caravanSetupGuiService = new CaravanSetupGuiService(
            configManager,
            guiConfigManager,
            inventoryService,
            tradeOperationService,
            caravanRouteService,
            townyIntegrationService
        );
        this.tradeSetupSessionService = new TradeSetupSessionService(
            caravanService,
            tradeOperationService,
            messageService,
            configManager::getTradeSetupTimeoutSeconds
        );
        this.routeSetupSessionService = new RouteSetupSessionService(
            caravanService,
            caravanRouteService,
            messageService,
            configManager::getRouteSetupTimeoutSeconds,
            configManager::getRouteMinStopMinutes,
            configManager::getRouteMaxStopMinutes
        );
        this.dynmapService = new DynmapService(
            this,
            getServer().getPluginManager(),
            configManager,
            caravanService,
            getLogger()
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
