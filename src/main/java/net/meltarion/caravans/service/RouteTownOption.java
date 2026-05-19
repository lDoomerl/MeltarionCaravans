package net.meltarion.caravans.service;

public record RouteTownOption(
    String townName,
    TownRelation relation,
    int shopPlotCount
) {
}
