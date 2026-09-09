package com.cardplatform.api.dto;

import com.cardplatform.domain.transaction.model.AuthorizationHold;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Real-time Two-Phase Card Authorization Request")
public record AuthorizeTransactionRequest(
        @Schema(description = "Payment Card UUID (provide cardId or cardToken)", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
        UUID cardId,

        @Schema(description = "Payment Card Token reference", example = "tok_visa_debit_9999")
        String cardToken,

        @Schema(description = "Transaction amount to authorize and reserve as hold", example = "100.00")
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than 0")
        BigDecimal amount,

        @Schema(description = "ISO-4217 three-letter currency code", example = "USD")
        @NotBlank(message = "currency is required")
        String currency,

        @Schema(description = "Merchant name or description", example = "Amazon Marketplace")
        @NotBlank(message = "merchantName is required")
        String merchantName,

        @Schema(description = "Whether the transaction originates online (Card-Not-Present)", example = "true")
        boolean online,

        @Schema(description = "Whether the transaction is an ATM cash withdrawal", example = "false")
        boolean atm,

        @Schema(description = "Whether the transaction is international / cross-border", example = "false")
        boolean international
) {
    @Schema(description = "Authorization Hold Confirmation Response")
    public record Response(
            @Schema(description = "Authorization Hold unique UUID", example = "7e1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb99")
            UUID authorizationId,

            @Schema(description = "Authorized Card UUID", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
            UUID cardId,

            @Schema(description = "Funding Account UUID where available balance is held", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            UUID accountId,

            @Schema(description = "Reserved hold amount", example = "100.0000")
            BigDecimal amount,

            @Schema(description = "ISO-4217 Currency Code", example = "USD")
            String currency,

            @Schema(description = "Merchant Identifier / Name", example = "Amazon Marketplace")
            String merchantName,

            @Schema(description = "Authorization Status: AUTHORIZED, SETTLED, VOIDED, EXPIRED", example = "AUTHORIZED")
            String status,

            @Schema(description = "UTC ISO Timestamp when active hold will expire if uncaptured", example = "2026-09-16T10:15:30.000Z")
            Instant expiresAt,

            @Schema(description = "Authorization creation timestamp in UTC ISO format", example = "2026-09-09T10:15:30.000Z")
            Instant createdAt
    ) {
        public static Response fromDomain(AuthorizationHold hold) {
            return new Response(
                    hold.getId(),
                    hold.getCardId(),
                    hold.getAccountId(),
                    hold.getAmount().getAmount(),
                    hold.getAmount().getCurrency(),
                    hold.getMerchantName(),
                    hold.getStatus().name(),
                    hold.getExpiresAt(),
                    hold.getCreatedAt()
            );
        }
    }
}
