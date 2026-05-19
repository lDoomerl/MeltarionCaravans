package net.meltarion.caravans.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.inventory.CaravanInventoryHolder;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.storage.CaravanInventoryStorage;
import net.meltarion.caravans.storage.StorageException;
import net.meltarion.caravans.util.ItemStackSerializationUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class CaravanInventoryService {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private final ConfigManager configManager;
    private final CaravanInventoryStorage storage;
    private final Logger logger;
    private final Map<UUID, Inventory> openInventories = new ConcurrentHashMap<>();

    public CaravanInventoryService(ConfigManager configManager, CaravanInventoryStorage storage, Logger logger) {
        this.configManager = configManager;
        this.storage = storage;
        this.logger = logger;
    }

    public void initializeInventory(CaravanRecord caravan) throws StorageException {
        if (storage.loadInventoryContents(caravan.id()) == null) {
                storage.saveInventoryContents(
                caravan.id(),
                ItemStackSerializationUtil.serializeItemStacks(new ItemStack[configManager.getCaravanInventorySize()]),
                Instant.now().toString()
            );
        }
    }

    public Inventory openInventoryForAdmin(Player player, CaravanRecord caravan) throws StorageException {
        Inventory inventory = getInventory(caravan);
        player.openInventory(inventory);
        return inventory;
    }

    public Inventory getInventory(CaravanRecord caravan) throws StorageException {
        Inventory inventory = openInventories.get(caravan.id());
        if (inventory == null) {
            inventory = loadInventory(caravan);
            openInventories.put(caravan.id(), inventory);
        }
        return inventory;
    }

    public void saveInventory(CaravanRecord caravan) throws StorageException {
        Inventory inventory = openInventories.get(caravan.id());
        if (inventory == null) {
            inventory = getInventory(caravan);
        }
        saveInventory(inventory);
    }

    public void handleInventoryClose(Inventory inventory) throws StorageException {
        if (!(inventory.getHolder(false) instanceof CaravanInventoryHolder holder)) {
            return;
        }

        saveInventory(inventory);
        if (inventory.getViewers().size() <= 1) {
            openInventories.remove(holder.getCaravanId(), inventory);
        }
    }

    public void saveAllOpenInventories() {
        for (Inventory inventory : openInventories.values()) {
            try {
                saveInventory(inventory);
            } catch (StorageException exception) {
                logger.log(Level.SEVERE, "Failed to save an open caravan inventory during plugin shutdown.", exception);
            }
        }
        openInventories.clear();
    }

    public void deleteInventory(UUID caravanId) throws StorageException {
        openInventories.remove(caravanId);
        storage.deleteInventoryContents(caravanId);
    }

    public void discardOpenInventory(UUID caravanId) {
        openInventories.remove(caravanId);
    }

    public ItemStack getItemInSlot(CaravanRecord caravan, int slot) throws StorageException {
        Inventory inventory = getInventory(caravan);

        if (slot < 0 || slot >= inventory.getSize()) {
            return null;
        }

        ItemStack itemStack = inventory.getItem(slot);
        return itemStack == null ? null : itemStack.clone();
    }

    public ItemStack[] getInventorySnapshot(CaravanRecord caravan) throws StorageException {
        Inventory inventory = getInventory(caravan);

        ItemStack[] snapshot = new ItemStack[inventory.getSize()];
        ItemStack[] contents = inventory.getContents();
        for (int index = 0; index < contents.length; index++) {
            snapshot[index] = contents[index] == null ? null : contents[index].clone();
        }
        return snapshot;
    }

    private Inventory loadInventory(CaravanRecord caravan) throws StorageException {
        String serializedContents = storage.loadInventoryContents(caravan.id());
        if (serializedContents == null) {
            initializeInventory(caravan);
            serializedContents = storage.loadInventoryContents(caravan.id());
        }

        ItemStack[] storedContents = deserializeContents(serializedContents);
        CaravanInventoryHolder holder = new CaravanInventoryHolder(caravan.id(), caravan.name());
        Inventory inventory = Bukkit.createInventory(
            holder,
            configManager.getCaravanInventorySize(),
            LEGACY_SERIALIZER.deserialize(
                configManager.getCaravanInventoryTitle()
                    .replace("%name%", caravan.name())
                    .replace("%id%", caravan.id().toString().substring(0, 8))
            )
        );
        holder.setInventory(inventory);

        ItemStack[] resizedContents = new ItemStack[inventory.getSize()];
        System.arraycopy(storedContents, 0, resizedContents, 0, Math.min(storedContents.length, resizedContents.length));
        inventory.setContents(resizedContents);
        return inventory;
    }

    private void saveInventory(Inventory inventory) throws StorageException {
        if (!(inventory.getHolder(false) instanceof CaravanInventoryHolder holder)) {
            return;
        }

        storage.saveInventoryContents(
            holder.getCaravanId(),
            ItemStackSerializationUtil.serializeItemStacks(inventory.getContents()),
            Instant.now().toString()
        );
    }

    private ItemStack[] deserializeContents(String serializedContents) throws StorageException {
        if (serializedContents == null || serializedContents.isBlank()) {
            return new ItemStack[configManager.getCaravanInventorySize()];
        }
        return ItemStackSerializationUtil.deserializeItemStacks(serializedContents);
    }
}
