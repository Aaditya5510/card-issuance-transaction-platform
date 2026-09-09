package com.cardplatform.domain.transaction.model;

/**
 * Transaction type defining the stage in the two-phase payment lifecycle.
 */
public enum TransactionType {
    AUTHORIZATION,
    CAPTURE,
    VOID,
    REFUND
}
