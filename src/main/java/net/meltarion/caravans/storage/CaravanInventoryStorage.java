package net.meltarion.caravans.storage;

import java.util.UUID;

public interface CaravanInventoryStorage {

    String loadInventoryContents(UUID caravanId) throws StorageException;

    void saveInventoryContents(UUID caravanId, String serializedContents, String updatedAt) throws StorageException;

    void deleteInventoryContents(UUID caravanId) throws StorageException;
}
