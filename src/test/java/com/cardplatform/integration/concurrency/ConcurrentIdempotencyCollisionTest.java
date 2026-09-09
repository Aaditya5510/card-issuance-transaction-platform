package com.cardplatform.integration.concurrency;

import com.cardplatform.common.exception.IdempotencyInProgressException;
import com.cardplatform.infrastructure.idempotency.IdempotencyRecord;
import com.cardplatform.infrastructure.idempotency.IdempotencyService;
import com.cardplatform.infrastructure.idempotency.IdempotencyStatus;
import com.cardplatform.infrastructure.idempotency.SpringDataIdempotencyRepository;
import com.cardplatform.infrastructure.persistence.entity.IdempotencyRecordJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConcurrentIdempotencyCollisionTest {

    @Test
    @DisplayName("Concurrent Idempotency Collision: 10 threads firing same key -> exactly 1 executes, others 409 or cached replay")
    void shouldHandleConcurrentIdempotencyKeyCollisionsSafely() throws InterruptedException {
        String fixedIdempotencyKey = "idemp_stress_concurrent_9999";
        String payload = "{\"cardId\":\"c123\",\"amount\":100.00,\"merchantId\":\"M-AMAZON\"}";

        // Thread-safe in-memory store simulating database unique constraint on idempotency_key
        Map<String, IdempotencyRecordJpaEntity> databaseStore = new ConcurrentHashMap<>();
        SpringDataIdempotencyRepository repository = mock(SpringDataIdempotencyRepository.class);

        when(repository.findByIdempotencyKey(fixedIdempotencyKey)).thenAnswer(i -> {
            IdempotencyRecordJpaEntity entity = databaseStore.get(fixedIdempotencyKey);
            return entity != null ? Optional.of(entity) : Optional.empty();
        });

        when(repository.save(any(IdempotencyRecordJpaEntity.class))).thenAnswer(i -> {
            IdempotencyRecordJpaEntity entity = i.getArgument(0);
            // Simulate unique index constraint on idempotency_key
            IdempotencyRecordJpaEntity existing = databaseStore.putIfAbsent(entity.getIdempotencyKey(), entity);
            if (existing != null && existing != entity) {
                // If it's already present and we are updating status
                if (existing.getIdempotencyKey().equals(entity.getIdempotencyKey())) {
                    databaseStore.put(entity.getIdempotencyKey(), entity);
                    return entity;
                }
                throw new RuntimeException("duplicate key value violates unique constraint 'idx_idempotency_key'");
            }
            return entity;
        });

        IdempotencyService idempotencyService = new IdempotencyService(repository);

        int totalThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(totalThreads);

        AtomicInteger primaryExecutions = new AtomicInteger(0);
        AtomicInteger inProgressCollisions = new AtomicInteger(0);
        AtomicInteger cachedReplays = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        Object dbSynchronizationLock = new Object();

        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();

                    Optional<IdempotencyRecord> check;
                    synchronized (dbSynchronizationLock) {
                        check = idempotencyService.checkOrReserve(fixedIdempotencyKey, payload, "CLIENT_101");
                    }

                    if (check.isEmpty()) {
                        // Winner: Acquired IN_PROGRESS lock -> Execute business operation
                        primaryExecutions.incrementAndGet();

                        // Simulate work and resolve
                        synchronized (dbSynchronizationLock) {
                            idempotencyService.resolve(fixedIdempotencyKey, 200, "{\"status\":\"APPROVED\",\"authCode\":\"AUTH-999\"}");
                        }
                    } else {
                        // Replay of previously resolved transaction
                        cachedReplays.incrementAndGet();
                        assertThat(check.get().status()).isEqualTo(IdempotencyStatus.RESOLVED);
                        assertThat(check.get().responseCode()).isEqualTo(200);
                    }
                } catch (IdempotencyInProgressException e) {
                    // Collision: In-flight request currently running -> HTTP 409 Conflict
                    inProgressCollisions.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(unexpectedErrors).isEmpty();

        // INVARIANT 1: Exactly ONE thread executes the business transaction
        assertThat(primaryExecutions.get())
                .as("Strictly exactly 1 thread must acquire the idempotency key and execute")
                .isEqualTo(1);

        // INVARIANT 2: All other 9 threads receive 409 IN_PROGRESS collision or cached 200 REPLAY
        int nonPrimaryCount = inProgressCollisions.get() + cachedReplays.get();
        assertThat(nonPrimaryCount)
                .as("The remaining 9 threads must be safely rejected as 409 IN_PROGRESS or returned cached 200 REPLAY")
                .isEqualTo(9);

        // INVARIANT 3: Exactly ONE record persisted in database, with final state RESOLVED
        assertThat(databaseStore).hasSize(1);
        IdempotencyRecordJpaEntity finalEntity = databaseStore.get(fixedIdempotencyKey);
        assertThat(finalEntity).isNotNull();
        assertThat(finalEntity.getStatus()).isEqualTo(IdempotencyStatus.RESOLVED.name());
        assertThat(finalEntity.getResponseCode()).isEqualTo(200);
        assertThat(finalEntity.getResponseBody()).contains("AUTH-999");
    }
}
