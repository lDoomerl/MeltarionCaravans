package net.meltarion.caravans.service;

import net.meltarion.caravans.model.CaravanRecord;

public record CaravanLookupResult(
    boolean success,
    CaravanRecord caravan,
    FailureReason failureReason
) {

    public static CaravanLookupResult success(CaravanRecord caravan) {
        return new CaravanLookupResult(true, caravan, null);
    }

    public static CaravanLookupResult failure(FailureReason failureReason) {
        return new CaravanLookupResult(false, null, failureReason);
    }

    public enum FailureReason {
        NOT_FOUND,
        AMBIGUOUS
    }
}
