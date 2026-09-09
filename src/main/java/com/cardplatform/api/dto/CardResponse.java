package com.cardplatform.api.dto;

import com.cardplatform.domain.card.model.Card;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Payment card details and lifecycle state")
public record CardResponse(
        @Schema(description = "Card unique identifier UUID", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID id,

        @Schema(description = "Associated funding account UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID accountId,

        @Schema(description = "Tokenized reference for zero-PCI card usage", example = "tok_visa_debit_9999")
        String cardToken,

        @Schema(description = "Masked PAN for safe display", example = "**** **** **** 4242")
        String maskedPan,

        @Schema(description = "Expiration month (1-12)", example = "12")
        int expiryMonth,

        @Schema(description = "Expiration four-digit year", example = "2030")
        int expiryYear,

        @Schema(description = "Card lifecycle status: ACTIVE, FROZEN, TERMINATED", example = "ACTIVE")
        String status,

        @Schema(description = "Card creation timestamp in UTC ISO format", example = "2026-09-09T10:15:30.000Z")
        Instant createdAt
) {
    public static CardResponse fromDomain(Card card) {
        return new CardResponse(
                card.getId(),
                card.getAccountId(),
                card.getCardToken(),
                card.getMaskedPan(),
                card.getExpiryMonth(),
                card.getExpiryYear(),
                card.getStatus().name(),
                card.getCreatedAt()
        );
    }
}
