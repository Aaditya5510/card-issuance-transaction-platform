package com.cardplatform.infrastructure.outbox.service;

import com.cardplatform.infrastructure.outbox.OutboxEvent;
import com.cardplatform.infrastructure.outbox.model.OutboxStatus;
import com.cardplatform.infrastructure.outbox.repository.OutboxJpaRepository;
import com.cardplatform.infrastructure.persistence.entity.OutboxEventJpaEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Production implementation of the Transactional Outbox Pattern service.
 * Persists domain events directly into the database within the caller's active Spring @Transactional boundary,
 * eliminating dual-write anomalies and guaranteeing zero data loss.
 */
@Service
public class OutboxServiceImpl implements OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxServiceImpl.class);

    private final OutboxJpaRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxServiceImpl(OutboxJpaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper != null ? objectMapper.copy().findAndRegisterModules() : new ObjectMapper().findAndRegisterModules();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordEvent(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        recordEvent(aggregateType, aggregateId != null ? aggregateId.toString() : null, eventType, payload);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public OutboxEvent recordEvent(String aggregateType, String aggregateId, String eventType, Object payload) {
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("aggregateType is required");
        }
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId is required");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }

        String jsonPayload;
        if (payload instanceof String strPayload) {
            jsonPayload = strPayload;
        } else {
            try {
                jsonPayload = objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize outbox event payload: aggregateType={}, aggregateId={}, eventType={}",
                        aggregateType, aggregateId, eventType, e);
                throw new IllegalStateException("Failed to serialize outbox event payload to JSON", e);
            }
        }

        Instant now = Instant.now();
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setAggregateType(aggregateType);
        entity.setAggregateId(aggregateId);
        entity.setEventType(eventType);
        entity.setPayload(jsonPayload);
        entity.setStatus(OutboxStatus.PENDING);
        entity.setRetryCount(0);
        entity.setNextAttemptAt(now);
        entity.setCreatedAt(now);

        OutboxEventJpaEntity saved = repository.save(entity);
        log.info("Transactional outbox event recorded: id={}, aggregateType={}, aggregateId={}, eventType={}",
                saved.getId(), aggregateType, aggregateId, eventType);

        return new OutboxEvent(
                saved.getId(),
                saved.getAggregateType(),
                saved.getAggregateId(),
                saved.getEventType(),
                saved.getPayload(),
                saved.getStatus(),
                saved.getRetryCount(),
                saved.getNextAttemptAt(),
                saved.getCreatedAt(),
                saved.getPublishedAt()
        );
    }
}
