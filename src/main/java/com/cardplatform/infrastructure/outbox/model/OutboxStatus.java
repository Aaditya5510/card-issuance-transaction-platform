package com.cardplatform.infrastructure.outbox.model;

/**
 * State machine for Transactional Outbox Pattern events.
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED,
    DEAD_LETTER
}
