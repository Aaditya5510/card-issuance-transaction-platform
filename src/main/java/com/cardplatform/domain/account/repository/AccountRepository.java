package com.cardplatform.domain.account.repository;

import com.cardplatform.domain.account.model.Account;

import java.util.Optional;
import java.util.UUID;

/**
 * Domain repository contract for Accounts (Clean Architecture / Zero framework coupling).
 */
public interface AccountRepository {

    Optional<Account> findById(UUID id);

    Optional<Account> findByIdWithLock(UUID id);

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    Account save(Account account);
}
