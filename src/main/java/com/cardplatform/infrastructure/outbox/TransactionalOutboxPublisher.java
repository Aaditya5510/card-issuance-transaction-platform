package com.cardplatform.infrastructure.outbox;

import com.cardplatform.infrastructure.outbox.service.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementation for transactional enqueueing and publishing of domain events (Transactional Outbox).
 * Delegates to OutboxService.
 */
@Service
public class TransactionalOutboxPublisher implements OutboxPublisher {

    private final OutboxService outboxService;

    public TransactionalOutboxPublisher(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public OutboxEvent publish(String aggregateType, String aggregateId, String eventType, String jsonPayload) {
        return outboxService.recordEvent(aggregateType, aggregateId, eventType, jsonPayload);
    }
}
