package com.cardplatform.infrastructure.persistence.jpa;

import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.domain.ledger.model.JournalEntry;
import com.cardplatform.domain.ledger.model.LedgerPosting;
import com.cardplatform.domain.ledger.model.PostingType;
import com.cardplatform.domain.ledger.repository.LedgerRepository;
import com.cardplatform.infrastructure.persistence.entity.JournalEntryJpaEntity;
import com.cardplatform.infrastructure.persistence.entity.LedgerEntryJpaEntity;
import com.cardplatform.infrastructure.persistence.entity.LedgerPostingJpaEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaLedgerRepositoryAdapter implements LedgerRepository {

    private final SpringDataJournalEntryRepository journalEntryRepository;
    private final SpringDataLedgerPostingRepository ledgerPostingRepository;
    private final SpringDataLedgerRepository legacyLedgerRepository;

    public JpaLedgerRepositoryAdapter(
            SpringDataJournalEntryRepository journalEntryRepository,
            SpringDataLedgerPostingRepository ledgerPostingRepository,
            SpringDataLedgerRepository legacyLedgerRepository) {
        this.journalEntryRepository = journalEntryRepository;
        this.ledgerPostingRepository = ledgerPostingRepository;
        this.legacyLedgerRepository = legacyLedgerRepository;
    }

    @Override
    public JournalEntry save(JournalEntry entry) {
        JournalEntryJpaEntity entity = new JournalEntryJpaEntity();
        entity.setId(entry.getId());
        entity.setIdempotencyKey(entry.getIdempotencyKey());
        entity.setCorrelationId(entry.getCorrelationId());
        entity.setDescription(entry.getDescription());
        entity.setPostedAt(entry.getPostedAt());

        List<LedgerPostingJpaEntity> postingEntities = new ArrayList<>();
        for (LedgerPosting posting : entry.getPostings()) {
            LedgerPostingJpaEntity postingEntity = new LedgerPostingJpaEntity();
            postingEntity.setId(posting.getId());
            postingEntity.setAccountId(posting.getAccountId());
            postingEntity.setPostingType(posting.getType().name());
            postingEntity.setAmount(posting.getAmount().getAmount());
            postingEntity.setCurrency(posting.getAmount().getCurrency());
            postingEntity.setSequenceNumber(posting.getSequenceNumber());
            postingEntity.setDescription(posting.getDescription());
            postingEntity.setCreatedAt(posting.getCreatedAt());

            entity.addPosting(postingEntity);
            postingEntities.add(postingEntity);

            // Backwards compatibility with legacy ledger_entries table
            LedgerEntryJpaEntity legacyEntity = new LedgerEntryJpaEntity();
            legacyEntity.setId(posting.getId());
            legacyEntity.setTransactionRefId(entry.getId());
            legacyEntity.setAccountId(posting.getAccountId());
            legacyEntity.setEntryType(posting.getType().name());
            legacyEntity.setAmount(posting.getAmount().getAmount());
            legacyEntity.setBalanceAfter(posting.getBalanceAfter() != null ? posting.getBalanceAfter().getAmount() : posting.getAmount().getAmount());
            legacyEntity.setDescription(posting.getDescription() != null ? posting.getDescription() : entry.getDescription());
            legacyEntity.setCreatedAt(posting.getCreatedAt());
            legacyLedgerRepository.save(legacyEntity);
        }

        JournalEntryJpaEntity saved = journalEntryRepository.save(entity);
        return toDomain(saved, postingEntities);
    }

    @Override
    public Optional<JournalEntry> findById(UUID id) {
        return journalEntryRepository.findById(id)
                .map(entity -> {
                    List<LedgerPostingJpaEntity> postings = ledgerPostingRepository.findAllByJournalEntryIdOrderBySequenceNumberAsc(entity.getId());
                    return toDomain(entity, postings);
                });
    }

    @Override
    public Optional<JournalEntry> findByCorrelationId(String correlationId) {
        return journalEntryRepository.findByCorrelationId(correlationId)
                .map(entity -> {
                    List<LedgerPostingJpaEntity> postings = ledgerPostingRepository.findAllByJournalEntryIdOrderBySequenceNumberAsc(entity.getId());
                    return toDomain(entity, postings);
                });
    }

    @Override
    public Optional<JournalEntry> findByIdempotencyKey(String idempotencyKey) {
        return journalEntryRepository.findByIdempotencyKey(idempotencyKey)
                .map(entity -> {
                    List<LedgerPostingJpaEntity> postings = ledgerPostingRepository.findAllByJournalEntryIdOrderBySequenceNumberAsc(entity.getId());
                    return toDomain(entity, postings);
                });
    }

    @Override
    public List<LedgerPosting> findPostingsByAccountId(UUID accountId) {
        return ledgerPostingRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId)
                .stream()
                .map(this::toPostingDomain)
                .toList();
    }

    @Override
    public List<LedgerPosting> findPostingsByTransactionRefId(UUID transactionRefId) {
        return ledgerPostingRepository.findAllByJournalEntryIdOrderBySequenceNumberAsc(transactionRefId)
                .stream()
                .map(this::toPostingDomain)
                .toList();
    }

    private JournalEntry toDomain(JournalEntryJpaEntity entity, List<LedgerPostingJpaEntity> postings) {
        List<LedgerPosting> domainPostings = postings.stream()
                .map(this::toPostingDomain)
                .toList();
        return new JournalEntry(
                entity.getId(),
                entity.getIdempotencyKey(),
                entity.getCorrelationId(),
                entity.getDescription(),
                entity.getPostedAt(),
                domainPostings
        );
    }

    private LedgerPosting toPostingDomain(LedgerPostingJpaEntity entity) {
        return new LedgerPosting(
                entity.getId(),
                entity.getAccountId(),
                PostingType.valueOf(entity.getPostingType()),
                MonetaryAmount.of(entity.getAmount(), entity.getCurrency()),
                entity.getSequenceNumber(),
                entity.getDescription(),
                entity.getCreatedAt(),
                null
        );
    }
}
