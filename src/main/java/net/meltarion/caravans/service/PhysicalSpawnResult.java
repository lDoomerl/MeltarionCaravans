package net.meltarion.caravans.service;

public record PhysicalSpawnResult(
    boolean success,
    PhysicalSpawnFailureReason failureReason
) {

    public static PhysicalSpawnResult successful() {
        return new PhysicalSpawnResult(true, null);
    }

    public static PhysicalSpawnResult failure(PhysicalSpawnFailureReason failureReason) {
        return new PhysicalSpawnResult(false, failureReason);
    }
}
