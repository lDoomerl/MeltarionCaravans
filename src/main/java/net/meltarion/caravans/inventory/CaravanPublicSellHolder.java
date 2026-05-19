package net.meltarion.caravans.inventory;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CaravanPublicSellHolder implements InventoryHolder {

    private final UUID caravanId;
    private final String caravanName;
    private Inventory inventory;

    public CaravanPublicSellHolder(UUID caravanId, String caravanName) {
        this.caravanId = caravanId;
        this.caravanName = caravanName;
    }

    public UUID getCaravanId() {
        return caravanId;
    }

    public String getCaravanName() {
        return caravanName;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
