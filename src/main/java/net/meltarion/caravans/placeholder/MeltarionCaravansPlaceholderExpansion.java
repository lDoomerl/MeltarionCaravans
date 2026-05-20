package net.meltarion.caravans.placeholder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.meltarion.caravans.MeltarionCaravansPlugin;
import net.meltarion.caravans.api.CaravanSummary;
import net.meltarion.caravans.api.MeltarionCaravansApi;
import net.meltarion.caravans.config.ConfigManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public final class MeltarionCaravansPlaceholderExpansion extends PlaceholderExpansion {

    private final MeltarionCaravansPlugin plugin;
    private final MeltarionCaravansApi api;
    private final ConfigManager configManager;

    public MeltarionCaravansPlaceholderExpansion(
        MeltarionCaravansPlugin plugin,
        MeltarionCaravansApi api,
        ConfigManager configManager
    ) {
        this.plugin = plugin;
        this.api = api;
        this.configManager = configManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "meltarioncaravans";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String emptyValue = configManager.getPlaceholderApiEmptyValue();
        if (player == null || player.getUniqueId() == null) {
            return emptyValue;
        }

        if (!api.isCaravanPluginReady()) {
            return emptyValue;
        }

        List<CaravanSummary> caravans = api.getCaravansByOwner(player.getUniqueId());
        String normalized = params.toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "count" -> String.valueOf(caravans.size());
            case "limit" -> String.valueOf(api.getCaravanLimit(player.getUniqueId()));
            case "active" -> String.valueOf(caravans.size());
            case "traveling" -> String.valueOf(countByStatus(caravans, "TRAVELING"));
            case "stopped" -> String.valueOf(countByStatus(caravans, "STOPPED"));
            case "attacked" -> String.valueOf(countByStatus(caravans, "ATTACKED"));
            case "returning" -> String.valueOf(countByStatus(caravans, "RETURNING"));
            case "first_name" -> getFieldByIndex(caravans, 1, "name", emptyValue);
            case "first_status" -> getFieldByIndex(caravans, 1, "status", emptyValue);
            case "first_hp" -> getFieldByIndex(caravans, 1, "hp", emptyValue);
            case "first_max_hp" -> getFieldByIndex(caravans, 1, "max_hp", emptyValue);
            case "first_eta" -> getFieldByIndex(caravans, 1, "eta", emptyValue);
            case "first_route_running" -> getFieldByIndex(caravans, 1, "route_running", emptyValue);
            case "first_physical_spawned" -> getFieldByIndex(caravans, 1, "physical_spawned", emptyValue);
            default -> resolveIndexedPlaceholder(caravans, normalized, emptyValue);
        };
    }

    private String resolveIndexedPlaceholder(List<CaravanSummary> caravans, String params, String emptyValue) {
        int separator = params.indexOf('_');
        if (separator <= 0 || separator == params.length() - 1) {
            return emptyValue;
        }

        int index;
        try {
            index = Integer.parseInt(params.substring(0, separator));
        } catch (NumberFormatException exception) {
            return emptyValue;
        }

        String field = params.substring(separator + 1);
        return getFieldByIndex(caravans, index, field, emptyValue);
    }

    private String getFieldByIndex(List<CaravanSummary> caravans, int index, String field, String emptyValue) {
        if (index < 1 || index > caravans.size()) {
            return emptyValue;
        }

        CaravanSummary caravan = caravans.get(index - 1);
        return switch (field) {
            case "name" -> caravan.name();
            case "status" -> caravan.status();
            case "hp" -> formatHp(caravan.hp());
            case "max_hp" -> formatHp(caravan.maxHp());
            case "eta" -> formatEta(caravan.etaSeconds());
            case "route_running" -> String.valueOf(caravan.routeRunning());
            case "physical_spawned" -> String.valueOf(caravan.physicalSpawned());
            case "active_sell_offers" -> String.valueOf(caravan.activeSellOffers());
            case "active_buy_orders" -> String.valueOf(caravan.activeBuyOrders());
            default -> emptyValue;
        };
    }

    private long countByStatus(List<CaravanSummary> caravans, String status) {
        return caravans.stream()
            .filter(caravan -> status.equals(caravan.status()))
            .count();
    }

    private String formatHp(double value) {
        int decimals = configManager.getPlaceholderApiHpDecimals();
        return BigDecimal.valueOf(value)
            .setScale(decimals, RoundingMode.HALF_UP)
            .toPlainString();
    }

    private String formatEta(Integer etaSeconds) {
        if (etaSeconds == null) {
            return configManager.getPlaceholderApiEmptyValue();
        }

        int totalSeconds = Math.max(0, etaSeconds);
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        String format = configManager.getPlaceholderApiEtaFormat().toLowerCase(Locale.ROOT);
        return format
            .replace("hh", String.format(Locale.US, "%02d", hours))
            .replace("mm", String.format(Locale.US, "%02d", minutes))
            .replace("ss", String.format(Locale.US, "%02d", seconds));
    }
}
