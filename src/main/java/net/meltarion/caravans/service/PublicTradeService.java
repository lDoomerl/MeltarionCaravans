package net.meltarion.caravans.service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.model.TradeOperationRecord;
import net.meltarion.caravans.model.TradeOperationType;
import net.meltarion.caravans.util.InventoryTransactionUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class PublicTradeService {

    private final ConfigManager configManager;
    private final CaravanInventoryService inventoryService;
    private final TradeOperationService tradeOperationService;
    private final CaravanEntityService caravanEntityService;
    private final TownyIntegrationService townyIntegrationService;
    private final Logger logger;
    private final Set<UUID> transactionLocks = ConcurrentHashMap.newKeySet();

    public PublicTradeService(
        ConfigManager configManager,
        CaravanInventoryService inventoryService,
        TradeOperationService tradeOperationService,
        CaravanEntityService caravanEntityService,
        TownyIntegrationService townyIntegrationService,
        Logger logger
    ) {
        this.configManager = configManager;
        this.inventoryService = inventoryService;
        this.tradeOperationService = tradeOperationService;
        this.caravanEntityService = caravanEntityService;
        this.townyIntegrationService = townyIntegrationService;
        this.logger = logger;
    }

    public PublicTradeResult attemptBuyFromCaravan(Player player, CaravanRecord caravan, TradeOperationRecord operation) {
        if (!transactionLocks.add(caravan.id())) {
            return failure("public-trade-transaction-failed", caravan, operation);
        }
        TradeOperationRecord latestOperation = validateCommon(caravan, operation, TradeOperationType.SELL);
        ItemStack[] playerSnapshot = null;
        ItemStack[] caravanSnapshot = null;
        try {
            if (latestOperation == null) {
                return failure("public-trade-operation-inactive", caravan, operation);
            }
            if (!isOnShopPlot(caravan.id())) {
                return failure("public-trade-shop-plot-required", caravan, latestOperation);
            }
            Inventory caravanInventory = inventoryService.getInventory(caravan);
            playerSnapshot = cloneContents(player.getInventory());
            caravanSnapshot = cloneContents(caravanInventory);
            Integer reservedSlot = latestOperation.reservedInventorySlot();
            if (reservedSlot == null || reservedSlot < 0 || reservedSlot >= caravanInventory.getSize()) {
                return failure("public-trade-reserved-slot-invalid", caravan, latestOperation);
            }

            ItemStack currentStack = caravanInventory.getItem(reservedSlot);
            if (currentStack == null || currentStack.getType().isAir()
                || !currentStack.isSimilar(latestOperation.itemStack())
                || currentStack.getAmount() < latestOperation.amountPerTransaction()) {
                return failure("public-trade-reserved-slot-invalid", caravan, latestOperation);
            }

            Material currency = configManager.getCurrencyItem();
            if (InventoryTransactionUtil.countCurrency(player.getInventory(), currency) < latestOperation.priceCurrencyAmount()) {
                return failure("public-trade-not-enough-currency", caravan, latestOperation);
            }

            if (!InventoryTransactionUtil.hasSpaceFor(player.getInventory(), latestOperation.itemStack(), latestOperation.amountPerTransaction())) {
                return failure("public-trade-not-enough-space", caravan, latestOperation);
            }

            if (!InventoryTransactionUtil.hasSpaceFor(caravanInventory, new ItemStack(currency, 1), latestOperation.priceCurrencyAmount())) {
                return failure("public-trade-caravan-not-enough-space", caravan, latestOperation);
            }

            if (!InventoryTransactionUtil.removeCurrency(player.getInventory(), currency, latestOperation.priceCurrencyAmount())) {
                return failure("public-trade-transaction-failed", caravan, latestOperation);
            }
            if (!InventoryTransactionUtil.addItemsSafely(player.getInventory(), latestOperation.itemStack(), latestOperation.amountPerTransaction())) {
                InventoryTransactionUtil.addCurrency(player.getInventory(), currency, latestOperation.priceCurrencyAmount());
                return failure("public-trade-transaction-failed", caravan, latestOperation);
            }

            ItemStack updatedReserved = currentStack.clone();
            updatedReserved.setAmount(updatedReserved.getAmount() - latestOperation.amountPerTransaction());
            caravanInventory.setItem(reservedSlot, updatedReserved.getAmount() <= 0 ? null : updatedReserved);

            if (!InventoryTransactionUtil.addCurrency(caravanInventory, currency, latestOperation.priceCurrencyAmount())) {
                restoreContents(player.getInventory(), playerSnapshot);
                restoreContents(caravanInventory, caravanSnapshot);
                return failure("public-trade-transaction-failed", caravan, latestOperation);
            }

            int fulfilled = latestOperation.fulfilledAmount() + latestOperation.amountPerTransaction();
            boolean depleted = updatedReserved.getAmount() <= 0;
            TradeOperationRecord updatedOperation = new TradeOperationRecord(
                latestOperation.id(),
                latestOperation.caravanId(),
                latestOperation.type(),
                latestOperation.itemStack().clone(),
                latestOperation.amountPerTransaction(),
                latestOperation.priceCurrencyAmount(),
                latestOperation.maxTotalAmount(),
                fulfilled,
                latestOperation.reservedInventorySlot(),
                !depleted,
                latestOperation.createdAt(),
                Instant.now()
            );

            inventoryService.saveInventory(caravan);
            TradeOperationMutationResult mutationResult = tradeOperationService.updateOperation(updatedOperation);
            if (!mutationResult.success()) {
                restoreContents(player.getInventory(), playerSnapshot);
                restoreContents(caravanInventory, caravanSnapshot);
                inventoryService.saveInventory(caravan);
                return failure("public-trade-transaction-failed", caravan, latestOperation);
            }

            return depleted
                ? success("public-trade-stock-depleted", caravan, updatedOperation, remainingForSell(caravan, updatedOperation))
                : success("public-trade-buy-success", caravan, updatedOperation, remainingForSell(caravan, updatedOperation));
        } catch (Exception exception) {
            if (playerSnapshot != null && caravanSnapshot != null) {
                restoreContents(player.getInventory(), playerSnapshot);
                try {
                    Inventory caravanInventory = inventoryService.getInventory(caravan);
                    restoreContents(caravanInventory, caravanSnapshot);
                    inventoryService.saveInventory(caravan);
                } catch (Exception restoreException) {
                    exception.addSuppressed(restoreException);
                }
            }
            logger.log(Level.SEVERE, "Failed to complete public SELL transaction for caravan " + caravan.id() + '.', exception);
            return failure("public-trade-transaction-failed", caravan, operation);
        } finally {
            transactionLocks.remove(caravan.id());
        }
    }

    public PublicTradeResult attemptSellToCaravan(Player player, CaravanRecord caravan, TradeOperationRecord operation) {
        if (!transactionLocks.add(caravan.id())) {
            return failure("public-trade-transaction-failed", caravan, operation);
        }
        TradeOperationRecord latestOperation = validateCommon(caravan, operation, TradeOperationType.BUY);
        ItemStack[] playerSnapshot = null;
        ItemStack[] caravanSnapshot = null;
        try {
            if (latestOperation == null) {
                return failure("public-trade-operation-inactive", caravan, operation);
            }
            if (!isOnShopPlot(caravan.id())) {
                return failure("public-trade-shop-plot-required", caravan, latestOperation);
            }
            int remainingWanted = remainingForBuy(latestOperation);
            if (remainingWanted < latestOperation.amountPerTransaction()) {
                return failure("public-trade-operation-inactive", caravan, latestOperation);
            }
            Inventory caravanInventory = inventoryService.getInventory(caravan);
            playerSnapshot = cloneContents(player.getInventory());
            caravanSnapshot = cloneContents(caravanInventory);
            ItemStack operationItem = latestOperation.itemStack().clone();
            operationItem.setAmount(1);
            Material currency = configManager.getCurrencyItem();

            if (InventoryTransactionUtil.countSimilarItems(player.getInventory(), operationItem) < latestOperation.amountPerTransaction()) {
                return failure("public-trade-not-enough-items", caravan, latestOperation);
            }
            if (!InventoryTransactionUtil.hasSpaceFor(caravanInventory, operationItem, latestOperation.amountPerTransaction())) {
                return failure("public-trade-caravan-not-enough-space", caravan, latestOperation);
            }
            if (InventoryTransactionUtil.countCurrency(caravanInventory, currency) < latestOperation.priceCurrencyAmount()) {
                return failure("public-trade-caravan-not-enough-currency", caravan, latestOperation);
            }
            if (!InventoryTransactionUtil.hasSpaceFor(player.getInventory(), new ItemStack(currency, 1), latestOperation.priceCurrencyAmount())) {
                return failure("public-trade-not-enough-space", caravan, latestOperation);
            }

            if (!InventoryTransactionUtil.removeSimilarItems(player.getInventory(), operationItem, latestOperation.amountPerTransaction())) {
                return failure("public-trade-transaction-failed", caravan, latestOperation);
            }
            if (!InventoryTransactionUtil.removeCurrency(caravanInventory, currency, latestOperation.priceCurrencyAmount())) {
                restoreContents(player.getInventory(), playerSnapshot);
                restoreContents(caravanInventory, caravanSnapshot);
                return failure("public-trade-transaction-failed", caravan, latestOperation);
            }
            if (!InventoryTransactionUtil.addCurrency(player.getInventory(), currency, latestOperation.priceCurrencyAmount())) {
                restoreContents(player.getInventory(), playerSnapshot);
                restoreContents(caravanInventory, caravanSnapshot);
                return failure("public-trade-transaction-failed", caravan, latestOperation);
            }
            if (!InventoryTransactionUtil.addItemsSafely(caravanInventory, operationItem, latestOperation.amountPerTransaction())) {
                restoreContents(player.getInventory(), playerSnapshot);
                restoreContents(caravanInventory, caravanSnapshot);
                return failure("public-trade-transaction-failed", caravan, latestOperation);
            }

            int fulfilled = latestOperation.fulfilledAmount() + latestOperation.amountPerTransaction();
            boolean completed = latestOperation.maxTotalAmount() != null && fulfilled >= latestOperation.maxTotalAmount();
            TradeOperationRecord updatedOperation = new TradeOperationRecord(
                latestOperation.id(),
                latestOperation.caravanId(),
                latestOperation.type(),
                latestOperation.itemStack().clone(),
                latestOperation.amountPerTransaction(),
                latestOperation.priceCurrencyAmount(),
                latestOperation.maxTotalAmount(),
                fulfilled,
                latestOperation.reservedInventorySlot(),
                !completed,
                latestOperation.createdAt(),
                Instant.now()
            );

            inventoryService.saveInventory(caravan);
            TradeOperationMutationResult mutationResult = tradeOperationService.updateOperation(updatedOperation);
            if (!mutationResult.success()) {
                restoreContents(player.getInventory(), playerSnapshot);
                restoreContents(caravanInventory, caravanSnapshot);
                inventoryService.saveInventory(caravan);
                return failure("public-trade-transaction-failed", caravan, latestOperation);
            }

            return completed
                ? success("public-trade-order-completed", caravan, updatedOperation, remainingForBuy(updatedOperation))
                : success("public-trade-sell-success", caravan, updatedOperation, remainingForBuy(updatedOperation));
        } catch (Exception exception) {
            if (playerSnapshot != null && caravanSnapshot != null) {
                restoreContents(player.getInventory(), playerSnapshot);
                try {
                    Inventory caravanInventory = inventoryService.getInventory(caravan);
                    restoreContents(caravanInventory, caravanSnapshot);
                    inventoryService.saveInventory(caravan);
                } catch (Exception restoreException) {
                    exception.addSuppressed(restoreException);
                }
            }
            logger.log(Level.SEVERE, "Failed to complete public BUY transaction for caravan " + caravan.id() + '.', exception);
            return failure("public-trade-transaction-failed", caravan, operation);
        } finally {
            transactionLocks.remove(caravan.id());
        }
    }

    public void clearPlayerState(UUID playerId) {
    }

    private TradeOperationRecord validateCommon(CaravanRecord caravan, TradeOperationRecord operation, TradeOperationType type) {
        if (operation == null || !operation.caravanId().equals(caravan.id())) {
            return null;
        }
        TradeOperationRecord latest = tradeOperationService.getTradeOperationById(operation.id());
        if (latest == null || !latest.active() || latest.type() != type || !latest.caravanId().equals(caravan.id())) {
            return null;
        }
        return latest;
    }

    private boolean isOnShopPlot(UUID caravanId) {
        Location location = caravanEntityService.getCaravanLocation(caravanId);
        return location != null && townyIntegrationService.isShopPlot(location);
    }

    private int remainingForSell(CaravanRecord caravan, TradeOperationRecord operation) {
        try {
            Inventory inventory = inventoryService.getInventory(caravan);
            Integer reservedSlot = operation.reservedInventorySlot();
            if (reservedSlot == null || reservedSlot < 0 || reservedSlot >= inventory.getSize()) {
                return 0;
            }
            ItemStack item = inventory.getItem(reservedSlot);
            if (item == null || item.getType().isAir() || !item.isSimilar(operation.itemStack())) {
                return 0;
            }
            return item.getAmount();
        } catch (Exception exception) {
            return 0;
        }
    }

    private int remainingForBuy(TradeOperationRecord operation) {
        if (operation.maxTotalAmount() == null) {
            return 0;
        }
        return Math.max(0, operation.maxTotalAmount() - operation.fulfilledAmount());
    }

    private PublicTradeResult success(String messageKey, CaravanRecord caravan, TradeOperationRecord operation, int remaining) {
        return PublicTradeResult.success(messageKey, placeholders(caravan, operation, remaining));
    }

    private PublicTradeResult failure(String messageKey, CaravanRecord caravan, TradeOperationRecord operation) {
        int remaining = 0;
        if (operation != null) {
            remaining = operation.type() == TradeOperationType.SELL
                ? remainingForSell(caravan, operation)
                : remainingForBuy(operation);
        }
        return PublicTradeResult.failure(messageKey, placeholders(caravan, operation, remaining));
    }

    private Map<String, String> placeholders(CaravanRecord caravan, TradeOperationRecord operation, int remaining) {
        String itemName = operation == null || operation.itemStack() == null ? "Unknown" : operation.itemStack().getType().name();
        String amount = operation == null ? "0" : String.valueOf(operation.amountPerTransaction());
        String price = operation == null ? "0" : String.valueOf(operation.priceCurrencyAmount());
        return Map.of(
            "name", caravan.name(),
            "id", caravan.id().toString().substring(0, 8),
            "item", itemName,
            "amount", amount,
            "price", price,
            "remaining", String.valueOf(Math.max(0, remaining))
        );
    }

    private ItemStack[] cloneContents(Inventory inventory) {
        ItemStack[] original = inventory.getContents();
        ItemStack[] clone = new ItemStack[original.length];
        for (int index = 0; index < original.length; index++) {
            clone[index] = original[index] == null ? null : original[index].clone();
        }
        return clone;
    }

    private void restoreContents(Inventory inventory, ItemStack[] contents) {
        ItemStack[] restored = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            restored[index] = contents[index] == null ? null : contents[index].clone();
        }
        inventory.setContents(restored);
    }
}
