package com.cardplatform.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an operation is attempted on a frozen card.
 */
public class CardFrozenException extends BusinessRuleViolationException {

    public CardFrozenException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.CARD_INACTIVE);
    }
}
