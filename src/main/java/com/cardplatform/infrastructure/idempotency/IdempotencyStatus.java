package com.cardplatform.infrastructure.idempotency;

/**
 * State machine states for HTTP Idempotency tracking.
 */
public enum IdempotencyStatus {
    IN_PROGRESS,
    RESOLVED,
    FAILED;

    public static IdempotencyStatus fromString(String value) {
        if (value == null) {
            return IN_PROGRESS;
        }
        return switch (value.toUpperCase()) {
            case "PROCESSING", "IN_PROGRESS" -> IN_PROGRESS;
            case "COMPLETED", "RESOLVED" -> RESOLVED;
            case "FAILED", "ERROR" -> FAILED;
            default -> valueOf(value.toUpperCase());
        };
    }
}
