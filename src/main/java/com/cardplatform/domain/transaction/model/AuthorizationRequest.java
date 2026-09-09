package com.cardplatform.domain.transaction.model;

import com.cardplatform.common.money.MonetaryAmount;

import java.util.UUID;

/**
 * Pure domain value object representing an inbound authorization request.
 */
public record AuthorizationRequest(
        String idempotencyKey,
        UUID cardId,
        String cardToken,
        MonetaryAmount amount,
        String merchantName,
        boolean isOnline,
        boolean isAtm,
        boolean isInternational
) {
    public AuthorizationRequest {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }
        if (merchantName == null || merchantName.isBlank()) {
            throw new IllegalArgumentException("Merchant name is required");
        }
    }
}
