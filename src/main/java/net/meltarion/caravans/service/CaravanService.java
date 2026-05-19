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

    CaravanLookupResult findCaravanForOwner(UUID ownerId, String reference);

    CaravanLookupResult findCaravan(String reference);

    CaravanRecord getCaravan(UUID caravanId);

    CaravanMutationResult renameCaravan(CaravanRecord caravan, String requestedName);

    CaravanMutationResult deleteCaravan(CaravanRecord caravan);

    boolean caravanExists(UUID caravanId);

    int getCaravanLimit(Player player);

    String getShortId(CaravanRecord caravan);
}
