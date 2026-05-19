package net.meltarion.caravans.util;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class InventoryTransactionUtil {

    private InventoryTransactionUtil() {
    }

    public static int countSimilarItems(Inventory inventory, ItemStack prototype) {
        if (inventory == null || prototype == null || prototype.getType().isAir()) {
            return 0;
        }

        int total = 0;
        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack != null && !itemStack.getType().isAir() && itemStack.isSimilar(prototype)) {
                total += itemStack.getAmount();
            }
        }
        return total;
    }

    public static boolean hasSpaceFor(Inventory inventory, ItemStack prototype, int amount) {
        if (inventory == null || prototype == null || prototype.getType().isAir() || amount <= 0) {
            return false;
        }

        int remaining = amount;
        int maxStack = prototype.getMaxStackSize();
        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack == null || itemStack.getType().isAir()) {
                remaining -= maxStack;
            } else if (itemStack.isSimilar(prototype)) {
                remaining -= Math.max(0, maxStack - itemStack.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean removeSimilarItems(Inventory inventory, ItemStack prototype, int amount) {
        if (countSimilarItems(inventory, prototype) < amount) {
            return false;
        }

        int remaining = amount;
        for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
            ItemStack itemStack = inventory.getItem(slot);
            if (itemStack == null || itemStack.getType().isAir() || !itemStack.isSimilar(prototype)) {
                continue;
            }

            if (itemStack.getAmount() <= remaining) {
                remaining -= itemStack.getAmount();
                inventory.setItem(slot, null);
            } else {
                itemStack.setAmount(itemStack.getAmount() - remaining);
                inventory.setItem(slot, itemStack);
                remaining = 0;
            }
        }
        return remaining == 0;
    }

    public static boolean addItemsSafely(Inventory inventory, ItemStack prototype, int amount) {
        if (!hasSpaceFor(inventory, prototype, amount)) {
            return false;
        }

        int remaining = amount;
        int maxStack = prototype.getMaxStackSize();
        while (remaining > 0) {
            int stackAmount = Math.min(maxStack, remaining);
            ItemStack toAdd = prototype.clone();
            toAdd.setAmount(stackAmount);
            inventory.addItem(toAdd);
            remaining -= stackAmount;
        }
        return true;
    }

    public static int countCurrency(Inventory inventory, Material currencyMaterial) {
        return countSimilarItems(inventory, new ItemStack(currencyMaterial, 1));
    }

    public static boolean removeCurrency(Inventory inventory, Material currencyMaterial, int amount) {
        return removeSimilarItems(inventory, new ItemStack(currencyMaterial, 1), amount);
    }

    public static boolean addCurrency(Inventory inventory, Material currencyMaterial, int amount) {
        return addItemsSafely(inventory, new ItemStack(currencyMaterial, 1), amount);
    }
}
