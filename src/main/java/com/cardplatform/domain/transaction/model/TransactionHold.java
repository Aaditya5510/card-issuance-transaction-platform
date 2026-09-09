package com.cardplatform.domain.transaction.model;

import com.cardplatform.common.exception.BusinessRuleViolationException;
import com.cardplatform.common.exception.ErrorCode;
import com.cardplatform.common.money.MonetaryAmount;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Transaction Hold Domain Entity.
 * Represents an active, non-settled balance reservation placed on an account during Phase 1 authorization.
 */
public class TransactionHold {

    private final UUID id;
    private final UUID transactionId;
    private final UUID accountId;
    private final MonetaryAmount amount;
    private boolean isReleased;
    private final Instant expiresAt;
    private final Long version;
    private final Instant createdAt;
    private Instant updatedAt;

    public TransactionHold(
            UUID id,
            UUID transactionId,
            UUID accountId,
            MonetaryAmount amount,
            boolean isReleased,
            Instant expiresAt,
            Long version,
            Instant createdAt,
            Instant updatedAt) {
        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID cannot be null");
        }
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("Hold amount must be positive");
        }
        this.id = id != null ? id : UUID.randomUUID();
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.amount = amount;
        this.isReleased = isReleased;
        this.expiresAt = expiresAt != null ? expiresAt : Instant.now().plusSeconds(7 * 24 * 3600);
        this.version = version != null ? version : 0L;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static TransactionHold create(
            UUID transactionId,
            UUID accountId,
            MonetaryAmount amount,
            Instant expiresAt) {
        return new TransactionHold(
                UUID.randomUUID(),
                transactionId,
                accountId,
                amount,
                false,
                expiresAt,
                0L,
                Instant.now(),
                Instant.now()
        );
    }

    public void release() {
        if (this.isReleased) {
            throw new BusinessRuleViolationException(
                    "Hold is already released: " + this.id,
                    ErrorCode.BUSINESS_RULE_VIOLATION
            );
        }
        this.isReleased = true;
        this.updatedAt = Instant.now();
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public MonetaryAmount getAmount() {
        return amount;
    }

    public boolean isReleased() {
        return isReleased;
    }

    public Instant getExpiresAt() {
        return expiresAt;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionHold that = (TransactionHold) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
