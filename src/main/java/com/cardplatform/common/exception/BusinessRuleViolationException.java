package com.cardplatform.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a business rule or invariant check fails in the domain/application layer.
 */
public class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    public BusinessRuleViolationException(String message, ErrorCode errorCode) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, errorCode);
    }

    public BusinessRuleViolationException(String message, HttpStatus status, ErrorCode errorCode) {
        super(message, status, errorCode);
    }
}
