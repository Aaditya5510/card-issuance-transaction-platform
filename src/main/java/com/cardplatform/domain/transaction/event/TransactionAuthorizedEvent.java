package com.cardplatform.domain.transaction.event;

import com.cardplatform.common.money.MonetaryAmount;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event emitted whenever a card transaction is successfully authorized and balance hold is placed.
 */
public record TransactionAuthorizedEvent(
        UUID transactionId,
        UUID cardId,
        UUID accountId,
        MonetaryAmount amount,
        String authorizationCode,
        String merchantId,
        String merchantCategoryCode,
        Instant authorizedAt,
        Instant expiresAt
) {}
