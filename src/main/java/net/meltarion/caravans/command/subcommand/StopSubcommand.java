package net.meltarion.caravans.command.subcommand;

import java.util.Locale;
import java.util.Map;
import net.meltarion.caravans.command.CaravanSubcommand;
import net.meltarion.caravans.command.CommandContext;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.service.CaravanLookupResult;
import net.meltarion.caravans.service.CaravanMovementResult;
import org.bukkit.entity.Player;

public final class StopSubcommand implements CaravanSubcommand {

    @Override
    public String getName() {
        return "stop";
    }

    @Override
    public String getPermission() {
        return "meltarion.caravans.use";
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public void execute(CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            context.messages().send(context.sender(), "player-only");
            return;
        }
        if (context.args().length < 2) {
            context.messages().send(player, "stop-usage");
            return;
        }

        CaravanRecord caravan = resolveCaravan(context, player, context.args()[1]);
        if (caravan == null) {
            return;
        }

        CaravanMovementResult result = context.movement().stopMovement(caravan);
        sendMovementResult(context, player, result, "movement-stopped");
    }

    private CaravanRecord resolveCaravan(CommandContext context, Player player, String reference) {
        CaravanLookupResult result = player.hasPermission("meltarion.caravans.admin")
            ? context.caravans().findCaravan(reference)
            : context.caravans().findCaravanForOwner(player.getUniqueId(), reference);
        if (!result.success()) {
            context.messages().send(player, result.failureReason() == CaravanLookupResult.FailureReason.AMBIGUOUS ? "ambiguous-id" : "caravan-not-found");
            return null;
        }
        return result.caravan();
    }

    private void sendMovementResult(CommandContext context, Player player, CaravanMovementResult result, String successKey) {
        if (!result.success()) {
            switch (result.failureReason()) {
                case DISABLED -> context.messages().send(player, "movement-disabled");
                case NO_POSITION -> context.messages().send(player, "movement-no-position");
                case INVALID_TARGET -> context.messages().send(player, "movement-invalid-target");
                case HOME_MISSING -> context.messages().send(player, "movement-no-home");
                case STORAGE_ERROR -> context.messages().send(player, "storage-error");
            }
            return;
        }
        context.messages().send(player, successKey, placeholders(context, result.caravan()));
    }

    private Map<String, String> placeholders(CommandContext context, CaravanRecord caravan) {
        return Map.ofEntries(
            Map.entry("id", context.caravans().getShortId(caravan)),
            Map.entry("name", caravan.name()),
            Map.entry("world", caravan.worldName() == null ? "unknown" : caravan.worldName()),
            Map.entry("x", caravan.virtualX() == null ? "?" : String.format(Locale.US, "%.1f", caravan.virtualX())),
            Map.entry("y", caravan.virtualY() == null ? "?" : String.format(Locale.US, "%.1f", caravan.virtualY())),
            Map.entry("z", caravan.virtualZ() == null ? "?" : String.format(Locale.US, "%.1f", caravan.virtualZ())),
            Map.entry("target_x", caravan.targetX() == null ? "?" : String.format(Locale.US, "%.1f", caravan.targetX())),
            Map.entry("target_y", caravan.targetY() == null ? "?" : String.format(Locale.US, "%.1f", caravan.targetY())),
            Map.entry("target_z", caravan.targetZ() == null ? "?" : String.format(Locale.US, "%.1f", caravan.targetZ())),
            Map.entry("speed", String.format(Locale.US, "%.2f", caravan.speedBlocksPerSecond())),
            Map.entry("eta", caravan.etaSeconds() == null ? "?" : String.valueOf(caravan.etaSeconds())),
            Map.entry("status", caravan.status().name())
        );
    }
}
