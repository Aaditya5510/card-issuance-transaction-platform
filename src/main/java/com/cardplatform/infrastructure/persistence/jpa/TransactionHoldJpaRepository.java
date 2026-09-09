package com.cardplatform.infrastructure.persistence.jpa;

import com.cardplatform.infrastructure.persistence.entity.TransactionHoldJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionHoldJpaRepository extends JpaRepository<TransactionHoldJpaEntity, UUID> {

    Optional<TransactionHoldJpaEntity> findByTransactionId(UUID transactionId);

    @Query("SELECT h FROM TransactionHoldJpaEntity h WHERE h.expiresAt < :now AND h.isReleased = false")
    List<TransactionHoldJpaEntity> findExpiredActiveHolds(@Param("now") Instant now);

    List<TransactionHoldJpaEntity> findByAccountId(UUID accountId);
}
