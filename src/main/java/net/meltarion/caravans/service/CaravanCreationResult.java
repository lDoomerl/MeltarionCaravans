package net.meltarion.caravans.service;

import net.meltarion.caravans.model.CaravanRecord;

public record CaravanCreationResult(
    boolean success,
    CaravanRecord caravan,
    FailureReason failureReason,
    int currentLimit
) {

    public static CaravanCreationResult success(CaravanRecord caravan, int currentLimit) {
        return new CaravanCreationResult(true, caravan, null, currentLimit);
    }

    public static CaravanCreationResult failure(FailureReason failureReason, int currentLimit) {
        return new CaravanCreationResult(false, null, failureReason, currentLimit);
    }

    public enum FailureReason {
        INVALID_NAME,
        DUPLICATE_NAME,
        LIMIT_REACHED
    }
}
