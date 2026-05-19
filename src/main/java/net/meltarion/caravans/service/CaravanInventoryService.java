package net.meltarion.caravans.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

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
                serializeContents(new ItemStack[configManager.getCaravanInventorySize()]),
                Instant.now().toString()
            );
        }
    }

    public Inventory openInventoryForAdmin(Player player, CaravanRecord caravan) throws StorageException {
        Inventory inventory = openInventories.get(caravan.id());
        if (inventory == null) {
            inventory = loadInventory(caravan);
            openInventories.put(caravan.id(), inventory);
        }

        player.openInventory(inventory);
        return inventory;
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
            serializeContents(inventory.getContents()),
            Instant.now().toString()
        );
    }

    private String serializeContents(ItemStack[] contents) throws StorageException {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream outputStream = new BukkitObjectOutputStream(byteStream)) {
            outputStream.writeInt(contents.length);
            for (ItemStack itemStack : contents) {
                outputStream.writeObject(itemStack);
            }
            outputStream.flush();
            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        } catch (IOException exception) {
            throw new StorageException("Failed to serialize caravan inventory.", exception);
        }
    }

    private ItemStack[] deserializeContents(String serializedContents) throws StorageException {
        if (serializedContents == null || serializedContents.isBlank()) {
            return new ItemStack[configManager.getCaravanInventorySize()];
        }

        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(Base64.getDecoder().decode(serializedContents));
             BukkitObjectInputStream inputStream = new BukkitObjectInputStream(byteStream)) {
            int size = inputStream.readInt();
            ItemStack[] contents = new ItemStack[size];
            for (int index = 0; index < size; index++) {
                contents[index] = (ItemStack) inputStream.readObject();
            }
            return contents;
        } catch (IOException | ClassNotFoundException exception) {
            throw new StorageException("Failed to deserialize caravan inventory.", exception);
        }
    }
}
