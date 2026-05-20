package net.meltarion.caravans.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ResourceUpdateService {

    private static final String VERSION_KEY = "config-version";
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JavaPlugin plugin;
    private final Logger logger;

    public ResourceUpdateService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void ensureResourcesUpToDate() {
        ensureResourceUpToDate("config.yml");
        ensureResourceUpToDate("lang.yml");
        ensureResourceUpToDate("gui.yml");
    }

    public void ensureResourceUpToDate(String resourceName) {
        try {
            updateResource(resourceName);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to update resource " + resourceName, exception);
        }
    }

    private void updateResource(String resourceName) throws IOException {
        Files.createDirectories(plugin.getDataFolder().toPath());

        String defaultContent = readBundledResource(resourceName);
        YamlConfiguration defaultConfig;
        try {
            defaultConfig = loadYaml(defaultContent);
        } catch (InvalidConfigurationException exception) {
            throw new IOException("Bundled default resource is invalid YAML: " + resourceName, exception);
        }
        int latestVersion = Math.max(1, defaultConfig.getInt(VERSION_KEY, 1));
        Path filePath = plugin.getDataFolder().toPath().resolve(resourceName);

        if (Files.notExists(filePath)) {
            Files.writeString(filePath, defaultContent, StandardCharsets.UTF_8);
            logger.info("Created resource " + resourceName + " with version " + latestVersion + '.');
            return;
        }

        String existingContent = Files.readString(filePath, StandardCharsets.UTF_8);
        YamlConfiguration existingConfig;
        try {
            existingConfig = loadYaml(existingContent);
        } catch (InvalidConfigurationException exception) {
            Path backupPath = createBackup(filePath);
            Files.writeString(filePath, defaultContent, StandardCharsets.UTF_8);
            logger.log(
                Level.SEVERE,
                "Invalid YAML in " + resourceName + ". Backed up to " + backupPath + " and regenerated defaults.",
                exception
            );
            return;
        }

        int existingVersion = existingConfig.getInt(VERSION_KEY, 0);
        if (existingVersion > latestVersion) {
            logger.warning(
                "Resource " + resourceName + " has newer version " + existingVersion
                    + " than bundled default " + latestVersion + ". Leaving file untouched."
            );
            return;
        }

        if (existingVersion == latestVersion) {
            logger.info("Resource " + resourceName + " is current at version " + existingVersion + '.');
            return;
        }

        Path backupPath = createBackup(filePath);
        YamlConfiguration mergedConfig;
        try {
            mergedConfig = loadYaml(defaultContent);
        } catch (InvalidConfigurationException exception) {
            throw new IOException("Bundled default resource is invalid YAML during merge: " + resourceName, exception);
        }
        mergeInto(mergedConfig, existingConfig, "");
        mergedConfig.set(VERSION_KEY, latestVersion);
        Files.writeString(filePath, mergedConfig.saveToString(), StandardCharsets.UTF_8);
        logger.info(
            "Updated resource " + resourceName + " from version " + existingVersion + " to " + latestVersion
                + ". Backup: " + backupPath
        );
    }

    private String readBundledResource(String resourceName) throws IOException {
        try (InputStream inputStream = plugin.getResource(resourceName)) {
            if (inputStream == null) {
                throw new IOException("Bundled resource not found: " + resourceName);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private YamlConfiguration loadYaml(String content) throws InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().parseComments(true);
        configuration.loadFromString(content);
        return configuration;
    }

    private void mergeInto(YamlConfiguration target, YamlConfiguration source, String pathPrefix) {
        ConfigurationSection section = pathPrefix.isEmpty() ? source : source.getConfigurationSection(pathPrefix);
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            String path = pathPrefix.isEmpty() ? key : pathPrefix + '.' + key;
            Object value = source.get(path);
            if (value instanceof ConfigurationSection) {
                if (!target.isConfigurationSection(path)) {
                    target.createSection(path);
                }
                mergeInto(target, source, path);
                continue;
            }
            target.set(path, copyValue(value));
        }
    }

    private Object copyValue(Object value) {
        if (value instanceof java.util.List<?> list) {
            return new java.util.ArrayList<>(list);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(Objects.toString(entry.getKey()), entry.getValue());
            }
            return copy;
        }
        return value;
    }

    private Path createBackup(Path filePath) throws IOException {
        Path backupPath = filePath.resolveSibling(filePath.getFileName() + ".bak-" + LocalDateTime.now().format(BACKUP_TIMESTAMP));
        Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        return backupPath;
    }
}
