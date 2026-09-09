package com.cardplatform.infrastructure.idempotency;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain record representing an atomic HTTP Idempotency key and its cached execution state.
 */
public record IdempotencyRecord(
        UUID id,
        String idempotencyKey,
        String requestHash,
        String clientOrAccountId,
        IdempotencyStatus status,
        Integer responseCode,
        String responseBody,
        Instant createdAt,
        Instant expiresAt,
        Instant updatedAt
) {
    public static IdempotencyRecord inProgress(String idempotencyKey, String requestHash, String clientOrAccountId, Instant expiresAt) {
        Instant now = Instant.now();
        return new IdempotencyRecord(
                UUID.randomUUID(),
                idempotencyKey,
                requestHash,
                clientOrAccountId,
                IdempotencyStatus.IN_PROGRESS,
                null,
                null,
                now,
                expiresAt != null ? expiresAt : now.plusSeconds(24 * 3600), // 24-hour default TTL
                now
        );
    }

    public static IdempotencyRecord pending(String idempotencyKey, String requestHash) {
        return inProgress(idempotencyKey, requestHash, null, null);
    }

    public boolean isInProgress() {
        return status == IdempotencyStatus.IN_PROGRESS;
    }

    public boolean isResolved() {
        return status == IdempotencyStatus.RESOLVED;
    }

    public boolean isFailed() {
        return status == IdempotencyStatus.FAILED;
    }

    public boolean hasMatchingHash(String otherHash) {
        return this.requestHash != null && this.requestHash.equalsIgnoreCase(otherHash);
    }

    public String key() {
        return idempotencyKey;
    }

    public Integer responseStatus() {
        return responseCode;
    }
}
