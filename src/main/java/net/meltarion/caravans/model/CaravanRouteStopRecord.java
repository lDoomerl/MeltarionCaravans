package net.meltarion.caravans.model;

import java.time.Instant;
import java.util.UUID;

public record CaravanRouteStopRecord(
    UUID id,
    UUID caravanId,
    int stopOrder,
    String townName,
    String worldName,
    double x,
    double y,
    double z,
    int stopDurationSeconds,
    Instant createdAt,
    Instant updatedAt
) {

    public CaravanRouteStopRecord withStopOrder(int updatedStopOrder, Instant newUpdatedAt) {
        return new CaravanRouteStopRecord(
            id,
            caravanId,
            updatedStopOrder,
            townName,
            worldName,
            x,
            y,
            z,
            stopDurationSeconds,
            createdAt,
            newUpdatedAt
        );
    }
}
