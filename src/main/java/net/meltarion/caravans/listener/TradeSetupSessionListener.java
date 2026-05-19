package net.meltarion.caravans.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.meltarion.caravans.MeltarionCaravansPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TradeSetupSessionListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private final MeltarionCaravansPlugin plugin;

    public TradeSetupSessionListener(MeltarionCaravansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        if (!plugin.getTradeSetupSessionService().hasSession(event.getPlayer().getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        String input = PLAIN_TEXT.serialize(event.message());
        plugin.getServer().getScheduler().runTask(plugin, () ->
            plugin.getTradeSetupSessionService().handleInput(event.getPlayer(), input)
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getTradeSetupSessionService().clearSession(event.getPlayer().getUniqueId());
        plugin.getPublicTradeService().clearPlayerState(event.getPlayer().getUniqueId());
    }
}
