package com.cardplatform.domain.ledger.model;

import com.cardplatform.common.exception.LedgerImbalanceException;
import com.cardplatform.common.money.MonetaryAmount;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JournalEntry Aggregate Root representing an immutable, balanced double-entry accounting transaction.
 * Invariant: Sum of all DEBIT postings MUST strictly equal the sum of all CREDIT postings.
 */
public final class JournalEntry {

    private final UUID id;
    private final String idempotencyKey;
    private final String correlationId;
    private final String description;
    private final Instant postedAt;
    private final List<LedgerPosting> postings;

    public JournalEntry(
            UUID id,
            String idempotencyKey,
            String correlationId,
            String description,
            Instant postedAt,
            List<LedgerPosting> postings) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("JournalEntry description cannot be null or blank");
        }
        if (postings == null || postings.size() < 2) {
            throw new LedgerImbalanceException(
                    "A double-entry JournalEntry must consist of at least 2 legs (postings). Provided: " +
                            (postings == null ? 0 : postings.size())
            );
        }

        this.id = id != null ? id : UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.correlationId = correlationId;
        this.description = description;
        this.postedAt = postedAt != null ? postedAt : Instant.now();
        this.postings = List.copyOf(postings);

        validateDoubleEntryBalance();
    }

    public static JournalEntry create(
            String idempotencyKey,
            String correlationId,
            String description,
            List<LedgerPosting> postings) {
        return new JournalEntry(UUID.randomUUID(), idempotencyKey, correlationId, description, Instant.now(), postings);
    }

    public static JournalEntry create(UUID transactionRefId, String description, List<LedgerPosting> postings) {
        return new JournalEntry(transactionRefId, null, transactionRefId.toString(), description, Instant.now(), postings);
    }

    public static Builder builder() {
        return new Builder();
    }

    private void validateDoubleEntryBalance() {
        String baseCurrency = postings.getFirst().getAmount().getCurrency();
        MonetaryAmount totalDebit = MonetaryAmount.zero(baseCurrency);
        MonetaryAmount totalCredit = MonetaryAmount.zero(baseCurrency);

        for (LedgerPosting posting : postings) {
            if (!posting.getAmount().getCurrency().equalsIgnoreCase(baseCurrency)) {
                throw new LedgerImbalanceException(
                        "All postings within a single JournalEntry must share the same currency. Expected: " +
                                baseCurrency + ", Found: " + posting.getAmount().getCurrency()
                );
            }

            if (posting.getType() == PostingType.DEBIT) {
                totalDebit = totalDebit.add(posting.getAmount());
            } else if (posting.getType() == PostingType.CREDIT) {
                totalCredit = totalCredit.add(posting.getAmount());
            }
        }

        if (!totalDebit.equals(totalCredit)) {
            throw new LedgerImbalanceException(
                    "Mathematical Zero-Sum Invariant Failed! Total Debits (" + totalDebit +
                            ") does not strictly equal Total Credits (" + totalCredit + ")"
            );
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionRefId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getDescription() {
        return description;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public Instant getCreatedAt() {
        return postedAt;
    }

    public List<LedgerPosting> getPostings() {
        return postings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JournalEntry that = (JournalEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "JournalEntry{" +
                "id=" + id +
                ", description='" + description + '\'' +
                ", legs=" + postings.size() +
                ", postedAt=" + postedAt +
                '}';
    }

    public static final class Builder {
        private UUID id;
        private String idempotencyKey;
        private String correlationId;
        private String description;
        private Instant postedAt;
        private final List<LedgerPosting> postings = new ArrayList<>();

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder postedAt(Instant postedAt) {
            this.postedAt = postedAt;
            return this;
        }

        public Builder addDebit(UUID accountId, MonetaryAmount amount, String description) {
            int seq = postings.size() + 1;
            postings.add(LedgerPosting.debit(accountId, amount, seq, description));
            return this;
        }

        public Builder addCredit(UUID accountId, MonetaryAmount amount, String description) {
            int seq = postings.size() + 1;
            postings.add(LedgerPosting.credit(accountId, amount, seq, description));
            return this;
        }

        public Builder addPosting(LedgerPosting posting) {
            postings.add(posting);
            return this;
        }

        public JournalEntry build() {
            return new JournalEntry(id, idempotencyKey, correlationId, description, postedAt, postings);
        }
    }
}
