package com.cardplatform.infrastructure.outbox.service;

import com.cardplatform.infrastructure.outbox.OutboxEvent;
import com.cardplatform.infrastructure.outbox.model.OutboxStatus;
import com.cardplatform.infrastructure.outbox.repository.OutboxJpaRepository;
import com.cardplatform.infrastructure.persistence.entity.OutboxEventJpaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Background worker implementing at-least-once asynchronous event dispatching
 * with exponential backoff with jitter and Dead Letter Queue (DLQ) transitions.
 */
@Service
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxJpaRepository repository;
    private final EventDispatcher eventDispatcher;
    private final int maxRetries;
    private final int baseBackoffSeconds;
    private final int batchSize;

    @org.springframework.beans.factory.annotation.Autowired
    public OutboxEventPublisher(
            OutboxJpaRepository repository,
            EventDispatcher eventDispatcher,
            @Value("${outbox.publisher.max-retries:5}") int maxRetries,
            @Value("${outbox.publisher.base-backoff-seconds:2}") int baseBackoffSeconds,
            @Value("${outbox.publisher.batch-size:50}") int batchSize) {
        this.repository = repository;
        this.eventDispatcher = eventDispatcher;
        this.maxRetries = maxRetries;
        this.baseBackoffSeconds = baseBackoffSeconds;
        this.batchSize = batchSize;
    }

    public OutboxEventPublisher(OutboxJpaRepository repository, EventDispatcher eventDispatcher) {
        this(repository, eventDispatcher, 5, 2, 50);
    }

    /**
     * Periodic scheduled poller.
     */
    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:2000}")
    public void publishPendingEvents() {
        processPendingBatch();
    }

    /**
     * Executes batch processing of pending outbox events.
     * Returns total events processed.
     */
    public int processPendingBatch() {
        Instant now = Instant.now();
        List<OutboxEventJpaEntity> pendingEvents = repository.findPendingEvents(
                OutboxStatus.PENDING,
                now,
                PageRequest.of(0, batchSize)
        );

        if (pendingEvents.isEmpty()) {
            return 0;
        }

        log.debug("Polling outbox: found {} pending events for dispatch", pendingEvents.size());
        int processedCount = 0;

        for (OutboxEventJpaEntity entity : pendingEvents) {
            processSingleEvent(entity);
            processedCount++;
        }

        return processedCount;
    }

    /**
     * Dispatches a single outbox event, transitioning to PUBLISHED on success,
     * or calculating exponential backoff / DEAD_LETTER on failure.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleEvent(OutboxEventJpaEntity entity) {
        OutboxEvent domainEvent = new OutboxEvent(
                entity.getId(),
                entity.getAggregateType(),
                entity.getAggregateId(),
                entity.getEventType(),
                entity.getPayload(),
                entity.getStatus(),
                entity.getRetryCount(),
                entity.getNextAttemptAt(),
                entity.getCreatedAt(),
                entity.getPublishedAt()
        );

        try {
            eventDispatcher.dispatch(domainEvent);

            entity.setStatus(OutboxStatus.PUBLISHED);
            entity.setPublishedAt(Instant.now());
            repository.save(entity);

            log.info("Successfully published outbox event: id={}, aggregateType={}, aggregateId={}, eventType={}",
                    entity.getId(), entity.getAggregateType(), entity.getAggregateId(), entity.getEventType());
        } catch (Exception e) {
            int newRetryCount = (entity.getRetryCount() != null ? entity.getRetryCount() : 0) + 1;
            entity.setRetryCount(newRetryCount);

            if (newRetryCount >= maxRetries) {
                entity.setStatus(OutboxStatus.DEAD_LETTER);
                log.error("CRITICAL: Outbox event id={} exceeded max retries ({}/{}). Transitioned to DEAD_LETTER (DLQ). Aggregate={}, Event={}",
                        entity.getId(), newRetryCount, maxRetries, entity.getAggregateType(), entity.getEventType(), e);
            } else {
                long exponentialDelay = (long) Math.pow(2, newRetryCount) * baseBackoffSeconds;
                long jitter = ThreadLocalRandom.current().nextLong(1, 3);
                long totalDelaySeconds = exponentialDelay + jitter;
                Instant nextAttempt = Instant.now().plusSeconds(totalDelaySeconds);

                entity.setStatus(OutboxStatus.PENDING);
                entity.setNextAttemptAt(nextAttempt);

                log.warn("Failed to dispatch outbox event id={}, retry={}/{}, scheduling next attempt in {}s at {}. Reason: {}",
                        entity.getId(), newRetryCount, maxRetries, totalDelaySeconds, nextAttempt, e.getMessage());
            }

            repository.save(entity);
        }
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public int getBaseBackoffSeconds() {
        return baseBackoffSeconds;
    }

    public int getBatchSize() {
        return batchSize;
    }
}
