package com.cardplatform.infrastructure.persistence.jpa;

import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.transaction.model.TransactionHold;
import com.cardplatform.domain.transaction.repository.TransactionHoldRepository;
import com.cardplatform.infrastructure.persistence.entity.TransactionHoldJpaEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaTransactionHoldRepositoryAdapter implements TransactionHoldRepository {

    private final TransactionHoldJpaRepository transactionHoldJpaRepository;

    public JpaTransactionHoldRepositoryAdapter(TransactionHoldJpaRepository transactionHoldJpaRepository) {
        this.transactionHoldJpaRepository = transactionHoldJpaRepository;
    }

    @Override
    public Optional<TransactionHold> findById(UUID id) {
        return transactionHoldJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<TransactionHold> findByTransactionId(UUID transactionId) {
        return transactionHoldJpaRepository.findByTransactionId(transactionId).map(this::toDomain);
    }

    @Override
    public List<TransactionHold> findExpiredActiveHolds(Instant now) {
        return transactionHoldJpaRepository.findExpiredActiveHolds(now).stream().map(this::toDomain).toList();
    }

    @Override
    public List<TransactionHold> findByAccountId(UUID accountId) {
        return transactionHoldJpaRepository.findByAccountId(accountId).stream().map(this::toDomain).toList();
    }

    @Override
    public TransactionHold save(TransactionHold domain) {
        TransactionHoldJpaEntity entity;
        if (domain.getId() != null && transactionHoldJpaRepository.existsById(domain.getId())) {
            entity = transactionHoldJpaRepository.findById(domain.getId()).orElse(new TransactionHoldJpaEntity());
        } else {
            entity = new TransactionHoldJpaEntity();
            entity.setId(domain.getId());
        }

        entity.setTransactionId(domain.getTransactionId());
        entity.setAccountId(domain.getAccountId());
        entity.setAmount(domain.getAmount().getAmount());
        entity.setCurrency(domain.getAmount().getCurrency());
        entity.setIsReleased(domain.isReleased());
        entity.setExpiresAt(domain.getExpiresAt());

        TransactionHoldJpaEntity saved = transactionHoldJpaRepository.save(entity);
        return toDomain(saved);
    }

    private TransactionHold toDomain(TransactionHoldJpaEntity entity) {
        return new TransactionHold(
                entity.getId(),
                entity.getTransactionId(),
                entity.getAccountId(),
                MonetaryAmount.of(entity.getAmount(), entity.getCurrency()),
                entity.getIsReleased() != null && entity.getIsReleased(),
                entity.getExpiresAt(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
