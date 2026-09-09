package com.cardplatform.domain.transaction.model;

/**
 * Lifecycle states of a Transaction and Authorization in the payment processing engine.
 */
public enum TransactionStatus {
    PENDING,
    APPROVED,
    DECLINED,
    SETTLED,
    EXPIRED,
    VOIDED,
    // Aliases retained for backward compatibility with existing components
    AUTHORIZED,
    CAPTURED,
    REVERSED
}
