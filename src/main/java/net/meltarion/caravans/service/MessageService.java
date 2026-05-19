package net.meltarion.caravans.service;

import java.util.List;
import java.util.Map;
import net.meltarion.caravans.config.ConfigManager;
import net.meltarion.caravans.config.LangManager;
import org.bukkit.command.CommandSender;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class MessageService {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private final ConfigManager configManager;
    private final LangManager langManager;

    public MessageService(ConfigManager configManager, LangManager langManager) {
        this.configManager = configManager;
        this.langManager = langManager;
    }

    public void send(CommandSender sender, String path) {
        send(sender, path, Map.of());
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        String raw = langManager.getMessage(path);
        if (raw.isEmpty()) {
            raw = configManager.getLegacyMessage(path);
        }
        String message = applyPlaceholders(raw, placeholders);
        if (!message.isEmpty()) {
            sender.sendMessage(LEGACY_SERIALIZER.deserialize(message));
        }
    }

    public void sendList(CommandSender sender, String path) {
        sendList(sender, path, Map.of());
    }

    public void sendList(CommandSender sender, String path, Map<String, String> placeholders) {
        List<String> lines = langManager.getMessageList(path);
        if (lines.isEmpty()) {
            lines = configManager.getLegacyMessageList(path);
        }
        for (String line : lines) {
            sender.sendMessage(LEGACY_SERIALIZER.deserialize(applyPlaceholders(line, placeholders)));
        }
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        String output = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace('%' + entry.getKey() + '%', entry.getValue());
        }
        return output;
    }

}
