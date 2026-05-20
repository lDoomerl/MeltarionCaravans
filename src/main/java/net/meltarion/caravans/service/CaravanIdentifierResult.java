package net.meltarion.caravans.service;

import net.meltarion.caravans.model.CaravanRecord;

public record CaravanIdentifierResult(
    boolean success,
    CaravanRecord caravan,
    FailureReason failureReason,
    String input,
    String owner,
    Integer index
) {

    public static CaravanIdentifierResult success(CaravanRecord caravan, String input) {
        return new CaravanIdentifierResult(true, caravan, null, input, null, null);
    }

    public static CaravanIdentifierResult failure(FailureReason failureReason, String input) {
        return new CaravanIdentifierResult(false, null, failureReason, input, null, null);
    }

    public static CaravanIdentifierResult failure(FailureReason failureReason, String input, String owner, Integer index) {
        return new CaravanIdentifierResult(false, null, failureReason, input, owner, index);
    }

    public enum FailureReason {
        NOT_FOUND,
        AMBIGUOUS_SHORT_ID,
        INVALID_INDEX,
        AMBIGUOUS_NAME,
        NAME_NOT_FOUND,
        OWNER_NOT_FOUND,
        OWNER_HAS_NO_CARAVANS
    }
}
