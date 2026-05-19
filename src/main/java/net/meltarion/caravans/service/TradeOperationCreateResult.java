package net.meltarion.caravans.service;

import net.meltarion.caravans.model.TradeOperationRecord;

public record TradeOperationCreateResult(
    boolean success,
    TradeOperationRecord tradeOperation,
    FailureReason failureReason
) {

    public static TradeOperationCreateResult success(TradeOperationRecord tradeOperation) {
        return new TradeOperationCreateResult(true, tradeOperation, null);
    }

    public static TradeOperationCreateResult failure(FailureReason failureReason) {
        return new TradeOperationCreateResult(false, null, failureReason);
    }

    public enum FailureReason {
        INVALID_PRICE,
        INVALID_AMOUNT,
        INVALID_MATERIAL,
        EMPTY_SLOT,
        DUPLICATE_SLOT,
        TRADE_NOT_FOUND,
        STORAGE_ERROR
    }
}
