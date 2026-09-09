package com.cardplatform.infrastructure.persistence.jpa;

import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.account.model.AccountStatus;
import com.cardplatform.domain.account.repository.AccountRepository;
import com.cardplatform.infrastructure.persistence.entity.AccountJpaEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class JpaAccountRepositoryAdapter implements AccountRepository {

    private final SpringDataAccountRepository springDataAccountRepository;

    public JpaAccountRepositoryAdapter(SpringDataAccountRepository springDataAccountRepository) {
        this.springDataAccountRepository = springDataAccountRepository;
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return springDataAccountRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Account> findByIdWithLock(UUID id) {
        return springDataAccountRepository.findByIdWithLock(id).map(this::toDomain);
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        return springDataAccountRepository.findByAccountNumber(accountNumber).map(this::toDomain);
    }

    @Override
    public boolean existsByAccountNumber(String accountNumber) {
        return springDataAccountRepository.existsByAccountNumber(accountNumber);
    }

    @Override
    public Account save(Account domain) {
        AccountJpaEntity entity;
        if (domain.getId() != null && springDataAccountRepository.existsById(domain.getId())) {
            entity = springDataAccountRepository.findById(domain.getId()).orElse(new AccountJpaEntity());
        } else {
            entity = new AccountJpaEntity();
            entity.setId(domain.getId());
        }

        entity.setAccountNumber(domain.getAccountNumber());
        entity.setCurrency(domain.getCurrency());
        entity.setAvailableBalance(domain.getAvailableBalance().getAmount());
        entity.setPendingHoldBalance(domain.getPendingHoldBalance().getAmount());
        entity.setStatus(domain.getStatus().name());

        AccountJpaEntity saved = springDataAccountRepository.save(entity);
        return toDomain(saved);
    }

    private Account toDomain(AccountJpaEntity entity) {
        return new Account(
                entity.getId(),
                entity.getAccountNumber(),
                entity.getCurrency(),
                MonetaryAmount.of(entity.getAvailableBalance(), entity.getCurrency()),
                MonetaryAmount.of(entity.getPendingHoldBalance(), entity.getCurrency()),
                AccountStatus.valueOf(entity.getStatus()),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
