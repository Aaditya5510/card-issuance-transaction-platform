package com.cardplatform.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a card transaction exceeds spending limits (per-transaction or daily limit).
 */
public class CardLimitExceededException extends BusinessRuleViolationException {

    public CardLimitExceededException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.CARD_LIMIT_EXCEEDED);
    }
}
