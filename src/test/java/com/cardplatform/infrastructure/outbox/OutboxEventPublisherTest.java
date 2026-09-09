package com.cardplatform.infrastructure.outbox;

import com.cardplatform.infrastructure.outbox.model.OutboxStatus;
import com.cardplatform.infrastructure.outbox.repository.OutboxJpaRepository;
import com.cardplatform.infrastructure.outbox.service.EventDispatcher;
import com.cardplatform.infrastructure.outbox.service.OutboxEventPublisher;
import com.cardplatform.infrastructure.persistence.entity.OutboxEventJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    private OutboxJpaRepository repository;

    @Mock
    private EventDispatcher eventDispatcher;

    private OutboxEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxEventPublisher(repository, eventDispatcher, 5, 2, 50);
    }

    @Test
    @DisplayName("Should dispatch pending events and mark them PUBLISHED with timestamp")
    void shouldPublishPendingEventsSuccessfully() throws Exception {
        UUID eventId = UUID.randomUUID();
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setId(eventId);
        entity.setAggregateType("TRANSACTION");
        entity.setAggregateId(UUID.randomUUID().toString());
        entity.setEventType("TransactionAuthorized");
        entity.setPayload("{\"amount\":100}");
        entity.setStatus(OutboxStatus.PENDING);
        entity.setRetryCount(0);
        entity.setCreatedAt(Instant.now());

        when(repository.findPendingEvents(eq(OutboxStatus.PENDING), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(entity));
        when(repository.save(any(OutboxEventJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

        int processed = publisher.processPendingBatch();

        assertThat(processed).isEqualTo(1);

        // Verify dispatcher was invoked
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(eventDispatcher).dispatch(eventCaptor.capture());
        assertThat(eventCaptor.getValue().id()).isEqualTo(eventId);

        // Verify entity state transition to PUBLISHED
        ArgumentCaptor<OutboxEventJpaEntity> entityCaptor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(repository).save(entityCaptor.capture());

        OutboxEventJpaEntity savedEntity = entityCaptor.getValue();
        assertThat(savedEntity.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(savedEntity.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should increment retryCount and apply exponential backoff when dispatch fails")
    void shouldRetryWithExponentialBackoffAndJitterOnFailure() throws Exception {
        UUID eventId = UUID.randomUUID();
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setId(eventId);
        entity.setAggregateType("LEDGER");
        entity.setAggregateId(UUID.randomUUID().toString());
        entity.setEventType("JournalEntryPosted");
        entity.setPayload("{\"legs\":2}");
        entity.setStatus(OutboxStatus.PENDING);
        entity.setRetryCount(1);
        entity.setCreatedAt(Instant.now());

        when(repository.findPendingEvents(eq(OutboxStatus.PENDING), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(entity));
        when(repository.save(any(OutboxEventJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Simulate message broker outage / connection exception
        doThrow(new RuntimeException("Kafka broker connection timeout"))
                .when(eventDispatcher).dispatch(any(OutboxEvent.class));

        Instant beforeProcessing = Instant.now();
        int processed = publisher.processPendingBatch();

        assertThat(processed).isEqualTo(1);

        ArgumentCaptor<OutboxEventJpaEntity> entityCaptor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(repository).save(entityCaptor.capture());

        OutboxEventJpaEntity savedEntity = entityCaptor.getValue();
        assertThat(savedEntity.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(savedEntity.getRetryCount()).isEqualTo(2);
        // Exponential backoff: 2^2 * 2 = 8s + jitter (1..2s) => nextAttemptAt is at least 8s into the future
        assertThat(savedEntity.getNextAttemptAt()).isAfter(beforeProcessing.plusSeconds(7));
        assertThat(savedEntity.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("Should transition event to DEAD_LETTER (DLQ) when retry count reaches maxRetries")
    void shouldTransitionToDeadLetterWhenMaxRetriesExceeded() throws Exception {
        UUID eventId = UUID.randomUUID();
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setId(eventId);
        entity.setAggregateType("TRANSACTION");
        entity.setAggregateId(UUID.randomUUID().toString());
        entity.setEventType("TransactionCaptured");
        entity.setPayload("{\"txId\":\"123\"}");
        entity.setStatus(OutboxStatus.PENDING);
        entity.setRetryCount(4); // 4th retry, next failure makes it 5 (>= maxRetries)
        entity.setCreatedAt(Instant.now());

        when(repository.findPendingEvents(eq(OutboxStatus.PENDING), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(entity));
        when(repository.save(any(OutboxEventJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

        doThrow(new RuntimeException("Permanent serialization or topic rejection"))
                .when(eventDispatcher).dispatch(any(OutboxEvent.class));

        int processed = publisher.processPendingBatch();

        assertThat(processed).isEqualTo(1);

        ArgumentCaptor<OutboxEventJpaEntity> entityCaptor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(repository).save(entityCaptor.capture());

        OutboxEventJpaEntity savedEntity = entityCaptor.getValue();
        assertThat(savedEntity.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER);
        assertThat(savedEntity.getRetryCount()).isEqualTo(5);
        assertThat(savedEntity.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("Should handle empty pending batch cleanly without throwing exceptions")
    void shouldHandleEmptyBatchGracefully() throws Exception {
        when(repository.findPendingEvents(eq(OutboxStatus.PENDING), any(Instant.class), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        int processed = publisher.processPendingBatch();

        assertThat(processed).isEqualTo(0);
        verify(eventDispatcher, never()).dispatch(any());
        verify(repository, never()).save(any());
    }
}
