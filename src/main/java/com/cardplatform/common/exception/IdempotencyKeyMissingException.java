package com.cardplatform.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the required Idempotency-Key header is missing on state-mutating endpoints.
 */
public class IdempotencyKeyMissingException extends BaseException {

    public IdempotencyKeyMissingException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ErrorCode.IDEMPOTENCY_KEY_MISSING);
    }
}
