package net.meltarion.caravans.inventory;

import java.util.UUID;
import net.meltarion.caravans.service.TradeCatalogCategory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class CaravanBuyMaterialHolder implements InventoryHolder {

    private final UUID caravanId;
    private final String caravanName;
    private final TradeCatalogCategory category;
    private final int page;
    private Inventory inventory;

    public CaravanBuyMaterialHolder(UUID caravanId, String caravanName, TradeCatalogCategory category, int page) {
        this.caravanId = caravanId;
        this.caravanName = caravanName;
        this.category = category;
        this.page = page;
    }

    public UUID getCaravanId() {
        return caravanId;
    }

    public String getCaravanName() {
        return caravanName;
    }

    public TradeCatalogCategory getCategory() {
        return category;
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
