package net.meltarion.caravans.model;

import java.time.Instant;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public record TradeOperationRecord(
    UUID id,
    UUID caravanId,
    TradeOperationType type,
    ItemStack itemStack,
    int amountPerTransaction,
    int priceCurrencyAmount,
    Integer maxTotalAmount,
    int fulfilledAmount,
    Integer reservedInventorySlot,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
}
