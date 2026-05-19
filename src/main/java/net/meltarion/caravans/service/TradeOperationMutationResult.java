package net.meltarion.caravans.service;

import net.meltarion.caravans.model.TradeOperationRecord;

public record TradeOperationMutationResult(
    boolean success,
    TradeOperationRecord tradeOperation,
    FailureReason failureReason
) {

    public static TradeOperationMutationResult success(TradeOperationRecord tradeOperation) {
        return new TradeOperationMutationResult(true, tradeOperation, null);
    }

    public static TradeOperationMutationResult failure(FailureReason failureReason) {
        return new TradeOperationMutationResult(false, null, failureReason);
    }

    public enum FailureReason {
        TRADE_NOT_FOUND,
        STORAGE_ERROR
    }
}
