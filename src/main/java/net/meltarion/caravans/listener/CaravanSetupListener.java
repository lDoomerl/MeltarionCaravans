package net.meltarion.caravans.listener;

import java.util.List;
import java.util.Map;
import net.meltarion.caravans.MeltarionCaravansPlugin;
import net.meltarion.caravans.inventory.CaravanBuyCategoryHolder;
import net.meltarion.caravans.inventory.CaravanBuyMaterialHolder;
import net.meltarion.caravans.inventory.CaravanInfoHolder;
import net.meltarion.caravans.inventory.CaravanRouteSetupHolder;
import net.meltarion.caravans.inventory.CaravanSellSetupHolder;
import net.meltarion.caravans.inventory.CaravanSetupHolder;
import net.meltarion.caravans.inventory.CaravanTownSelectHolder;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.model.CaravanRouteStopRecord;
import net.meltarion.caravans.service.TradeCatalogCategory;
import net.meltarion.caravans.service.RoutePlotTarget;
import net.meltarion.caravans.service.RouteTownOption;
import net.meltarion.caravans.storage.StorageException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class CaravanSetupListener implements Listener {

    private static final int STORAGE_SLOT = 10;
    private static final int SELL_SLOT = 11;
    private static final int BUY_SLOT = 12;
    private static final int TRADES_SLOT = 13;
    private static final int INFO_SLOT = 14;
    private static final int ROUTE_SLOT = 15;
    private static final int CLOSE_SLOT = 16;
    private static final int BUY_BACK_SLOT = 45;
    private static final int BUY_PREVIOUS_SLOT = 48;
    private static final int BUY_NEXT_SLOT = 50;
    private static final int BUY_CLOSE_SLOT = 53;
    private static final int ROUTE_BACK_SLOT = 45;
    private static final int ROUTE_ADD_SLOT = 46;
    private static final int ROUTE_LOOP_SLOT = 47;
    private static final int ROUTE_START_SLOT = 48;
    private static final int ROUTE_STOP_SLOT = 49;
    private static final int ROUTE_CLEAR_SLOT = 50;
    private static final int ROUTE_PREVIOUS_SLOT = 51;
    private static final int ROUTE_NEXT_SLOT = 52;
    private static final int ROUTE_CLOSE_SLOT = 53;
    private static final int TOWN_BACK_SLOT = 45;
    private static final int TOWN_PREVIOUS_SLOT = 48;
    private static final int TOWN_NEXT_SLOT = 50;
    private static final int TOWN_CLOSE_SLOT = 53;

    private final MeltarionCaravansPlugin plugin;

    public CaravanSetupListener(MeltarionCaravansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof CaravanSetupHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            CaravanRecord caravan = plugin.getCaravanService().getCaravan(holder.getCaravanId());
            if (caravan == null) {
                player.closeInventory();
                plugin.getMessageService().send(player, "caravan-not-found");
                return;
            }

            switch (event.getRawSlot()) {
                case STORAGE_SLOT -> openStorage(player, caravan);
                case SELL_SLOT -> openSellSetup(player, caravan);
                case BUY_SLOT -> openBuyCategories(player, caravan);
                case TRADES_SLOT -> {
                    plugin.getTradeOperationService().openTradeManagementInventory(player, caravan);
                    plugin.getMessageService().send(player, "trade-management-opened", Map.of(
                        "id", plugin.getCaravanService().getShortId(caravan),
                        "name", caravan.name()
                    ));
                }
                case INFO_SLOT -> plugin.getCaravanSetupGuiService().openInfo(player, caravan);
                case ROUTE_SLOT -> openRouteSetup(player, caravan);
                case CLOSE_SLOT -> player.closeInventory();
                default -> {
                }
            }
            return;
        }

        if (event.getView().getTopInventory().getHolder(false) instanceof CaravanSellSetupHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
                return;
            }

            CaravanRecord caravan = plugin.getCaravanService().getCaravan(holder.getCaravanId());
            if (caravan == null) {
                player.closeInventory();
                plugin.getMessageService().send(player, "caravan-not-found");
                return;
            }

            if (event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) {
                plugin.getMessageService().send(player, "setup-no-item-selected");
                return;
            }
            if (plugin.getTradeOperationService().isReservedSlot(holder.getCaravanId(), event.getRawSlot())) {
                plugin.getMessageService().send(player, "setup-slot-already-reserved");
                return;
            }

            player.closeInventory();
            plugin.getTradeSetupSessionService().beginSellSession(player, caravan, event.getRawSlot());
            return;
        }

        if (event.getView().getTopInventory().getHolder(false) instanceof CaravanBuyCategoryHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            CaravanRecord caravan = plugin.getCaravanService().getCaravan(holder.getCaravanId());
            if (caravan == null) {
                player.closeInventory();
                plugin.getMessageService().send(player, "caravan-not-found");
                return;
            }

            if (event.getRawSlot() == 26) {
                player.closeInventory();
                return;
            }

            List<TradeCatalogCategory> categories = plugin.getCaravanSetupGuiService().getAvailableCategories();
            int[] categorySlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22};
            for (int index = 0; index < Math.min(categorySlots.length, categories.size()); index++) {
                if (event.getRawSlot() == categorySlots[index]) {
                    plugin.getCaravanSetupGuiService().openBuyMaterialCatalog(player, caravan, categories.get(index), 0);
                    return;
                }
            }
            return;
        }

        if (event.getView().getTopInventory().getHolder(false) instanceof CaravanBuyMaterialHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            CaravanRecord caravan = plugin.getCaravanService().getCaravan(holder.getCaravanId());
            if (caravan == null) {
                player.closeInventory();
                plugin.getMessageService().send(player, "caravan-not-found");
                return;
            }

            if (event.getRawSlot() == BUY_BACK_SLOT) {
                plugin.getCaravanSetupGuiService().openBuyCategoryCatalog(player, caravan);
                return;
            }
            if (event.getRawSlot() == BUY_PREVIOUS_SLOT && holder.getPage() > 0) {
                plugin.getCaravanSetupGuiService().openBuyMaterialCatalog(player, caravan, holder.getCategory(), holder.getPage() - 1);
                return;
            }
            if (event.getRawSlot() == BUY_NEXT_SLOT) {
                plugin.getCaravanSetupGuiService().openBuyMaterialCatalog(player, caravan, holder.getCategory(), holder.getPage() + 1);
                return;
            }
            if (event.getRawSlot() == BUY_CLOSE_SLOT) {
                player.closeInventory();
                return;
            }
            if (event.getRawSlot() < 0 || event.getRawSlot() >= 45) {
                return;
            }

            List<org.bukkit.Material> materials = plugin.getCaravanSetupGuiService().getMaterialsForCategory(holder.getCategory());
            int index = holder.getPage() * 45 + event.getRawSlot();
            if (index >= materials.size()) {
                plugin.getMessageService().send(player, "setup-category-empty");
                return;
            }

            org.bukkit.Material material = materials.get(index);
            if (plugin.getConfigManager().getTradeBuyCatalogBlacklist().contains(material)) {
                plugin.getMessageService().send(player, "setup-material-blacklisted", Map.of("material", material.name()));
                return;
            }

            player.closeInventory();
            plugin.getTradeSetupSessionService().beginBuySession(player, caravan, material);
            return;
        }

        if (event.getView().getTopInventory().getHolder(false) instanceof CaravanRouteSetupHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            CaravanRecord caravan = plugin.getCaravanService().getCaravan(holder.getCaravanId());
            if (caravan == null) {
                player.closeInventory();
                plugin.getMessageService().send(player, "caravan-not-found");
                return;
            }

            switch (event.getRawSlot()) {
                case ROUTE_BACK_SLOT -> plugin.getCaravanSetupGuiService().openMainSetup(player, caravan);
                case ROUTE_ADD_SLOT -> openTownSelection(player, caravan);
                case ROUTE_LOOP_SLOT -> toggleRouteLoop(player, caravan, holder.getPage());
                case ROUTE_START_SLOT -> startRoute(player, caravan);
                case ROUTE_STOP_SLOT -> stopRoute(player, caravan);
                case ROUTE_CLEAR_SLOT -> clearRoute(player, caravan);
                case ROUTE_PREVIOUS_SLOT -> {
                    if (holder.getPage() > 0) {
                        plugin.getCaravanSetupGuiService().openRouteSetup(player, caravan, holder.getPage() - 1);
                    }
                }
                case ROUTE_NEXT_SLOT -> plugin.getCaravanSetupGuiService().openRouteSetup(player, caravan, holder.getPage() + 1);
                case ROUTE_CLOSE_SLOT -> player.closeInventory();
                default -> {
                    if (event.getRawSlot() >= 0 && event.getRawSlot() < 45 && event.isShiftClick()) {
                        removeRouteStop(player, caravan, holder.getPage(), event.getRawSlot());
                    }
                }
            }
            return;
        }

        if (event.getView().getTopInventory().getHolder(false) instanceof CaravanTownSelectHolder holder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            CaravanRecord caravan = plugin.getCaravanService().getCaravan(holder.getCaravanId());
            if (caravan == null) {
                player.closeInventory();
                plugin.getMessageService().send(player, "caravan-not-found");
                return;
            }

            if (event.getRawSlot() == TOWN_BACK_SLOT) {
                plugin.getCaravanSetupGuiService().openRouteSetup(player, caravan, 0);
                return;
            }
            if (event.getRawSlot() == TOWN_PREVIOUS_SLOT && holder.getPage() > 0) {
                plugin.getCaravanSetupGuiService().openTownSelection(player, caravan, holder.getPage() - 1);
                return;
            }
            if (event.getRawSlot() == TOWN_NEXT_SLOT) {
                plugin.getCaravanSetupGuiService().openTownSelection(player, caravan, holder.getPage() + 1);
                return;
            }
            if (event.getRawSlot() == TOWN_CLOSE_SLOT) {
                player.closeInventory();
                return;
            }
            if (event.getRawSlot() < 0 || event.getRawSlot() >= 45) {
                return;
            }

            List<RouteTownOption> towns = plugin.getCaravanSetupGuiService().getAvailableRouteTowns(player);
            int index = holder.getPage() * 45 + event.getRawSlot();
            if (index >= towns.size()) {
                plugin.getMessageService().send(player, "route-no-available-towns");
                return;
            }

            RouteTownOption town = towns.get(index);
            RoutePlotTarget target = plugin.getTownyIntegrationService().getRandomShopPlotLocation(town.townName());
            if (target == null) {
                plugin.getMessageService().send(player, "route-no-shop-plots");
                return;
            }

            player.closeInventory();
            plugin.getRouteSetupSessionService().beginStopDurationSession(player, caravan, target);
            return;
        }

        if (event.getView().getTopInventory().getHolder(false) instanceof CaravanInfoHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof CaravanSetupHolder
            || event.getView().getTopInventory().getHolder(false) instanceof CaravanSellSetupHolder
            || event.getView().getTopInventory().getHolder(false) instanceof CaravanBuyCategoryHolder
            || event.getView().getTopInventory().getHolder(false) instanceof CaravanBuyMaterialHolder
            || event.getView().getTopInventory().getHolder(false) instanceof CaravanRouteSetupHolder
            || event.getView().getTopInventory().getHolder(false) instanceof CaravanTownSelectHolder
            || event.getView().getTopInventory().getHolder(false) instanceof CaravanInfoHolder) {
            event.setCancelled(true);
        }
    }

    private void openStorage(Player player, CaravanRecord caravan) {
        try {
            plugin.getInventoryService().openInventoryForAdmin(player, caravan);
            plugin.getMessageService().send(player, "inventory-opened", Map.of(
                "id", plugin.getCaravanService().getShortId(caravan),
                "name", caravan.name()
            ));
        } catch (StorageException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to open caravan storage " + caravan.id() + '.', exception);
            plugin.getMessageService().send(player, "storage-error");
        }
    }

    private void openSellSetup(Player player, CaravanRecord caravan) {
        try {
            plugin.getCaravanSetupGuiService().openSellSetup(player, caravan);
        } catch (StorageException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to open sell setup for caravan " + caravan.id() + '.', exception);
            plugin.getMessageService().send(player, "storage-error");
        }
    }

    private void openBuyCategories(Player player, CaravanRecord caravan) {
        List<TradeCatalogCategory> categories = plugin.getCaravanSetupGuiService().getAvailableCategories();
        if (categories.isEmpty()) {
            plugin.getMessageService().send(player, "setup-catalog-empty");
            return;
        }
        plugin.getCaravanSetupGuiService().openBuyCategoryCatalog(player, caravan);
    }

    private void openRouteSetup(Player player, CaravanRecord caravan) {
        plugin.getCaravanSetupGuiService().openRouteSetup(player, caravan, 0);
        plugin.getMessageService().send(player, "route-menu-opened", Map.of(
            "id", plugin.getCaravanService().getShortId(caravan),
            "name", caravan.name()
        ));
    }

    private void openTownSelection(Player player, CaravanRecord caravan) {
        if (!plugin.getConfigManager().isRouteEnabled()) {
            plugin.getMessageService().send(player, "movement-disabled");
            return;
        }
        if (!plugin.getTownyIntegrationService().isTownyAvailable()) {
            plugin.getMessageService().send(player, "route-towny-missing");
            return;
        }
        List<RouteTownOption> towns = plugin.getCaravanSetupGuiService().getAvailableRouteTowns(player);
        if (towns.isEmpty()) {
            plugin.getMessageService().send(player, "route-no-available-towns");
            return;
        }
        plugin.getCaravanSetupGuiService().openTownSelection(player, caravan, 0);
    }

    private void startRoute(Player player, CaravanRecord caravan) {
        var result = plugin.getCaravanRouteService().startRoute(caravan);
        if (!result.success()) {
            handleRouteFailure(player, result.failureReason());
            return;
        }
        plugin.getMessageService().send(player, "route-started", Map.of(
            "id", plugin.getCaravanService().getShortId(caravan),
            "name", caravan.name(),
            "order", "1",
            "eta", result.caravan().etaSeconds() == null ? "0" : String.valueOf(result.caravan().etaSeconds()),
            "town", result.routeStop() == null ? "Unknown" : result.routeStop().townName(),
            "duration", result.routeStop() == null ? "0" : String.valueOf(result.routeStop().stopDurationSeconds() / 60),
            "time_left", "0"
        ));
        plugin.getCaravanSetupGuiService().openRouteSetup(player, plugin.getCaravanService().getCaravan(caravan.id()), 0);
    }

    private void stopRoute(Player player, CaravanRecord caravan) {
        var result = plugin.getCaravanRouteService().stopRoute(caravan);
        if (!result.success()) {
            handleRouteFailure(player, result.failureReason());
            return;
        }
        plugin.getMessageService().send(player, "route-stopped", Map.of(
            "id", plugin.getCaravanService().getShortId(caravan),
            "name", caravan.name()
        ));
        plugin.getCaravanSetupGuiService().openRouteSetup(player, plugin.getCaravanService().getCaravan(caravan.id()), 0);
    }

    private void clearRoute(Player player, CaravanRecord caravan) {
        var result = plugin.getCaravanRouteService().clearRoute(caravan);
        if (!result.success()) {
            handleRouteFailure(player, result.failureReason());
            return;
        }
        plugin.getMessageService().send(player, "route-cleared", Map.of(
            "id", plugin.getCaravanService().getShortId(caravan),
            "name", caravan.name()
        ));
        plugin.getCaravanSetupGuiService().openRouteSetup(player, plugin.getCaravanService().getCaravan(caravan.id()), 0);
    }

    private void toggleRouteLoop(Player player, CaravanRecord caravan, int page) {
        var result = plugin.getCaravanRouteService().toggleLoopMode(caravan);
        if (!result.success()) {
            handleRouteFailure(player, result.failureReason());
            return;
        }

        plugin.getMessageService().send(player, result.caravan().routeLoopEnabled() ? "route-loop-enabled" : "route-loop-disabled", Map.of(
            "id", plugin.getCaravanService().getShortId(result.caravan()),
            "name", result.caravan().name()
        ));
        plugin.getCaravanSetupGuiService().openRouteSetup(player, result.caravan(), page);
    }

    private void removeRouteStop(Player player, CaravanRecord caravan, int page, int rawSlot) {
        List<CaravanRouteStopRecord> stops = plugin.getCaravanRouteService().getRouteStops(caravan.id());
        int index = page * 45 + rawSlot;
        if (index >= stops.size()) {
            return;
        }

        CaravanRouteStopRecord stop = stops.get(index);
        var result = plugin.getCaravanRouteService().removeRouteStop(caravan, stop.id());
        if (!result.success()) {
            handleRouteFailure(player, result.failureReason());
            return;
        }

        plugin.getMessageService().send(player, "route-stop-removed", Map.of(
            "id", plugin.getCaravanService().getShortId(caravan),
            "name", caravan.name(),
            "town", stop.townName(),
            "order", String.valueOf(stop.stopOrder() + 1)
        ));
        plugin.getCaravanSetupGuiService().openRouteSetup(player, plugin.getCaravanService().getCaravan(caravan.id()), page);
    }

    private void handleRouteFailure(CommandSender sender, net.meltarion.caravans.service.CaravanRouteFailureReason failureReason) {
        switch (failureReason) {
            case DISABLED -> plugin.getMessageService().send(sender, "movement-disabled");
            case TOWNY_MISSING -> plugin.getMessageService().send(sender, "route-towny-missing");
            case NO_STOPS -> plugin.getMessageService().send(sender, "route-no-stops");
            case INVALID_DURATION -> plugin.getMessageService().send(sender, "route-invalid-duration", Map.of("duration", "?"));
            case MAX_STOPS_REACHED -> plugin.getMessageService().send(sender, "route-max-stops-reached");
            case NO_AVAILABLE_TOWNS -> plugin.getMessageService().send(sender, "route-no-available-towns");
            case NO_SHOP_PLOTS -> plugin.getMessageService().send(sender, "route-no-shop-plots");
            case ALREADY_RUNNING, INVALID_STATE -> plugin.getMessageService().send(sender, "storage-error");
            case NOT_RUNNING -> plugin.getMessageService().send(sender, "route-not-running");
            case STORAGE_ERROR -> plugin.getMessageService().send(sender, "storage-error");
        }
    }
}
