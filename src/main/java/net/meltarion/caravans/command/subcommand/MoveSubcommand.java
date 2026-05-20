package net.meltarion.caravans.command.subcommand;

import java.util.Locale;
import java.util.Map;
import net.meltarion.caravans.command.CaravanSubcommand;
import net.meltarion.caravans.command.CommandContext;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.model.CaravanStatus;
import net.meltarion.caravans.service.CaravanMovementResult;
import org.bukkit.entity.Player;

public final class MoveSubcommand implements CaravanSubcommand {

    @Override
    public String getName() {
        return "move";
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
        if (context.args().length < 4) {
            context.messages().send(player, "move-usage");
            return;
        }

        CaravanRecord caravan = resolveCaravan(context, player, context.args()[1]);
        if (caravan == null) {
            return;
        }

        double x;
        double z;
        try {
            x = Double.parseDouble(context.args()[2]);
            z = Double.parseDouble(context.args()[3]);
        } catch (NumberFormatException exception) {
            context.messages().send(player, "movement-invalid-target");
            return;
        }

        CaravanMovementResult result = context.movement().startMovement(caravan, player.getWorld(), x, z, CaravanStatus.TRAVELING);
        sendMovementResult(context, player, result, "movement-started");
    }

    private CaravanRecord resolveCaravan(CommandContext context, Player player, String reference) {
        var result = player.hasPermission("meltarion.caravans.admin")
            ? context.identifiers().resolveForAdmin(reference)
            : context.identifiers().resolveForPlayer(player, reference);
        if (!result.success()) {
            context.identifiers().sendFailure(player, result);
            return null;
        }
        if (!player.hasPermission("meltarion.caravans.admin") && !result.caravan().ownerId().equals(player.getUniqueId())) {
            context.messages().send(player, "physical-not-owner");
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
