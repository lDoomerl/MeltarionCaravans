package net.meltarion.caravans.command;

import net.meltarion.caravans.MeltarionCaravansPlugin;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.service.CaravanInventoryService;
import net.meltarion.caravans.service.CaravanEntityService;
import net.meltarion.caravans.service.CaravanSetupGuiService;
import net.meltarion.caravans.service.CaravanService;
import net.meltarion.caravans.service.CaravanLicenseService;
import net.meltarion.caravans.service.MessageService;
import net.meltarion.caravans.service.TownyIntegrationService;
import net.meltarion.caravans.service.TradeOperationService;
import org.bukkit.command.CommandSender;

public record CommandContext(
    MeltarionCaravansPlugin plugin,
    CommandSender sender,
    String[] args
) {

    public MessageService messages() {
        return plugin.getMessageService();
    }

    public CaravanService caravans() {
        return plugin.getCaravanService();
    }

    public CaravanLicenseService licenses() {
        return plugin.getLicenseService();
    }

    public CaravanInventoryService inventories() {
        return plugin.getInventoryService();
    }

    public TradeOperationService trades() {
        return plugin.getTradeOperationService();
    }

    public CaravanSetupGuiService setupGui() {
        return plugin.getCaravanSetupGuiService();
    }

    public CaravanEntityService entities() {
        return plugin.getCaravanEntityService();
    }

    public TownyIntegrationService towny() {
        return plugin.getTownyIntegrationService();
    }

    public ConfigManager config() {
        return plugin.getConfigManager();
    }
}
