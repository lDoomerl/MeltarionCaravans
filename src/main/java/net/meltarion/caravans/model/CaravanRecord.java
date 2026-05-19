package net.meltarion.caravans.model;

import java.time.Instant;
import java.util.UUID;

public record CaravanRecord(
    UUID id,
    UUID ownerId,
    String ownerName,
    String name,
    CaravanStatus status,
    int hp,
    int maxHp,
    String worldName,
    Double virtualX,
    Double virtualY,
    Double virtualZ,
    String targetWorldName,
    Double targetX,
    Double targetY,
    Double targetZ,
    Instant movementStartedAt,
    Instant movementUpdatedAt,
    double speedBlocksPerSecond,
    Integer etaSeconds,
    boolean physicalSpawned,
    String homeWorldName,
    Double homeX,
    Double homeY,
    Double homeZ,
    Integer currentRouteStopIndex,
    boolean routeRunning,
    Instant currentStopStartedAt,
    Instant currentStopEndsAt,
    boolean returningHomeAfterRoute,
    Instant createdAt,
    Instant updatedAt
) {

    public boolean hasVirtualPosition() {
        return worldName != null && virtualX != null && virtualY != null && virtualZ != null;
    }

    public boolean hasTargetPosition() {
        return targetWorldName != null && targetX != null && targetY != null && targetZ != null;
    }

    public boolean hasHomePosition() {
        return homeWorldName != null && homeX != null && homeY != null && homeZ != null;
    }

    public CaravanRecord withName(String updatedName, Instant newUpdatedAt) {
        return new CaravanRecord(
            id,
            ownerId,
            ownerName,
            updatedName,
            status,
            hp,
            maxHp,
            worldName,
            virtualX,
            virtualY,
            virtualZ,
            targetWorldName,
            targetX,
            targetY,
            targetZ,
            movementStartedAt,
            movementUpdatedAt,
            speedBlocksPerSecond,
            etaSeconds,
            physicalSpawned,
            homeWorldName,
            homeX,
            homeY,
            homeZ,
            currentRouteStopIndex,
            routeRunning,
            currentStopStartedAt,
            currentStopEndsAt,
            returningHomeAfterRoute,
            createdAt,
            newUpdatedAt
        );
    }

    public CaravanRecord withHealthAndStatus(int updatedHp, CaravanStatus updatedStatus, Instant newUpdatedAt) {
        return new CaravanRecord(
            id,
            ownerId,
            ownerName,
            name,
            updatedStatus,
            updatedHp,
            maxHp,
            worldName,
            virtualX,
            virtualY,
            virtualZ,
            targetWorldName,
            targetX,
            targetY,
            targetZ,
            movementStartedAt,
            movementUpdatedAt,
            speedBlocksPerSecond,
            etaSeconds,
            physicalSpawned,
            homeWorldName,
            homeX,
            homeY,
            homeZ,
            currentRouteStopIndex,
            routeRunning,
            currentStopStartedAt,
            currentStopEndsAt,
            returningHomeAfterRoute,
            createdAt,
            newUpdatedAt
        );
    }

    public CaravanRecord withMovement(
        CaravanStatus updatedStatus,
        String updatedWorldName,
        Double updatedVirtualX,
        Double updatedVirtualY,
        Double updatedVirtualZ,
        String updatedTargetWorldName,
        Double updatedTargetX,
        Double updatedTargetY,
        Double updatedTargetZ,
        Instant updatedMovementStartedAt,
        Instant updatedMovementUpdatedAt,
        double updatedSpeedBlocksPerSecond,
        Integer updatedEtaSeconds,
        boolean updatedPhysicalSpawned,
        Instant newUpdatedAt
    ) {
        return new CaravanRecord(
            id,
            ownerId,
            ownerName,
            name,
            updatedStatus,
            hp,
            maxHp,
            updatedWorldName,
            updatedVirtualX,
            updatedVirtualY,
            updatedVirtualZ,
            updatedTargetWorldName,
            updatedTargetX,
            updatedTargetY,
            updatedTargetZ,
            updatedMovementStartedAt,
            updatedMovementUpdatedAt,
            updatedSpeedBlocksPerSecond,
            updatedEtaSeconds,
            updatedPhysicalSpawned,
            homeWorldName,
            homeX,
            homeY,
            homeZ,
            currentRouteStopIndex,
            routeRunning,
            currentStopStartedAt,
            currentStopEndsAt,
            returningHomeAfterRoute,
            createdAt,
            newUpdatedAt
        );
    }

    public CaravanRecord withHomePosition(String updatedHomeWorldName, Double updatedHomeX, Double updatedHomeY, Double updatedHomeZ, Instant newUpdatedAt) {
        return new CaravanRecord(
            id,
            ownerId,
            ownerName,
            name,
            status,
            hp,
            maxHp,
            worldName,
            virtualX,
            virtualY,
            virtualZ,
            targetWorldName,
            targetX,
            targetY,
            targetZ,
            movementStartedAt,
            movementUpdatedAt,
            speedBlocksPerSecond,
            etaSeconds,
            physicalSpawned,
            updatedHomeWorldName,
            updatedHomeX,
            updatedHomeY,
            updatedHomeZ,
            currentRouteStopIndex,
            routeRunning,
            currentStopStartedAt,
            currentStopEndsAt,
            returningHomeAfterRoute,
            createdAt,
            newUpdatedAt
        );
    }

    public CaravanRecord withPhysicalSpawned(boolean updatedPhysicalSpawned, Instant newUpdatedAt) {
        return new CaravanRecord(
            id,
            ownerId,
            ownerName,
            name,
            status,
            hp,
            maxHp,
            worldName,
            virtualX,
            virtualY,
            virtualZ,
            targetWorldName,
            targetX,
            targetY,
            targetZ,
            movementStartedAt,
            movementUpdatedAt,
            speedBlocksPerSecond,
            etaSeconds,
            updatedPhysicalSpawned,
            homeWorldName,
            homeX,
            homeY,
            homeZ,
            currentRouteStopIndex,
            routeRunning,
            currentStopStartedAt,
            currentStopEndsAt,
            returningHomeAfterRoute,
            createdAt,
            newUpdatedAt
        );
    }

    public CaravanRecord withRouteState(
        Integer updatedCurrentRouteStopIndex,
        boolean updatedRouteRunning,
        Instant updatedCurrentStopStartedAt,
        Instant updatedCurrentStopEndsAt,
        boolean updatedReturningHomeAfterRoute,
        Instant newUpdatedAt
    ) {
        return new CaravanRecord(
            id,
            ownerId,
            ownerName,
            name,
            status,
            hp,
            maxHp,
            worldName,
            virtualX,
            virtualY,
            virtualZ,
            targetWorldName,
            targetX,
            targetY,
            targetZ,
            movementStartedAt,
            movementUpdatedAt,
            speedBlocksPerSecond,
            etaSeconds,
            physicalSpawned,
            homeWorldName,
            homeX,
            homeY,
            homeZ,
            updatedCurrentRouteStopIndex,
            updatedRouteRunning,
            updatedCurrentStopStartedAt,
            updatedCurrentStopEndsAt,
            updatedReturningHomeAfterRoute,
            createdAt,
            newUpdatedAt
        );
    }
}
