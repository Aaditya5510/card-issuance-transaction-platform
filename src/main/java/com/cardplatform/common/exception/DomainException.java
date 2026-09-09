package com.cardplatform.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for all business domain invariant violations.
 */
public class DomainException extends BaseException {

    public DomainException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.DOMAIN_ERROR);
    }

    public DomainException(String message, ErrorCode errorCode) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, errorCode);
    }

    public DomainException(String message, HttpStatus status, ErrorCode errorCode) {
        super(message, status, errorCode);
    }
}
