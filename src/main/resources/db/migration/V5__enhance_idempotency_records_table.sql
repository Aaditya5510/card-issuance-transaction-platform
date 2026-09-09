-- =============================================================================
-- Migration: V5__enhance_idempotency_records_table.sql
-- Description: Adds client_or_account_id, expires_at columns and indexes to idempotency_records
-- =============================================================================

ALTER TABLE idempotency_records ADD COLUMN IF NOT EXISTS client_or_account_id VARCHAR(64);
ALTER TABLE idempotency_records ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_idempotency_status ON idempotency_records(idempotency_key, status);
CREATE INDEX IF NOT EXISTS idx_idempotency_expiry ON idempotency_records(expires_at);
