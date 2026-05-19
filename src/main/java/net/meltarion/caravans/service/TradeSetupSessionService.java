package net.meltarion.caravans.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.meltarion.caravans.model.CaravanRecord;
import net.meltarion.caravans.model.TradeOperationType;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class TradeSetupSessionService {

    private final CaravanService caravanService;
    private final TradeOperationService tradeOperationService;
    private final MessageService messageService;
    private final java.util.function.IntSupplier timeoutSecondsSupplier;
    private final Map<UUID, TradeSetupSession> sessions = new ConcurrentHashMap<>();

    public TradeSetupSessionService(
        CaravanService caravanService,
        TradeOperationService tradeOperationService,
        MessageService messageService,
        java.util.function.IntSupplier timeoutSecondsSupplier
    ) {
        this.caravanService = caravanService;
        this.tradeOperationService = tradeOperationService;
        this.messageService = messageService;
        this.timeoutSecondsSupplier = timeoutSecondsSupplier;
    }

    public void beginSellSession(Player player, CaravanRecord caravan, int slot) {
        sessions.put(player.getUniqueId(), TradeSetupSession.forSell(caravan.id(), slot));
        messageService.send(player, "setup-enter-sell-price", Map.of(
            "timeout", String.valueOf(timeoutSecondsSupplier.getAsInt()),
            "name", caravan.name(),
            "id", caravan.id().toString().substring(0, 8)
        ));
    }

    public void beginBuySession(Player player, CaravanRecord caravan, Material material) {
        sessions.put(player.getUniqueId(), TradeSetupSession.forBuy(caravan.id(), material));
        messageService.send(player, "setup-enter-buy-amount", Map.of(
            "timeout", String.valueOf(timeoutSecondsSupplier.getAsInt()),
            "material", material.name(),
            "name", caravan.name(),
            "id", caravan.id().toString().substring(0, 8)
        ));
    }

    public boolean hasSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public void handleInput(Player player, String message) {
        UUID playerId = player.getUniqueId();
        TradeSetupSession session = sessions.get(playerId);
        if (session == null) {
            return;
        }

        if (isExpired(session)) {
            sessions.remove(playerId);
            messageService.send(player, "setup-timeout", Map.of("timeout", String.valueOf(timeoutSecondsSupplier.getAsInt())));
            return;
        }

        if ("cancel".equalsIgnoreCase(message.trim())) {
            sessions.remove(playerId);
            messageService.send(player, "setup-cancelled");
            return;
        }

        CaravanRecord caravan = caravanService.getCaravan(session.caravanId());
        if (caravan == null) {
            sessions.remove(playerId);
            messageService.send(player, "caravan-not-found");
            return;
        }

        switch (session.step()) {
            case SELL_PRICE -> handleSellPrice(player, caravan, session, message);
            case BUY_AMOUNT -> handleBuyAmount(player, caravan, session, message);
            case BUY_PRICE -> handleBuyPrice(player, caravan, session, message);
            case BUY_MAX_TOTAL -> handleBuyMaxTotal(player, caravan, session, message);
        }
    }

    public void clearSession(UUID playerId) {
        sessions.remove(playerId);
    }

    private void handleSellPrice(Player player, CaravanRecord caravan, TradeSetupSession session, String message) {
        Integer price = parsePositiveInt(message);
        if (price == null) {
            messageService.send(player, "setup-invalid-price");
            return;
        }

        TradeOperationCreateResult result = tradeOperationService.createSellOperation(caravan, session.slot(), price);
        if (!result.success()) {
            switch (result.failureReason()) {
                case DUPLICATE_SLOT -> messageService.send(player, "setup-slot-already-reserved");
                case EMPTY_SLOT -> messageService.send(player, "setup-no-item-selected");
                case INVALID_PRICE -> messageService.send(player, "setup-invalid-price");
                case INVALID_AMOUNT -> messageService.send(player, "setup-invalid-amount");
                default -> messageService.send(player, "storage-error");
            }
            sessions.remove(player.getUniqueId());
            return;
        }

        sessions.remove(player.getUniqueId());
        messageService.send(player, "setup-sell-created", Map.of(
            "name", caravan.name(),
            "id", caravan.id().toString().substring(0, 8),
            "price", String.valueOf(price)
        ));
    }

    private void handleBuyAmount(Player player, CaravanRecord caravan, TradeSetupSession session, String message) {
        Integer amount = parsePositiveInt(message);
        if (amount == null || amount < 1 || amount > 64) {
            messageService.send(player, "setup-invalid-amount");
            return;
        }

        TradeSetupSession updatedSession = session.withAmount(amount).advance(TradeSetupStep.BUY_PRICE);
        sessions.put(player.getUniqueId(), updatedSession);
        messageService.send(player, "setup-enter-buy-price", Map.of(
            "material", updatedSession.material().name(),
            "amount", String.valueOf(amount),
            "timeout", String.valueOf(timeoutSecondsSupplier.getAsInt())
        ));
    }

    private void handleBuyPrice(Player player, CaravanRecord caravan, TradeSetupSession session, String message) {
        Integer price = parsePositiveInt(message);
        if (price == null) {
            messageService.send(player, "setup-invalid-price");
            return;
        }

        TradeSetupSession updatedSession = session.withPrice(price).advance(TradeSetupStep.BUY_MAX_TOTAL);
        sessions.put(player.getUniqueId(), updatedSession);
        messageService.send(player, "setup-enter-buy-max-total", Map.of(
            "material", updatedSession.material().name(),
            "amount", String.valueOf(updatedSession.amountPerTransaction()),
            "price", String.valueOf(price),
            "timeout", String.valueOf(timeoutSecondsSupplier.getAsInt())
        ));
    }

    private void handleBuyMaxTotal(Player player, CaravanRecord caravan, TradeSetupSession session, String message) {
        Integer maxTotal = parsePositiveInt(message);
        if (maxTotal == null || maxTotal < session.amountPerTransaction()) {
            messageService.send(player, "setup-invalid-max-total", Map.of(
                "amount", String.valueOf(session.amountPerTransaction())
            ));
            return;
        }

        TradeOperationCreateResult result = tradeOperationService.createBuyOperation(
            caravan,
            session.material(),
            session.amountPerTransaction(),
            session.price(),
            maxTotal
        );
        if (!result.success()) {
            switch (result.failureReason()) {
                case INVALID_MATERIAL -> messageService.send(player, "trade-invalid-material");
                case INVALID_AMOUNT -> messageService.send(player, "setup-invalid-amount");
                case INVALID_PRICE -> messageService.send(player, "setup-invalid-price");
                default -> messageService.send(player, "storage-error");
            }
            sessions.remove(player.getUniqueId());
            return;
        }

        sessions.remove(player.getUniqueId());
        messageService.send(player, "setup-buy-created", Map.of(
            "name", caravan.name(),
            "id", caravan.id().toString().substring(0, 8),
            "material", session.material().name(),
            "amount", String.valueOf(session.amountPerTransaction()),
            "price", String.valueOf(session.price()),
            "max", String.valueOf(maxTotal)
        ));
    }

    private boolean isExpired(TradeSetupSession session) {
        return Duration.between(session.startedAt(), Instant.now()).getSeconds() > timeoutSecondsSupplier.getAsInt();
    }

    private Integer parsePositiveInt(String input) {
        try {
            int value = Integer.parseInt(input.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private enum TradeSetupStep {
        SELL_PRICE,
        BUY_AMOUNT,
        BUY_PRICE,
        BUY_MAX_TOTAL
    }

    private record TradeSetupSession(
        UUID caravanId,
        TradeOperationType type,
        TradeSetupStep step,
        Integer slot,
        Material material,
        Integer amountPerTransaction,
        Integer price,
        Instant startedAt
    ) {
        private static TradeSetupSession forSell(UUID caravanId, int slot) {
            return new TradeSetupSession(caravanId, TradeOperationType.SELL, TradeSetupStep.SELL_PRICE, slot, null, null, null, Instant.now());
        }

        private static TradeSetupSession forBuy(UUID caravanId, Material material) {
            return new TradeSetupSession(caravanId, TradeOperationType.BUY, TradeSetupStep.BUY_AMOUNT, null, material, null, null, Instant.now());
        }

        private TradeSetupSession withAmount(int amount) {
            return new TradeSetupSession(caravanId, type, step, slot, material, amount, price, startedAt);
        }

        private TradeSetupSession withPrice(int updatedPrice) {
            return new TradeSetupSession(caravanId, type, step, slot, material, amountPerTransaction, updatedPrice, startedAt);
        }

        private TradeSetupSession advance(TradeSetupStep updatedStep) {
            return new TradeSetupSession(caravanId, type, updatedStep, slot, material, amountPerTransaction, price, startedAt);
        }
    }
}
