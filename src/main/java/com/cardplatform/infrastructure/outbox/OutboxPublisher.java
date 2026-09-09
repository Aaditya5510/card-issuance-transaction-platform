package com.cardplatform.infrastructure.outbox;

/**
 * Port contract for publishing domain events through the Transactional Outbox pattern.
 */
public interface OutboxPublisher {

    OutboxEvent publish(String aggregateType, String aggregateId, String eventType, String jsonPayload);
}
