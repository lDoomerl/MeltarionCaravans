package net.meltarion.caravans.storage;

import java.util.List;
import java.util.UUID;
import net.meltarion.caravans.model.CaravanRouteStopRecord;

public interface CaravanRouteStorage {

    List<CaravanRouteStopRecord> loadAllRouteStops() throws StorageException;

    void insertRouteStop(CaravanRouteStopRecord routeStop) throws StorageException;

    void updateRouteStop(CaravanRouteStopRecord routeStop) throws StorageException;

    void deleteRouteStop(UUID routeStopId) throws StorageException;

    void deleteRouteStopsByCaravan(UUID caravanId) throws StorageException;
}
