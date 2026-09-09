package com.cardplatform.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a concurrent request with the same idempotency key is already in progress.
 */
public class IdempotencyInProgressException extends BaseException {

    public IdempotencyInProgressException(String message) {
        super(message, HttpStatus.CONFLICT, ErrorCode.IDEMPOTENCY_PROCESSING);
    }
}
