package net.meltarion.caravans.storage;

import java.util.List;
import java.util.UUID;
import net.meltarion.caravans.model.CaravanRecord;

public interface CaravanStorage extends AutoCloseable {

    void initialize() throws StorageException;

    List<CaravanRecord> loadAllCaravans() throws StorageException;

    void insertCaravan(CaravanRecord caravan) throws StorageException;

    void renameCaravan(UUID caravanId, String name, String updatedAt) throws StorageException;

    void deleteCaravan(UUID caravanId) throws StorageException;

    void deleteCaravanData(UUID caravanId) throws StorageException;

    void updateCaravanState(UUID caravanId, String status, int hp, String updatedAt) throws StorageException;

    @Override
    void close() throws StorageException;
}
