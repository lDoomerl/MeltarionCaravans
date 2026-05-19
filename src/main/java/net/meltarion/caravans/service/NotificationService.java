package net.meltarion.caravans.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.meltarion.caravans.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class NotificationService {

    private final ConfigManager configManager;
    private final MessageService messageService;
    private final Map<UUID, Deque<QueuedNotification>> queuedNotifications = new ConcurrentHashMap<>();

    public NotificationService(ConfigManager configManager, MessageService messageService) {
        this.configManager = configManager;
        this.messageService = messageService;
    }

    public void sendPlayerMessage(UUID playerId, String messagePath, Map<String, String> placeholders) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            messageService.send(player, messagePath, placeholders);
            return;
        }

        if (!configManager.shouldQueueOfflineNotifications()) {
            return;
        }

        Deque<QueuedNotification> queue = queuedNotifications.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        while (queue.size() >= configManager.getMaxQueuedNotificationsPerPlayer()) {
            queue.pollFirst();
        }
        queue.addLast(new QueuedNotification(messagePath, placeholders));
    }

    public void flushQueuedNotifications(Player player) {
        Deque<QueuedNotification> queue = queuedNotifications.remove(player.getUniqueId());
        if (queue == null || queue.isEmpty()) {
            return;
        }

        messageService.send(player, "route-offline-notification-summary", Map.of(
            "amount", String.valueOf(queue.size())
        ));
        for (QueuedNotification notification : queue) {
            messageService.send(player, notification.messagePath(), notification.placeholders());
        }
    }

    public void clear(UUID playerId) {
        queuedNotifications.remove(playerId);
    }

    private record QueuedNotification(
        String messagePath,
        Map<String, String> placeholders
    ) {
    }
}
