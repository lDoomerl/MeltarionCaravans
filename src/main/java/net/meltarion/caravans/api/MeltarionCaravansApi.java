package net.meltarion.caravans.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeltarionCaravansApi {

    List<CaravanSummary> getCaravansByOwner(UUID ownerUuid);

    Optional<CaravanSummary> getCaravan(UUID caravanId);

    int getCaravanCount(UUID ownerUuid);

    int getCaravanLimit(UUID ownerUuid);

    boolean isCaravanPluginReady();
}
