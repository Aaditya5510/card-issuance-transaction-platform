package com.cardplatform.infrastructure.outbox.service;

import com.cardplatform.infrastructure.outbox.OutboxEvent;

import java.util.UUID;

/**
 * Port contract for the Transactional Outbox Pattern service.
 */
public interface OutboxService {

    void recordEvent(String aggregateType, UUID aggregateId, String eventType, Object payload);

    OutboxEvent recordEvent(String aggregateType, String aggregateId, String eventType, Object payload);
}
