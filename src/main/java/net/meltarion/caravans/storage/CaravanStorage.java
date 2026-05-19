package net.meltarion.caravans.storage;

import java.util.List;
import net.meltarion.caravans.model.CaravanRecord;

public interface CaravanStorage extends AutoCloseable {

    void initialize() throws StorageException;

    List<CaravanRecord> loadAllCaravans() throws StorageException;

    void insertCaravan(CaravanRecord caravan) throws StorageException;

    @Override
    void close() throws StorageException;
}
