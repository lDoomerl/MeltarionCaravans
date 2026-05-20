package net.meltarion.caravans.api;

import java.util.UUID;

public record CaravanSummary(
    UUID id,
    String shortId,
    UUID ownerUuid,
    String ownerName,
    String name,
    String status,
    double hp,
    double maxHp,
    String worldName,
    double virtualX,
    double virtualY,
    double virtualZ,
    String targetWorldName,
    Double targetX,
    Double targetY,
    Double targetZ,
    Integer etaSeconds,
    boolean routeRunning,
    int currentRouteStopIndex,
    int totalRouteStops,
    boolean routeLoopEnabled,
    boolean physicalSpawned,
    int activeSellOffers,
    int activeBuyOrders,
    String updatedAt
) {
}
