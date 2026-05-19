package net.meltarion.caravans.service;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.meltarion.caravans.config.ConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

public final class CaravanLicenseService {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private final ConfigManager configManager;

    public CaravanLicenseService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public boolean isEnabled() {
        return configManager.isCaravanLicenseEnabled();
    }

    public boolean shouldConsumeOnCreate() {
        return configManager.isCaravanLicenseConsumeOnCreate();
    }

    public boolean isRightClickCreateAllowed() {
        return configManager.isCaravanLicenseRightClickCreateAllowed();
    }

    public boolean hasLicense(Player player) {
        for (ItemStack itemStack : player.getInventory().getContents()) {
            if (isLicenseItem(itemStack)) {
                return true;
            }
        }
        return false;
    }

    public boolean consumeOneLicense(Player player) {
        PlayerInventory inventory = player.getInventory();

        ItemStack mainHand = inventory.getItemInMainHand();
        if (isLicenseItem(mainHand) && decrementStack(inventory, mainHand, true, -1)) {
            return true;
        }

        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack itemStack = contents[slot];
            if (!isLicenseItem(itemStack)) {
                continue;
            }

            if (decrementStack(inventory, itemStack, false, slot)) {
                return true;
            }
        }

        return false;
    }

    public ItemStack createLicenseItem(int amount) {
        ItemStack itemStack = new ItemStack(configManager.getCaravanLicenseMaterial(), amount);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.displayName(LEGACY_SERIALIZER.deserialize(configManager.getCaravanLicenseDisplayName()));

        List<String> configuredLore = configManager.getCaravanLicenseLore();
        if (!configuredLore.isEmpty()) {
            List<Component> lore = new ArrayList<>(configuredLore.size());
            for (String line : configuredLore) {
                lore.add(LEGACY_SERIALIZER.deserialize(line));
            }
            itemMeta.lore(lore);
        }

        Integer customModelData = configManager.getCaravanLicenseCustomModelData();
        if (customModelData != null) {
            itemMeta.setCustomModelData(customModelData);
        }

        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    public void giveLicenses(Player player, int amount) {
        ItemStack itemStack = createLicenseItem(amount);
        var leftovers = player.getInventory().addItem(itemStack);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    public boolean isLicenseItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != configManager.getCaravanLicenseMaterial()) {
            return false;
        }

        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return false;
        }

        if (configManager.isCaravanLicenseRequireDisplayName()) {
            if (!itemMeta.hasDisplayName() || itemMeta.displayName() == null) {
                return false;
            }

            String actualDisplayName = LEGACY_SERIALIZER.serialize(itemMeta.displayName());
            if (!configManager.getCaravanLicenseDisplayName().equals(actualDisplayName)) {
                return false;
            }
        }

        if (configManager.isCaravanLicenseRequireLore()) {
            List<String> expectedLore = configManager.getCaravanLicenseLore();
            List<Component> actualLore = itemMeta.lore();
            if (actualLore == null || actualLore.size() != expectedLore.size()) {
                return false;
            }

            for (int index = 0; index < expectedLore.size(); index++) {
                if (!expectedLore.get(index).equals(LEGACY_SERIALIZER.serialize(actualLore.get(index)))) {
                    return false;
                }
            }
        }

        if (configManager.isCaravanLicenseRequireCustomModelData()) {
            Integer expectedCustomModelData = configManager.getCaravanLicenseCustomModelData();
            if (expectedCustomModelData == null || !itemMeta.hasCustomModelData()) {
                return false;
            }
            if (itemMeta.getCustomModelData() != expectedCustomModelData) {
                return false;
            }
        }

        return true;
    }

    private boolean decrementStack(PlayerInventory inventory, ItemStack itemStack, boolean mainHand, int slot) {
        int amount = itemStack.getAmount();
        if (amount <= 0) {
            return false;
        }

        if (amount == 1) {
            if (mainHand) {
                inventory.setItemInMainHand(null);
            } else {
                inventory.setItem(slot, null);
            }
        } else {
            itemStack.setAmount(amount - 1);
            if (mainHand) {
                inventory.setItemInMainHand(itemStack);
            } else {
                inventory.setItem(slot, itemStack);
            }
        }
        return true;
    }
}
