package com.cardplatform.domain.ledger.model;

/**
 * Double-Entry Bookkeeping leg direction (DEBIT or CREDIT).
 */
public enum PostingType {
    DEBIT,
    CREDIT;

    public boolean isDebit() {
        return this == DEBIT;
    }

    public boolean isCredit() {
        return this == CREDIT;
    }

    public PostingType opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
