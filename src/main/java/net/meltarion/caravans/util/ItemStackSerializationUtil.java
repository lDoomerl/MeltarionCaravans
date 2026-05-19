package net.meltarion.caravans.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import net.meltarion.caravans.storage.StorageException;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class ItemStackSerializationUtil {

    private ItemStackSerializationUtil() {
    }

    public static String serializeItemStack(ItemStack itemStack) throws StorageException {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream outputStream = new BukkitObjectOutputStream(byteStream)) {
            outputStream.writeObject(itemStack);
            outputStream.flush();
            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        } catch (IOException exception) {
            throw new StorageException("Failed to serialize ItemStack.", exception);
        }
    }

    public static ItemStack deserializeItemStack(String serializedItemStack) throws StorageException {
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(Base64.getDecoder().decode(serializedItemStack));
             BukkitObjectInputStream inputStream = new BukkitObjectInputStream(byteStream)) {
            return (ItemStack) inputStream.readObject();
        } catch (IOException | ClassNotFoundException exception) {
            throw new StorageException("Failed to deserialize ItemStack.", exception);
        }
    }

    public static String serializeItemStacks(ItemStack[] contents) throws StorageException {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream outputStream = new BukkitObjectOutputStream(byteStream)) {
            outputStream.writeInt(contents.length);
            for (ItemStack itemStack : contents) {
                outputStream.writeObject(itemStack);
            }
            outputStream.flush();
            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        } catch (IOException exception) {
            throw new StorageException("Failed to serialize ItemStack array.", exception);
        }
    }

    public static ItemStack[] deserializeItemStacks(String serializedContents) throws StorageException {
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(Base64.getDecoder().decode(serializedContents));
             BukkitObjectInputStream inputStream = new BukkitObjectInputStream(byteStream)) {
            int size = inputStream.readInt();
            ItemStack[] contents = new ItemStack[size];
            for (int index = 0; index < size; index++) {
                contents[index] = (ItemStack) inputStream.readObject();
            }
            return contents;
        } catch (IOException | ClassNotFoundException exception) {
            throw new StorageException("Failed to deserialize ItemStack array.", exception);
        }
    }
}
