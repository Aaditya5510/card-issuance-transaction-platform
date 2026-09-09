package com.cardplatform.infrastructure.outbox;

import com.cardplatform.infrastructure.outbox.model.OutboxStatus;
import com.cardplatform.infrastructure.outbox.repository.OutboxJpaRepository;
import com.cardplatform.infrastructure.persistence.entity.OutboxEventJpaEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class JpaOutboxRepositoryAdapter implements OutboxRepository {

    private final OutboxJpaRepository jpaRepository;

    public JpaOutboxRepositoryAdapter(OutboxJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(OutboxEvent outboxEvent) {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setId(outboxEvent.id());
        entity.setAggregateType(outboxEvent.aggregateType());
        entity.setAggregateId(outboxEvent.aggregateId());
        entity.setEventType(outboxEvent.eventType());
        entity.setPayload(outboxEvent.payload());
        entity.setStatus(outboxEvent.status() != null ? outboxEvent.status() : OutboxStatus.PENDING);
        entity.setRetryCount(outboxEvent.retryCount());
        entity.setNextAttemptAt(outboxEvent.nextAttemptAt() != null ? outboxEvent.nextAttemptAt() : Instant.now());
        entity.setCreatedAt(outboxEvent.createdAt() != null ? outboxEvent.createdAt() : Instant.now());
        entity.setPublishedAt(outboxEvent.publishedAt());
        jpaRepository.save(entity);
    }

    @Override
    public List<OutboxEvent> findPendingEvents(int limit) {
        return jpaRepository.findPendingEvents(OutboxStatus.PENDING, Instant.now(), PageRequest.of(0, limit))
                .stream()
                .map(e -> new OutboxEvent(
                        e.getId(),
                        e.getAggregateType(),
                        e.getAggregateId(),
                        e.getEventType(),
                        e.getPayload(),
                        e.getStatus(),
                        e.getRetryCount() != null ? e.getRetryCount() : 0,
                        e.getNextAttemptAt(),
                        e.getCreatedAt(),
                        e.getPublishedAt()
                ))
                .toList();
    }
}
