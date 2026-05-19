package net.meltarion.caravans.service;

import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.model.CaravanRouteStopRecord;

public record CaravanRouteResult(
    boolean success,
    CaravanRecord caravan,
    CaravanRouteStopRecord routeStop,
    CaravanRouteFailureReason failureReason
) {

    public static CaravanRouteResult success(CaravanRecord caravan) {
        return new CaravanRouteResult(true, caravan, null, null);
    }

    public static CaravanRouteResult success(CaravanRecord caravan, CaravanRouteStopRecord routeStop) {
        return new CaravanRouteResult(true, caravan, routeStop, null);
    }

    public static CaravanRouteResult failure(CaravanRouteFailureReason failureReason) {
        return new CaravanRouteResult(false, null, null, failureReason);
    }
}
