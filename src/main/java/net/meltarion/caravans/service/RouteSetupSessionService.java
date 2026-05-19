package net.meltarion.caravans.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.meltarion.caravans.model.CaravanRecord;
import org.bukkit.entity.Player;

public final class RouteSetupSessionService {

    private final CaravanService caravanService;
    private final CaravanRouteService caravanRouteService;
    private final MessageService messageService;
    private final java.util.function.IntSupplier timeoutSecondsSupplier;
    private final java.util.function.IntSupplier minStopMinutesSupplier;
    private final java.util.function.IntSupplier maxStopMinutesSupplier;
    private final Map<UUID, RouteSetupSession> sessions = new ConcurrentHashMap<>();

    public RouteSetupSessionService(
        CaravanService caravanService,
        CaravanRouteService caravanRouteService,
        MessageService messageService,
        java.util.function.IntSupplier timeoutSecondsSupplier,
        java.util.function.IntSupplier minStopMinutesSupplier,
        java.util.function.IntSupplier maxStopMinutesSupplier
    ) {
        this.caravanService = caravanService;
        this.caravanRouteService = caravanRouteService;
        this.messageService = messageService;
        this.timeoutSecondsSupplier = timeoutSecondsSupplier;
        this.minStopMinutesSupplier = minStopMinutesSupplier;
        this.maxStopMinutesSupplier = maxStopMinutesSupplier;
    }

    public void beginStopDurationSession(Player player, CaravanRecord caravan, RoutePlotTarget target) {
        sessions.put(player.getUniqueId(), new RouteSetupSession(caravan.id(), target, Instant.now()));
        messageService.send(player, "route-duration-prompt", Map.of(
            "town", target.townName(),
            "timeout", String.valueOf(timeoutSecondsSupplier.getAsInt())
        ));
    }

    public boolean hasSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public void handleInput(Player player, String message) {
        RouteSetupSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        if (isExpired(session)) {
            sessions.remove(player.getUniqueId());
            messageService.send(player, "route-setup-timeout", Map.of("timeout", String.valueOf(timeoutSecondsSupplier.getAsInt())));
            return;
        }

        if ("cancel".equalsIgnoreCase(message.trim())) {
            sessions.remove(player.getUniqueId());
            messageService.send(player, "route-setup-cancelled");
            return;
        }

        CaravanRecord caravan = caravanService.getCaravan(session.caravanId());
        if (caravan == null) {
            sessions.remove(player.getUniqueId());
            messageService.send(player, "caravan-not-found");
            return;
        }

        Integer durationMinutes = parsePositiveInt(message);
        if (durationMinutes == null
            || durationMinutes < minStopMinutesSupplier.getAsInt()
            || durationMinutes > maxStopMinutesSupplier.getAsInt()) {
            messageService.send(player, "route-invalid-duration", Map.of(
                "duration", message.trim()
            ));
            return;
        }

        CaravanRouteResult result = caravanRouteService.addRouteStop(caravan, session.target(), durationMinutes * 60);
        sessions.remove(player.getUniqueId());
        if (!result.success()) {
            switch (result.failureReason()) {
                case DISABLED -> messageService.send(player, "movement-disabled");
                case MAX_STOPS_REACHED -> messageService.send(player, "route-max-stops-reached");
                case INVALID_DURATION -> messageService.send(player, "route-invalid-duration", Map.of("duration", String.valueOf(durationMinutes)));
                case NO_SHOP_PLOTS -> messageService.send(player, "route-no-shop-plots");
                default -> messageService.send(player, "storage-error");
            }
            return;
        }

        messageService.send(player, "route-stop-added", Map.of(
            "id", caravan.id().toString().substring(0, 8),
            "name", caravan.name(),
            "town", session.target().townName(),
            "duration", String.valueOf(durationMinutes),
            "order", String.valueOf(result.routeStop().stopOrder() + 1)
        ));
    }

    public void clearSession(UUID playerId) {
        sessions.remove(playerId);
    }

    private boolean isExpired(RouteSetupSession session) {
        return Duration.between(session.startedAt(), Instant.now()).getSeconds() > timeoutSecondsSupplier.getAsInt();
    }

    private Integer parsePositiveInt(String input) {
        try {
            int value = Integer.parseInt(input.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record RouteSetupSession(
        UUID caravanId,
        RoutePlotTarget target,
        Instant startedAt
    ) {
    }
}
