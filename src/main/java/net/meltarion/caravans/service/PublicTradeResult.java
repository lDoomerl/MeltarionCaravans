package net.meltarion.caravans.service;

import java.util.Map;

public record PublicTradeResult(
    boolean success,
    String messageKey,
    Map<String, String> placeholders
) {

    public static PublicTradeResult success(String messageKey, Map<String, String> placeholders) {
        return new PublicTradeResult(true, messageKey, placeholders);
    }

    public static PublicTradeResult failure(String messageKey, Map<String, String> placeholders) {
        return new PublicTradeResult(false, messageKey, placeholders);
    }
}
