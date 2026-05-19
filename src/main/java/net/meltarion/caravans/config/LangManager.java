package net.meltarion.caravans.config;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class LangManager {

    private final JavaPlugin plugin;
    private FileConfiguration configuration;
    private File file;

    public LangManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        ensureFileExists();
        this.configuration = YamlConfiguration.loadConfiguration(file);
    }

    public String getMessage(String path) {
        return configuration.getString("messages." + path, "");
    }

    public List<String> getMessageList(String path) {
        return configuration.getStringList("messages." + path);
    }

    private void ensureFileExists() {
        if (file == null) {
            file = new File(plugin.getDataFolder(), "lang.yml");
        }
        if (!file.exists()) {
            plugin.saveResource("lang.yml", false);
        }
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to create lang.yml", exception);
            }
        }
    }
}
