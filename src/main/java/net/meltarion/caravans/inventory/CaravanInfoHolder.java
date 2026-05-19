package net.meltarion.caravans.inventory;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CaravanInfoHolder implements InventoryHolder {

    private final UUID caravanId;
    private Inventory inventory;

    public CaravanInfoHolder(UUID caravanId) {
        this.caravanId = caravanId;
    }

    public UUID getCaravanId() {
        return caravanId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
