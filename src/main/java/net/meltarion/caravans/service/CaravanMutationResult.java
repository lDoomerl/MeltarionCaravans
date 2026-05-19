package net.meltarion.caravans.service;

import net.meltarion.caravans.model.CaravanRecord;

public record CaravanMutationResult(
    boolean success,
    CaravanRecord caravan,
    FailureReason failureReason
) {

    public static CaravanMutationResult success(CaravanRecord caravan) {
        return new CaravanMutationResult(true, caravan, null);
    }

    public static CaravanMutationResult failure(FailureReason failureReason) {
        return new CaravanMutationResult(false, null, failureReason);
    }

    public enum FailureReason {
        INVALID_NAME,
        DUPLICATE_NAME,
        STORAGE_ERROR
    }
}
