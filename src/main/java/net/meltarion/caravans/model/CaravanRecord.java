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
    Instant createdAt,
    Instant updatedAt
) {
}
