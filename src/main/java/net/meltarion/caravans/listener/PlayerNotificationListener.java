package net.meltarion.caravans.listener;

import net.meltarion.caravans.MeltarionCaravansPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerNotificationListener implements Listener {

    private final MeltarionCaravansPlugin plugin;

    public PlayerNotificationListener(MeltarionCaravansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getNotificationService().flushQueuedNotifications(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getPublicTradeService().clearPlayerState(event.getPlayer().getUniqueId());
    }
}
