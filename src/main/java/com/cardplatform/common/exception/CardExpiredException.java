package com.cardplatform.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a card transaction is attempted on an expired card.
 */
public class CardExpiredException extends BusinessRuleViolationException {

    public CardExpiredException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.TRANSACTION_NOT_PERMITTED);
    }
}
