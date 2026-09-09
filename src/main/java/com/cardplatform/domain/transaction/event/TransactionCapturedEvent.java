package com.cardplatform.domain.transaction.event;

import com.cardplatform.common.money.MonetaryAmount;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event emitted whenever an authorized transaction is successfully captured and settled.
 */
public record TransactionCapturedEvent(
        UUID transactionId,
        UUID accountId,
        MonetaryAmount capturedAmount,
        Instant settledAt
) {}
