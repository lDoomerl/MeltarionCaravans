package net.meltarion.caravans.service;

public enum CaravanRouteFailureReason {
    DISABLED,
    TOWNY_MISSING,
    NO_STOPS,
    STORAGE_ERROR,
    INVALID_DURATION,
    MAX_STOPS_REACHED,
    NO_AVAILABLE_TOWNS,
    NO_SHOP_PLOTS,
    ALREADY_RUNNING,
    NOT_RUNNING,
    INVALID_STATE
}
