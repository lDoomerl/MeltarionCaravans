package net.meltarion.caravans.storage;

import java.util.List;
import java.util.UUID;
import net.meltarion.caravans.model.TradeOperationRecord;

public interface TradeOperationStorage {

    List<TradeOperationRecord> loadAllTradeOperations() throws StorageException;

    void insertTradeOperation(TradeOperationRecord tradeOperation) throws StorageException;

    void updateTradeOperationActiveState(UUID tradeOperationId, boolean active, String updatedAt) throws StorageException;

    void deleteTradeOperation(UUID tradeOperationId) throws StorageException;

    void deleteTradeOperationsByCaravan(UUID caravanId) throws StorageException;
}
