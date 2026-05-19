package net.meltarion.caravans.service;

public record RoutePlotTarget(
    String townName,
    String worldName,
    double x,
    double y,
    double z
) {
}
