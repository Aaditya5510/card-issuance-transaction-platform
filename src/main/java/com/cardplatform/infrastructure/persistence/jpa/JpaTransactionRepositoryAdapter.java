package com.cardplatform.infrastructure.persistence.jpa;

import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.transaction.model.Transaction;
import com.cardplatform.domain.transaction.model.TransactionStatus;
import com.cardplatform.domain.transaction.model.TransactionType;
import com.cardplatform.domain.transaction.repository.TransactionRepository;
import com.cardplatform.infrastructure.persistence.entity.TransactionJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaTransactionRepositoryAdapter implements TransactionRepository {

    private final TransactionJpaRepository transactionJpaRepository;

    public JpaTransactionRepositoryAdapter(TransactionJpaRepository transactionJpaRepository) {
        this.transactionJpaRepository = transactionJpaRepository;
    }

    @Override
    public Optional<Transaction> findById(UUID id) {
        return transactionJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Transaction> findByAuthorizationCode(String authorizationCode) {
        return transactionJpaRepository.findByAuthorizationCode(authorizationCode).map(this::toDomain);
    }

    @Override
    public List<Transaction> findByCardId(UUID cardId) {
        return transactionJpaRepository.findByCardId(cardId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Transaction> findByAccountId(UUID accountId) {
        return transactionJpaRepository.findByAccountId(accountId).stream().map(this::toDomain).toList();
    }

    @Override
    public Transaction save(Transaction domain) {
        TransactionJpaEntity entity;
        if (domain.getId() != null && transactionJpaRepository.existsById(domain.getId())) {
            entity = transactionJpaRepository.findById(domain.getId()).orElse(new TransactionJpaEntity());
        } else {
            entity = new TransactionJpaEntity();
            entity.setId(domain.getId());
        }

        entity.setCardId(domain.getCardId());
        entity.setAccountId(domain.getAccountId());
        entity.setAmount(domain.getAmount().getAmount());
        entity.setCurrency(domain.getAmount().getCurrency());
        entity.setType(domain.getType().name());
        entity.setStatus(domain.getStatus().name());
        entity.setAuthorizationCode(domain.getAuthorizationCode());
        entity.setMerchantId(domain.getMerchantId());
        entity.setMerchantCategoryCode(domain.getMerchantCategoryCode());
        entity.setExpiresAt(domain.getExpiresAt());

        TransactionJpaEntity saved = transactionJpaRepository.save(entity);
        return toDomain(saved);
    }

    private Transaction toDomain(TransactionJpaEntity entity) {
        return new Transaction(
                entity.getId(),
                entity.getCardId(),
                entity.getAccountId(),
                MonetaryAmount.of(entity.getAmount(), entity.getCurrency()),
                TransactionType.valueOf(entity.getType()),
                TransactionStatus.valueOf(entity.getStatus()),
                entity.getAuthorizationCode(),
                entity.getMerchantId(),
                entity.getMerchantCategoryCode(),
                entity.getExpiresAt(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
