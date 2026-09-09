package com.cardplatform.domain.ledger.model;

import com.cardplatform.common.money.MonetaryAmount;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable single leg of a double-entry bookkeeping transaction.
 */
public final class LedgerPosting {

    private final UUID id;
    private final UUID accountId;
    private final PostingType type;
    private final MonetaryAmount amount;
    private final Integer sequenceNumber;
    private final String description;
    private final Instant createdAt;
    private final MonetaryAmount balanceAfter;

    public LedgerPosting(
            UUID id,
            UUID accountId,
            PostingType type,
            MonetaryAmount amount,
            Integer sequenceNumber,
            String description,
            Instant createdAt,
            MonetaryAmount balanceAfter) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Posting type cannot be null");
        }
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Posting amount must be strictly positive: " + amount);
        }

        this.id = id != null ? id : UUID.randomUUID();
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.sequenceNumber = sequenceNumber != null ? sequenceNumber : 1;
        this.description = description != null ? description : "";
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.balanceAfter = balanceAfter;
    }

    public static LedgerPosting of(UUID accountId, PostingType type, MonetaryAmount amount, Integer sequenceNumber, String description) {
        return new LedgerPosting(UUID.randomUUID(), accountId, type, amount, sequenceNumber, description, Instant.now(), null);
    }

    public static LedgerPosting debit(UUID accountId, MonetaryAmount amount, Integer sequenceNumber, String description) {
        return of(accountId, PostingType.DEBIT, amount, sequenceNumber, description);
    }

    public static LedgerPosting credit(UUID accountId, MonetaryAmount amount, Integer sequenceNumber, String description) {
        return of(accountId, PostingType.CREDIT, amount, sequenceNumber, description);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public PostingType getType() {
        return type;
    }

    public EntryType getEntryType() {
        return EntryType.fromPostingType(type);
    }

    public MonetaryAmount getAmount() {
        return amount;
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public MonetaryAmount getBalanceAfter() {
        return balanceAfter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LedgerPosting that = (LedgerPosting) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "LedgerPosting{" +
                "id=" + id +
                ", accountId=" + accountId +
                ", type=" + type +
                ", amount=" + amount +
                ", seq=" + sequenceNumber +
                '}';
    }
}
