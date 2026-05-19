package net.meltarion.caravans.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.config.GuiConfigManager;
import net.meltarion.caravans.inventory.CaravanTradeManagementHolder;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.model.TradeOperationRecord;
import net.meltarion.caravans.model.TradeOperationType;
import net.meltarion.caravans.storage.StorageException;
import net.meltarion.caravans.storage.TradeOperationStorage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class TradeOperationService {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private final ConfigManager configManager;
    private final GuiConfigManager guiConfigManager;
    private final CaravanInventoryService caravanInventoryService;
    private final TradeOperationStorage storage;
    private final Logger logger;
    private final Map<UUID, List<TradeOperationRecord>> operationsByCaravan = new ConcurrentHashMap<>();

    public TradeOperationService(
        ConfigManager configManager,
        GuiConfigManager guiConfigManager,
        CaravanInventoryService caravanInventoryService,
        TradeOperationStorage storage,
        Logger logger
    ) {
        this.configManager = configManager;
        this.guiConfigManager = guiConfigManager;
        this.caravanInventoryService = caravanInventoryService;
        this.storage = storage;
        this.logger = logger;
    }

    public void loadTradeOperations() throws StorageException {
        operationsByCaravan.clear();
        for (TradeOperationRecord tradeOperation : storage.loadAllTradeOperations()) {
            operationsByCaravan.computeIfAbsent(tradeOperation.caravanId(), ignored -> new ArrayList<>()).add(tradeOperation);
        }
        operationsByCaravan.values().forEach(this::sortOperations);
    }

    public synchronized TradeOperationCreateResult createSellOperation(CaravanRecord caravan, int slot, int priceCurrencyAmount) {
        if (priceCurrencyAmount <= 0) {
            return TradeOperationCreateResult.failure(TradeOperationCreateResult.FailureReason.INVALID_PRICE);
        }
        if (slot < 0 || slot >= configManager.getCaravanInventorySize()) {
            return TradeOperationCreateResult.failure(TradeOperationCreateResult.FailureReason.INVALID_AMOUNT);
        }
        if (hasActiveSellOperationForSlot(caravan.id(), slot)) {
            return TradeOperationCreateResult.failure(TradeOperationCreateResult.FailureReason.DUPLICATE_SLOT);
        }

        ItemStack itemStack;
        try {
            itemStack = caravanInventoryService.getItemInSlot(caravan, slot);
        } catch (StorageException exception) {
            logger.log(Level.SEVERE, "Failed to inspect caravan inventory slot " + slot + " for " + caravan.id() + '.', exception);
            return TradeOperationCreateResult.failure(TradeOperationCreateResult.FailureReason.STORAGE_ERROR);
        }

        if (itemStack == null || itemStack.getType().isAir()) {
            return TradeOperationCreateResult.failure(TradeOperationCreateResult.FailureReason.EMPTY_SLOT);
        }

        Instant now = Instant.now();
        TradeOperationRecord tradeOperation = new TradeOperationRecord(
            UUID.randomUUID(),
            caravan.id(),
            TradeOperationType.SELL,
            itemStack.clone(),
            itemStack.getAmount(),
            priceCurrencyAmount,
            itemStack.getAmount(),
            0,
            slot,
            true,
            now,
            now
        );

        return persistTradeOperation(tradeOperation);
    }

    public synchronized TradeOperationCreateResult createBuyOperation(
        CaravanRecord caravan,
        Material material,
        int amountPerTransaction,
        int priceCurrencyAmount,
        int maxTotalAmount
    ) {
        if (material == null || material.isAir() || !material.isItem()) {
            return TradeOperationCreateResult.failure(TradeOperationCreateResult.FailureReason.INVALID_MATERIAL);
        }
        if (amountPerTransaction < 1 || amountPerTransaction > 64 || maxTotalAmount < amountPerTransaction) {
            return TradeOperationCreateResult.failure(TradeOperationCreateResult.FailureReason.INVALID_AMOUNT);
        }
        if (priceCurrencyAmount <= 0 || maxTotalAmount <= 0) {
            return TradeOperationCreateResult.failure(
                priceCurrencyAmount <= 0
                    ? TradeOperationCreateResult.FailureReason.INVALID_PRICE
                    : TradeOperationCreateResult.FailureReason.INVALID_AMOUNT
            );
        }

        Instant now = Instant.now();
        TradeOperationRecord tradeOperation = new TradeOperationRecord(
            UUID.randomUUID(),
            caravan.id(),
            TradeOperationType.BUY,
            new ItemStack(material, 1),
            amountPerTransaction,
            priceCurrencyAmount,
            maxTotalAmount,
            0,
            null,
            true,
            now,
            now
        );

        return persistTradeOperation(tradeOperation);
    }

    public synchronized List<TradeOperationRecord> getOperations(UUID caravanId) {
        return List.copyOf(operationsByCaravan.getOrDefault(caravanId, List.of()));
    }

    public synchronized List<TradeOperationRecord> getActiveOperations(UUID caravanId) {
        return operationsByCaravan.getOrDefault(caravanId, List.of()).stream()
            .filter(TradeOperationRecord::active)
            .sorted(Comparator.comparing(TradeOperationRecord::createdAt))
            .toList();
    }

    public synchronized List<TradeOperationRecord> getActiveOperations(UUID caravanId, TradeOperationType type) {
        return operationsByCaravan.getOrDefault(caravanId, List.of()).stream()
            .filter(TradeOperationRecord::active)
            .filter(operation -> operation.type() == type)
            .sorted(Comparator.comparing(TradeOperationRecord::createdAt))
            .toList();
    }

    public synchronized boolean hasActiveSellOperationForSlot(UUID caravanId, int slot) {
        return operationsByCaravan.getOrDefault(caravanId, List.of()).stream()
            .anyMatch(operation -> operation.active()
                && operation.type() == TradeOperationType.SELL
                && operation.reservedInventorySlot() != null
                && operation.reservedInventorySlot() == slot);
    }

    public synchronized TradeOperationRecord getTradeOperation(UUID caravanId, int displayIndex) {
        List<TradeOperationRecord> operations = getOperations(caravanId);
        if (displayIndex < 0 || displayIndex >= operations.size()) {
            return null;
        }
        return operations.get(displayIndex);
    }

    public synchronized TradeOperationRecord getTradeOperationById(UUID tradeOperationId) {
        TradeOperationRecord operation = findById(tradeOperationId);
        return operation == null ? null : copyOperation(operation);
    }

    public synchronized void openTradeManagementInventory(Player player, CaravanRecord caravan) {
        CaravanTradeManagementHolder holder = new CaravanTradeManagementHolder(caravan.id(), caravan.name());
        Inventory inventory = Bukkit.createInventory(
            holder,
            54,
            LEGACY_SERIALIZER.deserialize(guiConfigManager.getString("trade-management.title", "&8Caravan Trades: &6%name%").replace("%name%", caravan.name()))
        );
        holder.setInventory(inventory);
        populateTradeManagementInventory(inventory, caravan.id());
        player.openInventory(inventory);
    }

    public synchronized void populateTradeManagementInventory(Inventory inventory, UUID caravanId) {
        inventory.clear();

        List<TradeOperationRecord> operations = getOperations(caravanId);
        for (int slot = 0; slot < Math.min(inventory.getSize(), operations.size()); slot++) {
            inventory.setItem(slot, createTradeDisplayItem(operations.get(slot)));
        }
    }

    public synchronized TradeOperationMutationResult toggleActive(UUID tradeOperationId) {
        TradeOperationRecord existing = findById(tradeOperationId);
        if (existing == null) {
            return TradeOperationMutationResult.failure(TradeOperationMutationResult.FailureReason.TRADE_NOT_FOUND);
        }

        Instant updatedAt = Instant.now();
        TradeOperationRecord updated = new TradeOperationRecord(
            existing.id(),
            existing.caravanId(),
            existing.type(),
            existing.itemStack().clone(),
            existing.amountPerTransaction(),
            existing.priceCurrencyAmount(),
            existing.maxTotalAmount(),
            existing.fulfilledAmount(),
            existing.reservedInventorySlot(),
            !existing.active(),
            existing.createdAt(),
            updatedAt
        );

        try {
            storage.updateTradeOperationActiveState(updated.id(), updated.active(), updatedAt.toString());
        } catch (StorageException exception) {
            logger.log(Level.SEVERE, "Failed to toggle trade operation " + tradeOperationId + '.', exception);
            return TradeOperationMutationResult.failure(TradeOperationMutationResult.FailureReason.STORAGE_ERROR);
        }

        replaceTradeOperation(updated);
        return TradeOperationMutationResult.success(updated);
    }

    public synchronized TradeOperationMutationResult deleteOperation(UUID tradeOperationId) {
        TradeOperationRecord existing = findById(tradeOperationId);
        if (existing == null) {
            return TradeOperationMutationResult.failure(TradeOperationMutationResult.FailureReason.TRADE_NOT_FOUND);
        }

        try {
            storage.deleteTradeOperation(tradeOperationId);
        } catch (StorageException exception) {
            logger.log(Level.SEVERE, "Failed to delete trade operation " + tradeOperationId + '.', exception);
            return TradeOperationMutationResult.failure(TradeOperationMutationResult.FailureReason.STORAGE_ERROR);
        }

        removeTradeOperation(existing);
        return TradeOperationMutationResult.success(existing);
    }

    public synchronized TradeOperationMutationResult updateOperation(TradeOperationRecord updated) {
        TradeOperationRecord existing = findById(updated.id());
        if (existing == null) {
            return TradeOperationMutationResult.failure(TradeOperationMutationResult.FailureReason.TRADE_NOT_FOUND);
        }

        try {
            storage.updateTradeOperation(updated);
        } catch (StorageException exception) {
            logger.log(Level.SEVERE, "Failed to update trade operation " + updated.id() + '.', exception);
            return TradeOperationMutationResult.failure(TradeOperationMutationResult.FailureReason.STORAGE_ERROR);
        }

        replaceTradeOperation(updated);
        return TradeOperationMutationResult.success(updated);
    }

    public synchronized void discardCaravanState(UUID caravanId) {
        operationsByCaravan.remove(caravanId);
    }

    public synchronized boolean isReservedSlot(UUID caravanId, int slot) {
        return hasActiveSellOperationForSlot(caravanId, slot);
    }

    private TradeOperationCreateResult persistTradeOperation(TradeOperationRecord tradeOperation) {
        try {
            storage.insertTradeOperation(tradeOperation);
        } catch (StorageException exception) {
            logger.log(Level.SEVERE, "Failed to persist trade operation " + tradeOperation.id() + '.', exception);
            return TradeOperationCreateResult.failure(TradeOperationCreateResult.FailureReason.STORAGE_ERROR);
        }

        operationsByCaravan.computeIfAbsent(tradeOperation.caravanId(), ignored -> new ArrayList<>()).add(tradeOperation);
        sortOperations(operationsByCaravan.get(tradeOperation.caravanId()));
        return TradeOperationCreateResult.success(tradeOperation);
    }

    private TradeOperationRecord findById(UUID tradeOperationId) {
        return operationsByCaravan.values().stream()
            .flatMap(List::stream)
            .filter(operation -> operation.id().equals(tradeOperationId))
            .findFirst()
            .orElse(null);
    }

    private TradeOperationRecord copyOperation(TradeOperationRecord operation) {
        return new TradeOperationRecord(
            operation.id(),
            operation.caravanId(),
            operation.type(),
            operation.itemStack().clone(),
            operation.amountPerTransaction(),
            operation.priceCurrencyAmount(),
            operation.maxTotalAmount(),
            operation.fulfilledAmount(),
            operation.reservedInventorySlot(),
            operation.active(),
            operation.createdAt(),
            operation.updatedAt()
        );
    }

    private void replaceTradeOperation(TradeOperationRecord updated) {
        List<TradeOperationRecord> operations = operationsByCaravan.get(updated.caravanId());
        if (operations == null) {
            return;
        }

        for (int index = 0; index < operations.size(); index++) {
            if (operations.get(index).id().equals(updated.id())) {
                operations.set(index, updated);
                break;
            }
        }
        sortOperations(operations);
    }

    private void removeTradeOperation(TradeOperationRecord existing) {
        List<TradeOperationRecord> operations = operationsByCaravan.get(existing.caravanId());
        if (operations == null) {
            return;
        }

        operations.removeIf(operation -> operation.id().equals(existing.id()));
        if (operations.isEmpty()) {
            operationsByCaravan.remove(existing.caravanId());
        }
    }

    private void sortOperations(List<TradeOperationRecord> operations) {
        operations.sort(Comparator.comparing(TradeOperationRecord::createdAt));
    }

    private ItemStack createTradeDisplayItem(TradeOperationRecord tradeOperation) {
        ItemStack displayItem = tradeOperation.itemStack().clone();
        ItemMeta itemMeta = displayItem.getItemMeta();
        if (itemMeta == null) {
            return displayItem;
        }

        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY_SERIALIZER.deserialize("&7Type: &f" + tradeOperation.type().name()));
        if (tradeOperation.type() == TradeOperationType.SELL) {
            lore.add(LEGACY_SERIALIZER.deserialize("&7Amount: &f" + tradeOperation.amountPerTransaction()));
            lore.add(LEGACY_SERIALIZER.deserialize("&7Price: &f" + tradeOperation.priceCurrencyAmount() + " " + configManager.getCurrencyItem().name()));
            lore.add(LEGACY_SERIALIZER.deserialize("&7Slot: &f" + (tradeOperation.reservedInventorySlot() == null ? "-" : tradeOperation.reservedInventorySlot())));
            lore.add(LEGACY_SERIALIZER.deserialize("&7Active: &f" + (tradeOperation.active() ? "yes" : "no")));
        } else {
            lore.add(LEGACY_SERIALIZER.deserialize("&7Amount per transaction: &f" + tradeOperation.amountPerTransaction()));
            lore.add(LEGACY_SERIALIZER.deserialize("&7Price: &f" + tradeOperation.priceCurrencyAmount() + " " + configManager.getCurrencyItem().name()));
            lore.add(LEGACY_SERIALIZER.deserialize("&7Max total: &f" + (tradeOperation.maxTotalAmount() == null ? "-" : tradeOperation.maxTotalAmount())));
            lore.add(LEGACY_SERIALIZER.deserialize("&7Fulfilled: &f" + tradeOperation.fulfilledAmount()));
            lore.add(LEGACY_SERIALIZER.deserialize("&7Active: &f" + (tradeOperation.active() ? "yes" : "no")));
        }

        itemMeta.lore(lore);
        displayItem.setItemMeta(itemMeta);
        return displayItem;
    }
}
