package com.cardplatform.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a double-entry bookkeeping invariant fails (Total Debits != Total Credits).
 */
public class LedgerImbalanceException extends DomainException {

    public LedgerImbalanceException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.LEDGER_UNBALANCED);
    }

    public LedgerImbalanceException(String message, ErrorCode errorCode) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, errorCode);
    }
}
