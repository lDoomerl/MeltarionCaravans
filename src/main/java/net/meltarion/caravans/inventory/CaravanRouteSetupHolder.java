package net.meltarion.caravans.inventory;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CaravanRouteSetupHolder implements InventoryHolder {

    private final UUID caravanId;
    private final int page;
    private Inventory inventory;

    public CaravanRouteSetupHolder(UUID caravanId, int page) {
        this.caravanId = caravanId;
        this.page = page;
    }

    public UUID getCaravanId() {
        return caravanId;
    }

    public int getPage() {
        return page;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
