package com.cardplatform.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an idempotency key is reused with a different request payload body.
 */
public class IdempotencyPayloadMismatchException extends BaseException {

    public IdempotencyPayloadMismatchException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.IDEMPOTENCY_CONFLICT);
    }
}
