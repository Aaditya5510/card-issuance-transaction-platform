-- =============================================================================
-- Migration: V4__create_transactions_and_holds_tables.sql
-- Description: Two-Phase Card Authorization and Settlement Engine tables
-- =============================================================================

-- 1. TRANSACTIONS TABLE
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id UUID NOT NULL REFERENCES cards(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    amount NUMERIC(18, 4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    authorization_code VARCHAR(32),
    merchant_id VARCHAR(64) NOT NULL,
    merchant_category_code VARCHAR(10),
    expires_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transactions_card_id ON transactions(card_id);
CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_auth_code ON transactions(authorization_code);
CREATE INDEX idx_transactions_status_created ON transactions(status, created_at DESC);

-- 2. TRANSACTION HOLDS TABLE
CREATE TABLE transaction_holds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    amount NUMERIC(18, 4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    is_released BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transaction_holds_tx_id ON transaction_holds(transaction_id);
CREATE INDEX idx_transaction_holds_account_id ON transaction_holds(account_id);
CREATE INDEX idx_transaction_holds_active_expiry ON transaction_holds(is_released, expires_at);
