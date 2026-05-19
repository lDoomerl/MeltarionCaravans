package net.meltarion.caravans.service;

import java.util.List;
import java.util.UUID;
import net.meltarion.caravans.model.CaravanRecord;
import org.bukkit.entity.Player;

public interface CaravanService {

    CaravanCreationResult createDefaultCaravan(Player owner);

    CaravanCreationResult createNamedCaravan(Player owner, String requestedName);

    List<CaravanRecord> getCaravans(UUID ownerId);

    List<CaravanRecord> getCaravansByOwnerName(String ownerName);

    List<CaravanRecord> getAllCaravans();

    CaravanLookupResult findCaravanForOwner(UUID ownerId, String reference);

    CaravanLookupResult findCaravan(String reference);

    CaravanRecord getCaravan(UUID caravanId);

    CaravanMutationResult renameCaravan(CaravanRecord caravan, String requestedName);

    CaravanMutationResult deleteCaravan(CaravanRecord caravan);

    CaravanMutationResult updateCachedCaravanRecord(CaravanRecord caravan);

    CaravanMutationResult updateCaravanRecord(CaravanRecord caravan);

    CaravanMutationResult updateCaravanHealthAndStatus(CaravanRecord caravan, int hp, net.meltarion.caravans.model.CaravanStatus status);

    boolean caravanExists(UUID caravanId);

    int getCaravanLimit(Player player);

    String getShortId(CaravanRecord caravan);
}
