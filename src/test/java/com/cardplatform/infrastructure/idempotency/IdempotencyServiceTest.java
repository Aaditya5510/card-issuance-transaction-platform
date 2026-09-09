package com.cardplatform.infrastructure.idempotency;

import com.cardplatform.common.exception.IdempotencyInProgressException;
import com.cardplatform.common.exception.IdempotencyPayloadMismatchException;
import com.cardplatform.infrastructure.persistence.entity.IdempotencyRecordJpaEntity;
import com.cardplatform.infrastructure.security.SecurityKeyHashingUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private SpringDataIdempotencyRepository repository;

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(repository);
    }

    @Test
    @DisplayName("Should successfully reserve new idempotency key with IN_PROGRESS status")
    void shouldReserveNewKey() {
        String key = "idemp_test_001";
        String payload = "{\"amount\":1000,\"currency\":\"INR\"}";
        String expectedHash = SecurityKeyHashingUtil.sha256Hex(payload);

        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(repository.save(any(IdempotencyRecordJpaEntity.class))).thenAnswer(i -> i.getArgument(0));

        Optional<IdempotencyRecord> result = idempotencyService.checkOrReserve(key, payload);

        assertThat(result).isEmpty();

        ArgumentCaptor<IdempotencyRecordJpaEntity> captor = ArgumentCaptor.forClass(IdempotencyRecordJpaEntity.class);
        verify(repository).save(captor.capture());
        IdempotencyRecordJpaEntity saved = captor.getValue();
        assertThat(saved.getIdempotencyKey()).isEqualTo(key);
        assertThat(saved.getRequestHash()).isEqualTo(expectedHash);
        assertThat(saved.getStatus()).isEqualTo(IdempotencyStatus.IN_PROGRESS.name());
        assertThat(saved.getExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("Should return cached record when key is RESOLVED and payload hash matches")
    void shouldReturnCachedRecordWhenResolved() {
        String key = "idemp_test_002";
        String payload = "{\"amount\":2500,\"currency\":\"INR\"}";
        String expectedHash = SecurityKeyHashingUtil.sha256Hex(payload);

        IdempotencyRecordJpaEntity existing = new IdempotencyRecordJpaEntity();
        existing.setId(UUID.randomUUID());
        existing.setIdempotencyKey(key);
        existing.setRequestHash(expectedHash);
        existing.setStatus(IdempotencyStatus.RESOLVED.name());
        existing.setResponseCode(200);
        existing.setResponseBody("{\"status\":\"APPROVED\"}");

        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        Optional<IdempotencyRecord> result = idempotencyService.checkOrReserve(key, payload);

        assertThat(result).isPresent();
        IdempotencyRecord record = result.get();
        assertThat(record.status()).isEqualTo(IdempotencyStatus.RESOLVED);
        assertThat(record.responseCode()).isEqualTo(200);
        assertThat(record.responseBody()).isEqualTo("{\"status\":\"APPROVED\"}");
    }

    @Test
    @DisplayName("Should throw IdempotencyInProgressException when duplicate in-flight request arrives")
    void shouldThrowWhenRequestInProgress() {
        String key = "idemp_test_003";
        String payload = "{\"amount\":500,\"currency\":\"INR\"}";
        String expectedHash = SecurityKeyHashingUtil.sha256Hex(payload);

        IdempotencyRecordJpaEntity existing = new IdempotencyRecordJpaEntity();
        existing.setId(UUID.randomUUID());
        existing.setIdempotencyKey(key);
        existing.setRequestHash(expectedHash);
        existing.setStatus(IdempotencyStatus.IN_PROGRESS.name());

        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.checkOrReserve(key, payload))
                .isInstanceOf(IdempotencyInProgressException.class)
                .hasMessageContaining("currently in progress");
    }

    @Test
    @DisplayName("Should throw IdempotencyPayloadMismatchException when key is reused with mismatched payload")
    void shouldThrowWhenPayloadMismatch() {
        String key = "idemp_test_004";
        String payload1 = "{\"amount\":500,\"currency\":\"INR\"}";
        String payload2 = "{\"amount\":9999,\"currency\":\"INR\"}";
        String hash1 = SecurityKeyHashingUtil.sha256Hex(payload1);

        IdempotencyRecordJpaEntity existing = new IdempotencyRecordJpaEntity();
        existing.setId(UUID.randomUUID());
        existing.setIdempotencyKey(key);
        existing.setRequestHash(hash1);
        existing.setStatus(IdempotencyStatus.RESOLVED.name());

        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> idempotencyService.checkOrReserve(key, payload2))
                .isInstanceOf(IdempotencyPayloadMismatchException.class)
                .hasMessageContaining("different request payload");
    }

    @Test
    @DisplayName("Should update status to RESOLVED with response code and body upon resolution")
    void shouldResolveRecord() {
        String key = "idemp_test_005";
        IdempotencyRecordJpaEntity existing = new IdempotencyRecordJpaEntity();
        existing.setId(UUID.randomUUID());
        existing.setIdempotencyKey(key);
        existing.setStatus(IdempotencyStatus.IN_PROGRESS.name());

        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        idempotencyService.resolve(key, 201, "{\"transactionId\":\"tx-123\"}");

        assertThat(existing.getStatus()).isEqualTo(IdempotencyStatus.RESOLVED.name());
        assertThat(existing.getResponseCode()).isEqualTo(201);
        assertThat(existing.getResponseBody()).isEqualTo("{\"transactionId\":\"tx-123\"}");
        verify(repository).save(existing);
    }

    @Test
    @DisplayName("Should mark record as FAILED when downstream execution fails")
    void shouldMarkFailed() {
        String key = "idemp_test_006";
        IdempotencyRecordJpaEntity existing = new IdempotencyRecordJpaEntity();
        existing.setId(UUID.randomUUID());
        existing.setIdempotencyKey(key);
        existing.setStatus(IdempotencyStatus.IN_PROGRESS.name());

        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        idempotencyService.markFailed(key);

        assertThat(existing.getStatus()).isEqualTo(IdempotencyStatus.FAILED.name());
        verify(repository).save(existing);
    }

    @Test
    @DisplayName("Should allow retry when previously failed key is reused with matching payload")
    void shouldAllowRetryWhenFailed() {
        String key = "idemp_test_007";
        String payload = "{\"amount\":100}";
        String hash = SecurityKeyHashingUtil.sha256Hex(payload);

        IdempotencyRecordJpaEntity existing = new IdempotencyRecordJpaEntity();
        existing.setId(UUID.randomUUID());
        existing.setIdempotencyKey(key);
        existing.setRequestHash(hash);
        existing.setStatus(IdempotencyStatus.FAILED.name());

        when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        Optional<IdempotencyRecord> result = idempotencyService.checkOrReserve(key, payload);

        assertThat(result).isEmpty();
        assertThat(existing.getStatus()).isEqualTo(IdempotencyStatus.IN_PROGRESS.name());
        verify(repository).save(existing);
    }
}
