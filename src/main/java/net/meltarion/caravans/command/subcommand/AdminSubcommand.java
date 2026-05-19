package net.meltarion.caravans.command.subcommand;

import java.util.List;
import java.util.Map;
import net.meltarion.caravans.command.CaravanSubcommand;
import net.meltarion.caravans.command.CommandContext;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.model.TradeOperationType;
import net.meltarion.caravans.service.CaravanLookupResult;
import net.meltarion.caravans.service.CaravanMovementResult;
import net.meltarion.caravans.service.CaravanMutationResult;
import net.meltarion.caravans.service.PhysicalSpawnFailureReason;
import net.meltarion.caravans.service.PhysicalSpawnResult;
import net.meltarion.caravans.service.TradeOperationCreateResult;
import net.meltarion.caravans.storage.StorageException;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class AdminSubcommand implements CaravanSubcommand {

    @Override
    public String getName() {
        return "admin";
    }

    @Override
    public String getPermission() {
        return "meltarion.caravans.admin";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public void execute(CommandContext context) {
        if (context.args().length < 2) {
            context.messages().sendList(context.sender(), "admin-help");
            return;
        }

        String action = context.args()[1].toLowerCase();
        switch (action) {
            case "list" -> handleList(context);
            case "info" -> handleInfo(context);
            case "open" -> handleOpen(context);
            case "setup" -> handleSetup(context);
            case "spawn" -> handleSpawn(context);
            case "despawn" -> handleDespawn(context);
            case "move" -> handleMove(context);
            case "position" -> handlePosition(context);
            case "return" -> handleReturn(context);
            case "trades" -> handleTrades(context);
            case "sell" -> handleSell(context);
            case "buy" -> handleBuy(context);
            case "delete" -> handleDelete(context);
            case "reload" -> handleReload(context);
            case "givelicense" -> handleGiveLicense(context);
            default -> context.messages().sendList(context.sender(), "admin-help");
        }
    }

    @Override
    public List<String> tabComplete(CommandContext context) {
        if (context.args().length == 1) {
            return List.of("list", "info", "open", "setup", "spawn", "despawn", "move", "position", "return", "trades", "sell", "buy", "delete", "reload", "givelicense").stream()
                .filter(option -> option.startsWith(context.args()[0].toLowerCase()))
                .toList();
        }
        return List.of();
    }

    private void handleList(CommandContext context) {
        if (context.args().length < 3) {
            context.messages().send(context.sender(), "admin-list-usage");
            return;
        }

        String playerName = context.args()[2];
        List<CaravanRecord> caravans = context.caravans().getCaravansByOwnerName(playerName);
        if (caravans.isEmpty()) {
            context.messages().send(context.sender(), "player-has-no-caravans", Map.of("player", playerName));
            return;
        }

        context.messages().send(context.sender(), "admin-list-header", Map.of("player", playerName));
        for (CaravanRecord caravan : caravans) {
            context.messages().send(context.sender(), "admin-list-entry", Map.of(
                "id", context.caravans().getShortId(caravan),
                "name", caravan.name(),
                "player", caravan.ownerName(),
                "status", caravan.status().name(),
                "hp", String.valueOf(caravan.hp()),
                "max_hp", String.valueOf(caravan.maxHp())
            ));
        }
    }

    private void handleInfo(CommandContext context) {
        if (context.args().length < 3) {
            context.messages().send(context.sender(), "admin-info-usage");
            return;
        }

        CaravanLookupResult result = context.caravans().findCaravan(context.args()[2]);
        if (!result.success()) {
            if (result.failureReason() == CaravanLookupResult.FailureReason.AMBIGUOUS) {
                context.messages().send(context.sender(), "ambiguous-id");
            } else {
                context.messages().send(context.sender(), "caravan-not-found");
            }
            return;
        }

        context.messages().sendList(context.sender(), "info", Map.of(
            "id", context.caravans().getShortId(result.caravan()),
            "name", result.caravan().name(),
            "player", result.caravan().ownerName(),
            "status", result.caravan().status().name(),
            "hp", String.valueOf(result.caravan().hp()),
            "max_hp", String.valueOf(result.caravan().maxHp())
        ));
    }

    private void handleDelete(CommandContext context) {
        if (context.args().length < 3) {
            context.messages().send(context.sender(), "admin-delete-usage");
            return;
        }

        CaravanLookupResult lookupResult = context.caravans().findCaravan(context.args()[2]);
        if (!lookupResult.success()) {
            if (lookupResult.failureReason() == CaravanLookupResult.FailureReason.AMBIGUOUS) {
                context.messages().send(context.sender(), "ambiguous-id");
            } else {
                context.messages().send(context.sender(), "caravan-not-found");
            }
            return;
        }

        CaravanMutationResult mutationResult = context.caravans().deleteCaravan(lookupResult.caravan());
        if (!mutationResult.success()) {
            context.messages().send(context.sender(), "storage-error");
            return;
        }

        context.entities().despawnCaravan(lookupResult.caravan().id());
        context.movement().removeRuntimeCaravan(lookupResult.caravan().id());

        context.messages().send(context.sender(), "deleted", Map.of(
            "id", context.caravans().getShortId(mutationResult.caravan()),
            "name", mutationResult.caravan().name()
        ));
    }

    private void handleTrades(CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            context.messages().send(context.sender(), "admin-open-only");
            return;
        }
        if (context.args().length < 3) {
            context.messages().send(context.sender(), "admin-trades-usage");
            return;
        }

        CaravanRecord caravan = resolveAdminCaravan(context, context.args()[2]);
        if (caravan == null) {
            return;
        }

        context.trades().openTradeManagementInventory(player, caravan);
        context.messages().send(context.sender(), "trade-management-opened", Map.of(
            "id", context.caravans().getShortId(caravan),
            "name", caravan.name()
        ));
    }

    private void handleSpawn(CommandContext context) {
        if (context.args().length < 3) {
            context.messages().send(context.sender(), "admin-spawn-usage");
            return;
        }

        CaravanRecord caravan = resolveAdminCaravan(context, context.args()[2]);
        if (caravan == null) {
            return;
        }

        Player targetPlayer = null;
        if (context.args().length >= 4) {
            targetPlayer = Bukkit.getPlayerExact(context.args()[3]);
            if (targetPlayer == null) {
                context.messages().send(context.sender(), "player-not-found", Map.of("player", context.args()[3]));
                return;
            }
        } else if (context.sender() instanceof Player player) {
            targetPlayer = player;
        }

        if (targetPlayer == null) {
            context.messages().send(context.sender(), "admin-spawn-player-required");
            return;
        }

        PhysicalSpawnResult result = context.entities().spawnCaravan(caravan, targetPlayer.getLocation());
        if (!result.success()) {
            switch (result.failureReason()) {
                case DISABLED -> context.messages().send(context.sender(), "physical-spawn-disabled");
                case ALREADY_SPAWNED -> context.messages().send(context.sender(), "physical-already-spawned", physicalPlaceholders(context, caravan));
                case ERROR -> context.messages().send(context.sender(), "storage-error");
            }
            return;
        }

        if (!context.movement().setManualPosition(caravan, targetPlayer.getLocation(), false, true).success()) {
            context.entities().despawnCaravan(caravan.id());
            context.messages().send(context.sender(), "storage-error");
            return;
        }

        context.messages().send(context.sender(), "physical-spawned", physicalPlaceholders(context, caravan));
    }

    private void handleDespawn(CommandContext context) {
        if (context.args().length < 3) {
            context.messages().send(context.sender(), "admin-despawn-usage");
            return;
        }

        CaravanRecord caravan = resolveAdminCaravan(context, context.args()[2]);
        if (caravan == null) {
            return;
        }

        if (!context.entities().despawnCaravan(caravan.id())) {
            context.messages().send(context.sender(), "physical-not-spawned", physicalPlaceholders(context, caravan));
            return;
        }

        context.movement().markPhysicalProjection(caravan, false);

        context.messages().send(context.sender(), "physical-despawned", physicalPlaceholders(context, caravan));
    }

    private void handleMove(CommandContext context) {
        if (context.args().length < 6) {
            context.messages().send(context.sender(), "admin-move-usage");
            return;
        }

        CaravanRecord caravan = resolveAdminCaravan(context, context.args()[2]);
        if (caravan == null) {
            return;
        }

        org.bukkit.World world = Bukkit.getWorld(context.args()[3]);
        if (world == null) {
            context.messages().send(context.sender(), "movement-invalid-target");
            return;
        }

        double x;
        double z;
        try {
            x = Double.parseDouble(context.args()[4]);
            z = Double.parseDouble(context.args()[5]);
        } catch (NumberFormatException exception) {
            context.messages().send(context.sender(), "movement-invalid-target");
            return;
        }

        CaravanMovementResult result = context.movement().startMovement(caravan, world, x, z, net.meltarion.caravans.model.CaravanStatus.TRAVELING);
        handleMovementResult(context, result, "movement-started");
    }

    private void handleReturn(CommandContext context) {
        if (context.args().length < 3) {
            context.messages().send(context.sender(), "admin-return-usage");
            return;
        }

        CaravanRecord caravan = resolveAdminCaravan(context, context.args()[2]);
        if (caravan == null) {
            return;
        }

        CaravanMovementResult result = context.movement().returnHome(caravan);
        handleMovementResult(context, result, "movement-started");
    }

    private void handlePosition(CommandContext context) {
        if (context.args().length < 3) {
            context.messages().send(context.sender(), "admin-position-usage");
            return;
        }

        CaravanRecord caravan = resolveAdminCaravan(context, context.args()[2]);
        if (caravan == null) {
            return;
        }

        CaravanRecord runtime = context.movement().getRuntimeCaravan(caravan.id());
        if (runtime == null || !runtime.hasVirtualPosition()) {
            context.messages().send(context.sender(), "movement-no-position");
            return;
        }

        context.messages().sendList(context.sender(), "movement-position-info", movementPlaceholders(context, runtime));
    }

    private void handleSetup(CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            context.messages().send(context.sender(), "admin-open-only");
            return;
        }
        if (context.args().length < 3) {
            context.messages().send(context.sender(), "admin-setup-usage");
            return;
        }

        CaravanRecord caravan = resolveAdminCaravan(context, context.args()[2]);
        if (caravan == null) {
            return;
        }

        context.setupGui().openMainSetup(player, caravan);
        context.messages().send(context.sender(), "setup-opened", Map.of(
            "id", context.caravans().getShortId(caravan),
            "name", caravan.name()
        ));
    }

    private void handleSell(CommandContext context) {
        if (context.args().length < 5) {
            context.messages().send(context.sender(), "admin-sell-usage");
            return;
        }

        CaravanRecord caravan = resolveAdminCaravan(context, context.args()[2]);
        if (caravan == null) {
            return;
        }

        int slot;
        int price;
        try {
            slot = Integer.parseInt(context.args()[3]);
            price = Integer.parseInt(context.args()[4]);
        } catch (NumberFormatException exception) {
            context.messages().send(context.sender(), "trade-invalid-amount");
            return;
        }

        TradeOperationCreateResult result = context.trades().createSellOperation(caravan, slot, price);
        handleTradeCreateResult(context, result, caravan);
    }

    private void handleBuy(CommandContext context) {
        if (context.args().length < 7) {
            context.messages().send(context.sender(), "admin-buy-usage");
            return;
        }

        CaravanRecord caravan = resolveAdminCaravan(context, context.args()[2]);
        if (caravan == null) {
            return;
        }

        Material material = Material.matchMaterial(context.args()[3]);
        int amountPerTransaction;
        int price;
        int maxTotal;
        try {
            amountPerTransaction = Integer.parseInt(context.args()[4]);
            price = Integer.parseInt(context.args()[5]);
            maxTotal = Integer.parseInt(context.args()[6]);
        } catch (NumberFormatException exception) {
            context.messages().send(context.sender(), "trade-invalid-amount");
            return;
        }

        TradeOperationCreateResult result = context.trades().createBuyOperation(
            caravan,
            material,
            amountPerTransaction,
            price,
            maxTotal
        );
        handleTradeCreateResult(context, result, caravan);
    }

    private void handleOpen(CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            context.messages().send(context.sender(), "admin-open-only");
            return;
        }

        if (context.args().length < 3) {
            context.messages().send(context.sender(), "admin-open-usage");
            return;
        }

        CaravanLookupResult result = context.caravans().findCaravan(context.args()[2]);
        if (!result.success()) {
            if (result.failureReason() == CaravanLookupResult.FailureReason.AMBIGUOUS) {
                context.messages().send(context.sender(), "ambiguous-id");
            } else {
                context.messages().send(context.sender(), "caravan-not-found");
            }
            return;
        }

        try {
            context.inventories().openInventoryForAdmin(player, result.caravan());
        } catch (StorageException exception) {
            context.plugin().getLogger().log(java.util.logging.Level.SEVERE, "Failed to open caravan inventory " + result.caravan().id() + '.', exception);
            context.messages().send(context.sender(), "storage-error");
            return;
        }

        context.messages().send(context.sender(), "inventory-opened", Map.of(
            "id", context.caravans().getShortId(result.caravan()),
            "name", result.caravan().name()
        ));
    }

    private void handleReload(CommandContext context) {
        context.plugin().reloadPlugin();
        context.messages().send(context.sender(), "reloaded");
    }

    private void handleGiveLicense(CommandContext context) {
        if (context.args().length < 3) {
            context.messages().send(context.sender(), "admin-givelicense-usage");
            return;
        }

        Player target = Bukkit.getPlayerExact(context.args()[2]);
        if (target == null) {
            context.messages().send(context.sender(), "player-not-found", Map.of("player", context.args()[2]));
            return;
        }

        int amount = 1;
        if (context.args().length >= 4) {
            try {
                amount = Integer.parseInt(context.args()[3]);
            } catch (NumberFormatException exception) {
                context.messages().send(context.sender(), "invalid-amount");
                return;
            }
        }

        if (amount < 1 || amount > 64) {
            context.messages().send(context.sender(), "invalid-amount");
            return;
        }

        context.licenses().giveLicenses(target, amount);
        context.messages().send(context.sender(), "license-given", Map.of(
            "player", target.getName(),
            "amount", String.valueOf(amount)
        ));
        context.messages().send(target, "license-received", Map.of("amount", String.valueOf(amount)));
    }

    private CaravanRecord resolveAdminCaravan(CommandContext context, String reference) {
        CaravanLookupResult result = context.caravans().findCaravan(reference);
        if (!result.success()) {
            if (result.failureReason() == CaravanLookupResult.FailureReason.AMBIGUOUS) {
                context.messages().send(context.sender(), "ambiguous-id");
            } else {
                context.messages().send(context.sender(), "caravan-not-found");
            }
            return null;
        }
        return result.caravan();
    }

    private void handleTradeCreateResult(CommandContext context, TradeOperationCreateResult result, CaravanRecord caravan) {
        if (!result.success()) {
            switch (result.failureReason()) {
                case INVALID_PRICE -> context.messages().send(context.sender(), "trade-invalid-price");
                case INVALID_AMOUNT -> context.messages().send(context.sender(), "trade-invalid-amount");
                case INVALID_MATERIAL -> context.messages().send(context.sender(), "trade-invalid-material");
                case EMPTY_SLOT -> context.messages().send(context.sender(), "trade-empty-slot");
                case DUPLICATE_SLOT -> context.messages().send(context.sender(), "trade-duplicate-slot");
                case TRADE_NOT_FOUND, STORAGE_ERROR -> context.messages().send(context.sender(), "storage-error");
            }
            return;
        }

        if (result.tradeOperation().type() == TradeOperationType.SELL) {
            context.messages().send(context.sender(), "trade-created-sell", Map.of(
                "id", context.caravans().getShortId(caravan),
                "name", caravan.name()
            ));
        } else {
            context.messages().send(context.sender(), "trade-created-buy", Map.of(
                "id", context.caravans().getShortId(caravan),
                "name", caravan.name()
            ));
        }
    }

    private Map<String, String> physicalPlaceholders(CommandContext context, CaravanRecord caravan) {
        return Map.of(
            "id", context.caravans().getShortId(caravan),
            "name", caravan.name(),
            "player", caravan.ownerName(),
            "hp", String.valueOf(caravan.hp()),
            "max_hp", String.valueOf(caravan.maxHp())
        );
    }

    private void handleMovementResult(CommandContext context, CaravanMovementResult result, String successMessageKey) {
        if (!result.success()) {
            switch (result.failureReason()) {
                case DISABLED -> context.messages().send(context.sender(), "movement-disabled");
                case NO_POSITION -> context.messages().send(context.sender(), "movement-no-position");
                case INVALID_TARGET -> context.messages().send(context.sender(), "movement-invalid-target");
                case HOME_MISSING -> context.messages().send(context.sender(), "movement-no-home");
                case STORAGE_ERROR -> context.messages().send(context.sender(), "storage-error");
            }
            return;
        }

        context.messages().send(context.sender(), successMessageKey, movementPlaceholders(context, result.caravan()));
    }

    private Map<String, String> movementPlaceholders(CommandContext context, CaravanRecord caravan) {
        return Map.ofEntries(
            Map.entry("id", context.caravans().getShortId(caravan)),
            Map.entry("name", caravan.name()),
            Map.entry("world", caravan.worldName() == null ? "unknown" : caravan.worldName()),
            Map.entry("x", caravan.virtualX() == null ? "?" : String.format(java.util.Locale.US, "%.1f", caravan.virtualX())),
            Map.entry("y", caravan.virtualY() == null ? "?" : String.format(java.util.Locale.US, "%.1f", caravan.virtualY())),
            Map.entry("z", caravan.virtualZ() == null ? "?" : String.format(java.util.Locale.US, "%.1f", caravan.virtualZ())),
            Map.entry("target_x", caravan.targetX() == null ? "?" : String.format(java.util.Locale.US, "%.1f", caravan.targetX())),
            Map.entry("target_y", caravan.targetY() == null ? "?" : String.format(java.util.Locale.US, "%.1f", caravan.targetY())),
            Map.entry("target_z", caravan.targetZ() == null ? "?" : String.format(java.util.Locale.US, "%.1f", caravan.targetZ())),
            Map.entry("speed", String.format(java.util.Locale.US, "%.2f", caravan.speedBlocksPerSecond())),
            Map.entry("eta", caravan.etaSeconds() == null ? "?" : String.valueOf(caravan.etaSeconds())),
            Map.entry("status", caravan.status().name()),
            Map.entry("physical_spawned", String.valueOf(caravan.physicalSpawned()))
        );
    }
}
