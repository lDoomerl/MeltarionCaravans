package net.meltarion.caravans.service;

import net.meltarion.caravans.model.CaravanRecord;

public record CaravanMovementResult(
    boolean success,
    CaravanRecord caravan,
    FailureReason failureReason
) {

    public static CaravanMovementResult success(CaravanRecord caravan) {
        return new CaravanMovementResult(true, caravan, null);
    }

    public static CaravanMovementResult failure(FailureReason failureReason) {
        return new CaravanMovementResult(false, null, failureReason);
    }

    public enum FailureReason {
        DISABLED,
        NO_POSITION,
        INVALID_TARGET,
        HOME_MISSING,
        STORAGE_ERROR
    }
}
