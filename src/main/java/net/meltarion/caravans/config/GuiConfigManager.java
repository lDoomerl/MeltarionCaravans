package net.meltarion.caravans.config;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class GuiConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration configuration;
    private File file;

    public GuiConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        ensureFileExists();
        this.configuration = YamlConfiguration.loadConfiguration(file);
    }

    public String getString(String path, String fallback) {
        return configuration.getString("gui." + path, fallback);
    }

    public List<String> getStringList(String path, List<String> fallback) {
        List<String> value = configuration.getStringList("gui." + path);
        return value.isEmpty() ? fallback : value;
    }

    public Material getMaterial(String path, Material fallback) {
        Material material = Material.matchMaterial(configuration.getString("gui." + path, fallback.name()));
        return material == null ? fallback : material;
    }

    private void ensureFileExists() {
        if (file == null) {
            file = new File(plugin.getDataFolder(), "gui.yml");
        }
        if (!file.exists()) {
            plugin.saveResource("gui.yml", false);
        }
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to create gui.yml", exception);
            }
        }
    }
}
