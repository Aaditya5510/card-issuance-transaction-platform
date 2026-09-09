package com.cardplatform.infrastructure.outbox;

import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.transaction.event.TransactionAuthorizedEvent;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxJpaRepository repository;

    private ObjectMapper objectMapper;
    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        outboxService = new OutboxServiceImpl(repository, objectMapper);
    }

    @Test
    @DisplayName("Should successfully serialize domain event and save PENDING outbox record")
    void shouldRecordDomainEventSuccessfully() {
        UUID txId = UUID.randomUUID();
        UUID cardId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.now();

        TransactionAuthorizedEvent event = new TransactionAuthorizedEvent(
                txId,
                cardId,
                accountId,
                MonetaryAmount.of(150.00, "INR"),
                "AUTH-12345",
                "MERCHANT-999",
                "5411",
                now,
                now.plusSeconds(3600)
        );

        when(repository.save(any(OutboxEventJpaEntity.class))).thenAnswer(invocation -> {
            OutboxEventJpaEntity entity = invocation.getArgument(0);
            return entity;
        });

        OutboxEvent result = outboxService.recordEvent("TRANSACTION", txId.toString(), "TransactionAuthorized", event);

        ArgumentCaptor<OutboxEventJpaEntity> captor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(repository).save(captor.capture());

        OutboxEventJpaEntity savedEntity = captor.getValue();
        assertThat(savedEntity.getId()).isNotNull();
        assertThat(savedEntity.getAggregateType()).isEqualTo("TRANSACTION");
        assertThat(savedEntity.getAggregateId()).isEqualTo(txId.toString());
        assertThat(savedEntity.getEventType()).isEqualTo("TransactionAuthorized");
        assertThat(savedEntity.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(savedEntity.getRetryCount()).isEqualTo(0);
        assertThat(savedEntity.getNextAttemptAt()).isNotNull();
        assertThat(savedEntity.getCreatedAt()).isNotNull();
        assertThat(savedEntity.getPublishedAt()).isNull();

        // Check JSON payload structure
        assertThat(savedEntity.getPayload()).contains("AUTH-12345");
        assertThat(savedEntity.getPayload()).contains("150.0000");

        assertThat(result.id()).isEqualTo(savedEntity.getId());
        assertThat(result.status()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("Should save raw JSON string payload directly without double serialization")
    void shouldHandleRawStringPayload() {
        String rawJson = "{\"key\":\"value\",\"amount\":100}";
        UUID aggregateId = UUID.randomUUID();

        when(repository.save(any(OutboxEventJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

        outboxService.recordEvent("LEDGER", aggregateId, "CustomEvent", rawJson);

        ArgumentCaptor<OutboxEventJpaEntity> captor = ArgumentCaptor.forClass(OutboxEventJpaEntity.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getPayload()).isEqualTo(rawJson);
    }

    @Test
    @DisplayName("Should reject recording when required arguments are missing")
    void shouldRejectInvalidArguments() {
        assertThatThrownBy(() -> outboxService.recordEvent(null, UUID.randomUUID(), "Type", "payload"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregateType is required");

        assertThatThrownBy(() -> outboxService.recordEvent("TYPE", (UUID) null, "Type", "payload"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregateId is required");

        assertThatThrownBy(() -> outboxService.recordEvent("TYPE", UUID.randomUUID(), null, "payload"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventType is required");

        assertThatThrownBy(() -> outboxService.recordEvent("TYPE", UUID.randomUUID(), "Type", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload is required");
    }
}
