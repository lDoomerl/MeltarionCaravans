package net.meltarion.caravans.service;

import java.util.List;
import java.util.UUID;
import net.meltarion.caravans.model.CaravanRecord;
import org.bukkit.entity.Player;

public interface CaravanService {

    CaravanCreationResult createCaravan(Player owner, String requestedName);

    List<CaravanRecord> getCaravans(UUID ownerId);

    int getCaravanLimit(Player player);
}
