package net.meltarion.caravans.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.meltarion.caravans.model.CaravanRecord;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CaravanIdentifierResolver {

    private final CaravanService caravanService;
    private final MessageService messageService;

    public CaravanIdentifierResolver(CaravanService caravanService, MessageService messageService) {
        this.caravanService = caravanService;
        this.messageService = messageService;
    }

    public CaravanIdentifierResult resolveForPlayer(Player player, String reference) {
        return resolveForOwner(player.getUniqueId(), reference);
    }

    public CaravanIdentifierResult resolveForOwner(UUID ownerId, String reference) {
        List<CaravanRecord> ownerCaravans = caravanService.getCaravans(ownerId);
        return resolveOwnerScoped(ownerCaravans, reference);
    }

    public CaravanIdentifierResult resolveForAdmin(String reference) {
        String normalizedReference = normalize(reference);
        if (normalizedReference == null) {
            return CaravanIdentifierResult.failure(CaravanIdentifierResult.FailureReason.NOT_FOUND, reference);
        }

        CaravanIdentifierResult globalUuidResult = resolveUuidScoped(caravanService.getAllCaravans(), normalizedReference);
        if (globalUuidResult.success() || globalUuidResult.failureReason() == CaravanIdentifierResult.FailureReason.AMBIGUOUS_SHORT_ID) {
            return globalUuidResult;
        }

        int separatorIndex = normalizedReference.indexOf(':');
        if (separatorIndex < 1 || separatorIndex == normalizedReference.length() - 1) {
            return CaravanIdentifierResult.failure(CaravanIdentifierResult.FailureReason.NOT_FOUND, reference);
        }

        String ownerName = normalizedReference.substring(0, separatorIndex).trim();
        String ownerScopedReference = normalizedReference.substring(separatorIndex + 1).trim();
        if (ownerName.isEmpty()) {
            return CaravanIdentifierResult.failure(CaravanIdentifierResult.FailureReason.OWNER_NOT_FOUND, reference, ownerName, null);
        }

        List<CaravanRecord> ownerCaravans = caravanService.getCaravansByOwnerName(ownerName);
        if (ownerCaravans.isEmpty()) {
            return CaravanIdentifierResult.failure(CaravanIdentifierResult.FailureReason.OWNER_HAS_NO_CARAVANS, reference, ownerName, null);
        }

        CaravanIdentifierResult ownerScopedResult = resolveOwnerScoped(ownerCaravans, ownerScopedReference);
        if (ownerScopedResult.success()) {
            return ownerScopedResult;
        }
        return CaravanIdentifierResult.failure(
            ownerScopedResult.failureReason(),
            reference,
            ownerName,
            ownerScopedResult.index()
        );
    }

    public void sendFailure(CommandSender sender, CaravanIdentifierResult result) {
        String input = result.input() == null ? "" : result.input();
        String owner = result.owner() == null ? "" : result.owner();
        String index = result.index() == null ? input : String.valueOf(result.index());
        Map<String, String> placeholders = Map.of(
            "input", input,
            "owner", owner,
            "index", index,
            "name", input,
            "id", input
        );

        switch (result.failureReason()) {
            case AMBIGUOUS_SHORT_ID -> messageService.send(sender, "ambiguous-id");
            case INVALID_INDEX -> messageService.send(sender, "caravan-invalid-index", placeholders);
            case AMBIGUOUS_NAME -> messageService.send(sender, "caravan-ambiguous-name", placeholders);
            case NAME_NOT_FOUND -> messageService.send(sender, "caravan-name-not-found", placeholders);
            case OWNER_NOT_FOUND -> messageService.send(sender, "caravan-owner-not-found", placeholders);
            case OWNER_HAS_NO_CARAVANS -> messageService.send(sender, "caravan-owner-has-no-caravans", placeholders);
            case NOT_FOUND -> messageService.send(sender, "caravan-not-found", placeholders);
        }
    }

    private CaravanIdentifierResult resolveOwnerScoped(List<CaravanRecord> candidates, String reference) {
        String normalizedReference = normalize(reference);
        if (normalizedReference == null) {
            return CaravanIdentifierResult.failure(CaravanIdentifierResult.FailureReason.NOT_FOUND, reference);
        }

        CaravanIdentifierResult uuidScopedResult = resolveUuidScoped(candidates, normalizedReference);
        if (uuidScopedResult.success() || uuidScopedResult.failureReason() == CaravanIdentifierResult.FailureReason.AMBIGUOUS_SHORT_ID) {
            return uuidScopedResult;
        }

        Integer index = parsePositiveIndex(normalizedReference);
        if (index != null) {
            if (index < 1 || index > candidates.size()) {
                return CaravanIdentifierResult.failure(CaravanIdentifierResult.FailureReason.INVALID_INDEX, reference, null, index);
            }
            return CaravanIdentifierResult.success(candidates.get(index - 1), reference);
        }

        List<CaravanRecord> nameMatches = candidates.stream()
            .filter(caravan -> caravan.name().equalsIgnoreCase(normalizedReference))
            .toList();
        if (nameMatches.isEmpty()) {
            return CaravanIdentifierResult.failure(CaravanIdentifierResult.FailureReason.NAME_NOT_FOUND, reference);
        }
        if (nameMatches.size() > 1) {
            return CaravanIdentifierResult.failure(CaravanIdentifierResult.FailureReason.AMBIGUOUS_NAME, reference);
        }
        return CaravanIdentifierResult.success(nameMatches.getFirst(), reference);
    }

    private CaravanIdentifierResult resolveUuidScoped(List<CaravanRecord> candidates, String normalizedReference) {
        UUID fullUuid = tryParseUuid(normalizedReference);
        if (fullUuid != null) {
            return candidates.stream()
                .filter(caravan -> caravan.id().equals(fullUuid))
                .findFirst()
                .map(caravan -> CaravanIdentifierResult.success(caravan, normalizedReference))
                .orElse(CaravanIdentifierResult.failure(CaravanIdentifierResult.FailureReason.NOT_FOUND, normalizedReference));
        }

        if (normalizedReference.length() == 8) {
            List<CaravanRecord> shortMatches = candidates.stream()
                .filter(caravan -> caravan.id().toString().regionMatches(true, 0, normalizedReference, 0, 8))
                .toList();
            if (shortMatches.size() == 1) {
                return CaravanIdentifierResult.success(shortMatches.getFirst(), normalizedReference);
            }
            if (shortMatches.size() > 1) {
                return CaravanIdentifierResult.failure(CaravanIdentifierResult.FailureReason.AMBIGUOUS_SHORT_ID, normalizedReference);
            }
        }

        return CaravanIdentifierResult.failure(CaravanIdentifierResult.FailureReason.NOT_FOUND, normalizedReference);
    }

    private UUID tryParseUuid(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Integer parsePositiveIndex(String input) {
        if (input.chars().allMatch(Character::isDigit)) {
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException exception) {
                return Integer.MAX_VALUE;
            }
        }
        return null;
    }

    private String normalize(String reference) {
        if (reference == null) {
            return null;
        }
        String normalized = reference.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
