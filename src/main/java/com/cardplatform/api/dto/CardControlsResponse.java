package com.cardplatform.api.dto;

import com.cardplatform.domain.card.model.CardControls;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Card spending limits and channel authorization controls")
public record CardControlsResponse(
        @Schema(description = "Controls configuration UUID", example = "5f2deb4d-3b7d-4bad-9bdd-2b0d7b3dcb7e")
        UUID id,

        @Schema(description = "Associated card UUID", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID cardId,

        @Schema(description = "Daily aggregate spend limit", example = "50000.0000")
        BigDecimal dailyLimit,

        @Schema(description = "Per-transaction single swipe spend limit", example = "10000.0000")
        BigDecimal perTxLimit,

        @Schema(description = "E-Commerce / Card-Not-Present transaction toggle", example = "true")
        boolean onlineEnabled,

        @Schema(description = "Automated Teller Machine cash withdrawal toggle", example = "true")
        boolean atmEnabled,

        @Schema(description = "Cross-border / International currency transaction toggle", example = "false")
        boolean internationalEnabled
) {
    public static CardControlsResponse fromDomain(CardControls controls) {
        return new CardControlsResponse(
                controls.getId(),
                controls.getCardId(),
                controls.getDailyLimit().getAmount(),
                controls.getPerTxLimit().getAmount(),
                controls.isOnlineEnabled(),
                controls.isAtmEnabled(),
                controls.isInternationalEnabled()
        );
    }
}
