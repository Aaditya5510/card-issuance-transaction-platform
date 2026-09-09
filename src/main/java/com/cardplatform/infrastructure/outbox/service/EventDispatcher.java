package com.cardplatform.infrastructure.outbox.service;

import com.cardplatform.infrastructure.outbox.OutboxEvent;

/**
 * Port contract for forwarding transactional outbox events to downstream message brokers (e.g. Kafka, RabbitMQ, EventBridge).
 */
public interface EventDispatcher {

    void dispatch(OutboxEvent event) throws Exception;
}
