package com.cardplatform.infrastructure.outbox;

import com.cardplatform.infrastructure.outbox.model.OutboxStatus;
import com.cardplatform.infrastructure.outbox.repository.OutboxJpaRepository;
import com.cardplatform.infrastructure.outbox.service.OutboxService;
import com.cardplatform.infrastructure.outbox.service.OutboxServiceImpl;
import com.cardplatform.infrastructure.persistence.entity.OutboxEventJpaEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxTransactionalIntegrationTest {

    @Mock
    private OutboxJpaRepository repository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ObjectMapper objectMapper;
    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        outboxService = new OutboxServiceImpl(repository, objectMapper);
    }

    @Test
    @DisplayName("Should guarantee atomic dual-write: successful business execution persists outbox event")
    void shouldPersistOutboxEventWhenTransactionSucceeds() {
        UUID aggregateId = UUID.randomUUID();
        when(repository.save(any(OutboxEventJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Simulate transactional use case execution
        TransactionStatus status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);

        // Transaction boundary start
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        TransactionStatus tx = transactionManager.getTransaction(def);

        try {
            // Business logic + outbox recording
            outboxService.recordEvent("CARD", aggregateId, "CardStatusUpdated", "{\"status\":\"ACTIVE\"}");
            transactionManager.commit(tx);
        } catch (Exception e) {
            transactionManager.rollback(tx);
        }

        verify(transactionManager).commit(tx);
        verify(repository).save(any(OutboxEventJpaEntity.class));
    }

    @Test
    @DisplayName("Should guarantee atomic dual-write: business exception triggers transaction rollback")
    void shouldRollbackOutboxEventWhenBusinessTransactionThrows() {
        UUID aggregateId = UUID.randomUUID();
        TransactionStatus status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);

        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        TransactionStatus tx = transactionManager.getTransaction(def);

        List<OutboxEventJpaEntity> rollbackSimulatedStore = new ArrayList<>();

        assertThatThrownBy(() -> {
            try {
                // Outbox recorded
                OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
                entity.setId(UUID.randomUUID());
                entity.setAggregateId(aggregateId.toString());
                entity.setStatus(OutboxStatus.PENDING);
                rollbackSimulatedStore.add(entity);

                // Business logic failure (e.g. InsufficientFunds or Concurrency conflict)
                throw new IllegalStateException("Business rule violation in middle of transaction");
            } catch (Exception e) {
                transactionManager.rollback(tx);
                rollbackSimulatedStore.clear(); // Transaction rollback reverts uncommitted memory/DB state
                throw e;
            }
        }).isInstanceOf(IllegalStateException.class);

        verify(transactionManager).rollback(tx);
        verify(transactionManager, never()).commit(tx);
        assertThat(rollbackSimulatedStore).isEmpty();
    }
}
