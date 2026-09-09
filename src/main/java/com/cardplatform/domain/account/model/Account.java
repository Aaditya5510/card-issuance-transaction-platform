package com.cardplatform.domain.account.model;

import com.cardplatform.common.exception.BusinessRuleViolationException;
import com.cardplatform.common.exception.ErrorCode;
import com.cardplatform.common.exception.InsufficientFundsException;
import com.cardplatform.common.money.MonetaryAmount;

import java.time.Instant;
import java.util.UUID;

/**
 * Core Account Domain Aggregate Root.
 * Encapsulates available balance, pending hold balance, and hold reservation/settlement invariants.
 */
public class Account {

    private final UUID id;
    private final String accountNumber;
    private final String currency;
    private MonetaryAmount availableBalance;
    private MonetaryAmount pendingHoldBalance;
    private AccountStatus status;
    private final Long version;
    private final Instant createdAt;
    private Instant updatedAt;

    public Account(
            UUID id,
            String accountNumber,
            String currency,
            MonetaryAmount availableBalance,
            MonetaryAmount pendingHoldBalance,
            AccountStatus status,
            Long version,
            Instant createdAt,
            Instant updatedAt) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException("Account number cannot be empty");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency cannot be empty");
        }
        this.id = id;
        this.accountNumber = accountNumber;
        this.currency = currency.toUpperCase();
        this.availableBalance = availableBalance != null ? availableBalance : MonetaryAmount.zero(this.currency);
        this.pendingHoldBalance = pendingHoldBalance != null ? pendingHoldBalance : MonetaryAmount.zero(this.currency);
        this.status = status != null ? status : AccountStatus.ACTIVE;
        this.version = version != null ? version : 0L;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static Account create(String accountNumber, String currency, MonetaryAmount initialBalance) {
        MonetaryAmount balance = initialBalance != null ? initialBalance : MonetaryAmount.zero(currency);
        return new Account(
                UUID.randomUUID(),
                accountNumber,
                currency,
                balance,
                MonetaryAmount.zero(currency),
                AccountStatus.ACTIVE,
                0L,
                Instant.now(),
                Instant.now()
        );
    }

    public void placeHold(MonetaryAmount holdAmount) {
        assertActive();
        assertPositive(holdAmount);
        if (availableBalance.isLessThan(holdAmount)) {
            throw new InsufficientFundsException(
                    "Insufficient available balance for authorization hold. Available: " +
                            availableBalance + ", Required: " + holdAmount
            );
        }
        this.availableBalance = this.availableBalance.subtract(holdAmount);
        this.pendingHoldBalance = this.pendingHoldBalance.add(holdAmount);
        this.updatedAt = Instant.now();
    }

    public void releaseHold(MonetaryAmount holdAmount) {
        assertActive();
        assertPositive(holdAmount);
        if (pendingHoldBalance.isLessThan(holdAmount)) {
            throw new BusinessRuleViolationException(
                    "Cannot release hold larger than current pending hold balance. Pending: " +
                            pendingHoldBalance + ", Release: " + holdAmount,
                    ErrorCode.BUSINESS_RULE_VIOLATION
            );
        }
        this.pendingHoldBalance = this.pendingHoldBalance.subtract(holdAmount);
        this.availableBalance = this.availableBalance.add(holdAmount);
        this.updatedAt = Instant.now();
    }

    public void captureHold(MonetaryAmount captureAmount) {
        assertActive();
        assertPositive(captureAmount);
        if (pendingHoldBalance.isLessThan(captureAmount)) {
            throw new BusinessRuleViolationException(
                    "Capture amount exceeds pending hold balance. Pending: " +
                            pendingHoldBalance + ", Capture: " + captureAmount,
                    ErrorCode.BUSINESS_RULE_VIOLATION
            );
        }
        this.pendingHoldBalance = this.pendingHoldBalance.subtract(captureAmount);
        this.updatedAt = Instant.now();
    }

    public void directDebit(MonetaryAmount debitAmount) {
        assertActive();
        assertPositive(debitAmount);
        if (availableBalance.isLessThan(debitAmount)) {
            throw new InsufficientFundsException(
                    "Insufficient available balance for direct debit. Available: " +
                            availableBalance + ", Required: " + debitAmount
            );
        }
        this.availableBalance = this.availableBalance.subtract(debitAmount);
        this.updatedAt = Instant.now();
    }

    public void credit(MonetaryAmount creditAmount) {
        assertActive();
        assertPositive(creditAmount);
        this.availableBalance = this.availableBalance.add(creditAmount);
        this.updatedAt = Instant.now();
    }

    public void freeze() {
        if (this.status == AccountStatus.CLOSED) {
            throw new BusinessRuleViolationException("Cannot freeze a closed account", ErrorCode.BUSINESS_RULE_VIOLATION);
        }
        this.status = AccountStatus.FROZEN;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        if (this.status == AccountStatus.CLOSED) {
            throw new BusinessRuleViolationException("Cannot activate a closed account", ErrorCode.BUSINESS_RULE_VIOLATION);
        }
        this.status = AccountStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    private void assertActive() {
        if (this.status != AccountStatus.ACTIVE) {
            throw new BusinessRuleViolationException("Account is not active: " + this.status, ErrorCode.BUSINESS_RULE_VIOLATION);
        }
    }

    private void assertPositive(MonetaryAmount amount) {
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getCurrency() {
        return currency;
    }

    public MonetaryAmount getAvailableBalance() {
        return availableBalance;
    }

    public MonetaryAmount getPendingHoldBalance() {
        return pendingHoldBalance;
    }

    public MonetaryAmount getTotalBalance() {
        return availableBalance.add(pendingHoldBalance);
    }

    public MonetaryAmount getLedgerBalance() {
        return availableBalance.add(pendingHoldBalance);
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
