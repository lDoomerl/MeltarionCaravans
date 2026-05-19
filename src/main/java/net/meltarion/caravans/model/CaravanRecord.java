package net.meltarion.caravans.model;

import java.time.Instant;
import java.util.UUID;

public record CaravanRecord(
    UUID id,
    UUID ownerId,
    String name,
    Instant createdAt
) {
}
