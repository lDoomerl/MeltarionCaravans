package net.meltarion.caravans.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.model.TradeOperationType;
import net.meltarion.caravans.service.CaravanRouteService;
import net.meltarion.caravans.service.CaravanService;
import net.meltarion.caravans.service.TradeOperationService;
import org.bukkit.Server;
import org.bukkit.entity.Player;

public final class MeltarionCaravansApiImpl implements MeltarionCaravansApi {

    private final Server server;
    private final ConfigManager configManager;
    private final CaravanService caravanService;
    private final CaravanRouteService caravanRouteService;
    private final TradeOperationService tradeOperationService;
    private final BooleanSupplier readySupplier;

    public MeltarionCaravansApiImpl(
        Server server,
        ConfigManager configManager,
        CaravanService caravanService,
        CaravanRouteService caravanRouteService,
        TradeOperationService tradeOperationService,
        BooleanSupplier readySupplier
    ) {
        this.server = server;
        this.configManager = configManager;
        this.caravanService = caravanService;
        this.caravanRouteService = caravanRouteService;
        this.tradeOperationService = tradeOperationService;
        this.readySupplier = readySupplier;
    }

    @Override
    public List<CaravanSummary> getCaravansByOwner(UUID ownerUuid) {
        if (ownerUuid == null) {
            return List.of();
        }
        return caravanService.getCaravans(ownerUuid).stream()
            .map(this::toSummary)
            .toList();
    }

    @Override
    public Optional<CaravanSummary> getCaravan(UUID caravanId) {
        if (caravanId == null) {
            return Optional.empty();
        }
        CaravanRecord caravan = caravanService.getCaravan(caravanId);
        return caravan == null ? Optional.empty() : Optional.of(toSummary(caravan));
    }

    @Override
    public int getCaravanCount(UUID ownerUuid) {
        return getCaravansByOwner(ownerUuid).size();
    }

    @Override
    public int getCaravanLimit(UUID ownerUuid) {
        if (ownerUuid == null) {
            return 0;
        }

        Player player = server.getPlayer(ownerUuid);
        if (player != null) {
            return caravanService.getCaravanLimit(player);
        }
        return configManager.getDefaultCaravanLimit();
    }

    @Override
    public boolean isCaravanPluginReady() {
        return readySupplier.getAsBoolean();
    }

    private CaravanSummary toSummary(CaravanRecord caravan) {
        return new CaravanSummary(
            caravan.id(),
            caravanService.getShortId(caravan),
            caravan.ownerId(),
            caravan.ownerName(),
            caravan.name(),
            caravan.status().name(),
            caravan.hp(),
            caravan.maxHp(),
            caravan.worldName(),
            caravan.virtualX() == null ? 0.0D : caravan.virtualX(),
            caravan.virtualY() == null ? 0.0D : caravan.virtualY(),
            caravan.virtualZ() == null ? 0.0D : caravan.virtualZ(),
            caravan.targetWorldName(),
            caravan.targetX(),
            caravan.targetY(),
            caravan.targetZ(),
            caravan.etaSeconds(),
            caravan.routeRunning(),
            caravan.currentRouteStopIndex() == null ? -1 : caravan.currentRouteStopIndex(),
            caravanRouteService.getRouteStops(caravan.id()).size(),
            caravan.routeLoopEnabled(),
            caravan.physicalSpawned(),
            tradeOperationService.getActiveOperations(caravan.id(), TradeOperationType.SELL).size(),
            tradeOperationService.getActiveOperations(caravan.id(), TradeOperationType.BUY).size(),
            caravan.updatedAt().toString()
        );
    }
}
