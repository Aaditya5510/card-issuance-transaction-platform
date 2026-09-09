package com.cardplatform.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an account does not have sufficient available balance for an authorization hold or debit.
 */
public class InsufficientFundsException extends BusinessRuleViolationException {

    public InsufficientFundsException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.INSUFFICIENT_FUNDS);
    }
}
