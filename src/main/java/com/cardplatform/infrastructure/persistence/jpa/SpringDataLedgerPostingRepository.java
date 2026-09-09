package com.cardplatform.infrastructure.persistence.jpa;

import com.cardplatform.infrastructure.persistence.entity.LedgerPostingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataLedgerPostingRepository extends JpaRepository<LedgerPostingJpaEntity, UUID> {

    List<LedgerPostingJpaEntity> findAllByAccountIdOrderByCreatedAtDesc(UUID accountId);

    List<LedgerPostingJpaEntity> findAllByJournalEntryIdOrderBySequenceNumberAsc(UUID journalEntryId);

    List<LedgerPostingJpaEntity> findByJournalEntryId(UUID journalEntryId);
}
