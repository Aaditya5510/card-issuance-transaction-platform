-- 1. ACCOUNTS TABLE
CREATE TABLE accounts (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          account_number VARCHAR(32) NOT NULL UNIQUE,
                          currency VARCHAR(3) NOT NULL DEFAULT 'INR',
                          available_balance NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
                          pending_hold_balance NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
                          status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                          version BIGINT NOT NULL DEFAULT 0,
                          created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          CONSTRAINT chk_account_available_balance CHECK (available_balance >= 0),
                          CONSTRAINT chk_account_pending_hold CHECK (pending_hold_balance >= 0)
);

-- 2. CARDS TABLE
CREATE TABLE cards (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
                       card_token VARCHAR(64) NOT NULL UNIQUE,
                       masked_pan VARCHAR(20) NOT NULL,
                       expiry_month INT NOT NULL CHECK (expiry_month BETWEEN 1 AND 12),
                       expiry_year INT NOT NULL,
                       status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                       version BIGINT NOT NULL DEFAULT 0,
                       created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_cards_account_id ON cards(account_id);

-- 3. CARD CONTROLS TABLE (Separate Fine-grained Rules Engine)
CREATE TABLE card_controls (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               card_id UUID NOT NULL UNIQUE REFERENCES cards(id) ON DELETE CASCADE,
                               daily_limit NUMERIC(18, 4) NOT NULL DEFAULT 50000.0000,
                               per_tx_limit NUMERIC(18, 4) NOT NULL DEFAULT 10000.0000,
                               online_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                               atm_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                               international_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                               created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. IDEMPOTENCY RECORDS TABLE (Full API Request / Response Replay Cache)
CREATE TABLE idempotency_records (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     idempotency_key VARCHAR(128) NOT NULL UNIQUE,
                                     request_hash VARCHAR(64) NOT NULL,
                                     response_code INT,
                                     response_body TEXT,
                                     status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
                                     created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_idempotency_lookup ON idempotency_records(idempotency_key);

-- 5. AUTHORIZATIONS TABLE (Real-time Swipes / Holds)
CREATE TABLE authorizations (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                card_id UUID NOT NULL REFERENCES cards(id) ON DELETE RESTRICT,
                                account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
                                idempotency_key VARCHAR(128) NOT NULL,
                                amount NUMERIC(18, 4) NOT NULL CHECK (amount > 0),
                                currency VARCHAR(3) NOT NULL DEFAULT 'INR',
                                merchant_name VARCHAR(100) NOT NULL,
                                status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                version BIGINT NOT NULL DEFAULT 0,
                                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_auth_account_id ON authorizations(account_id);
CREATE INDEX idx_auth_card_id ON authorizations(card_id);

-- 6. OUTBOX EVENTS TABLE (Transactional Outbox Pattern for Kafka)
CREATE TABLE outbox_events (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               aggregate_type VARCHAR(50) NOT NULL,
                               aggregate_id VARCHAR(64) NOT NULL,
                               event_type VARCHAR(50) NOT NULL,
                               payload JSONB NOT NULL,
                               status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                               retry_count INT NOT NULL DEFAULT 0,
                               created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_outbox_pending ON outbox_events(status, created_at);

-- 7. SETTLEMENT BATCHES TABLE (Clearing & Reconciliation Files)
CREATE TABLE settlement_batches (
                                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    batch_reference VARCHAR(64) NOT NULL UNIQUE,
                                    total_records INT NOT NULL DEFAULT 0,
                                    total_amount NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
                                    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
                                    settled_at TIMESTAMP WITH TIME ZONE,
                                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 8. LEDGER ENTRIES TABLE (Strictly Immutable Double-Entry Ledger)
CREATE TABLE ledger_entries (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                transaction_ref_id UUID NOT NULL,
                                account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
                                entry_type VARCHAR(10) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
                                amount NUMERIC(18, 4) NOT NULL CHECK (amount > 0),
                                balance_after NUMERIC(18, 4) NOT NULL,
                                description VARCHAR(255) NOT NULL,
                                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_ledger_account_created ON ledger_entries(account_id, created_at DESC);
CREATE INDEX idx_ledger_tx_ref ON ledger_entries(transaction_ref_id);