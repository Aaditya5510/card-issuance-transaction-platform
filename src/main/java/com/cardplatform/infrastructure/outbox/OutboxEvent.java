package com.cardplatform.infrastructure.outbox;

import com.cardplatform.infrastructure.outbox.model.OutboxStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Technical model for Transactional Outbox Pattern events (for Kafka / CDC message delivery).
 */
public record OutboxEvent(
        UUID id,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payload,
        OutboxStatus status,
        int retryCount,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant publishedAt
) {
    public static OutboxEvent pending(String aggregateType, String aggregateId, String eventType, String jsonPayload) {
        Instant now = Instant.now();
        return new OutboxEvent(
                UUID.randomUUID(),
                aggregateType,
                aggregateId,
                eventType,
                jsonPayload,
                OutboxStatus.PENDING,
                0,
                now,
                now,
                null
        );
    }

    public static OutboxEvent pending(String aggregateType, UUID aggregateId, String eventType, String jsonPayload) {
        return pending(aggregateType, aggregateId != null ? aggregateId.toString() : null, eventType, jsonPayload);
    }
}
