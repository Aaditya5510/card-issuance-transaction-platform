-- =============================================================================
-- Migration: V3__enhance_double_entry_ledger_tables.sql
-- Description: Enterprise-grade Double-Entry Bookkeeping Ledger Engine tables
-- =============================================================================

-- 1. JOURNAL ENTRIES TABLE (Immutable Transaction Aggregate Header)
CREATE TABLE journal_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(128) UNIQUE,
    correlation_id VARCHAR(128),
    description VARCHAR(255) NOT NULL,
    posted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_journal_entries_correlation ON journal_entries(correlation_id);
CREATE INDEX idx_journal_entries_idempotency ON journal_entries(idempotency_key);
CREATE INDEX idx_journal_entries_posted_at ON journal_entries(posted_at DESC);

-- 2. LEDGER POSTINGS TABLE (Immutable Multi-Leg Debits and Credits)
CREATE TABLE ledger_postings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    posting_type VARCHAR(10) NOT NULL CHECK (posting_type IN ('DEBIT', 'CREDIT')),
    amount NUMERIC(18, 4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    sequence_number INT NOT NULL DEFAULT 1,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ledger_postings_journal ON ledger_postings(journal_entry_id);
CREATE INDEX idx_ledger_postings_account ON ledger_postings(account_id);
CREATE INDEX idx_ledger_postings_created ON ledger_postings(created_at DESC);
