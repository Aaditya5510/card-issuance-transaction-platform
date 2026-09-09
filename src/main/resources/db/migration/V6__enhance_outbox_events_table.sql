-- V6: Enhance Outbox Events Table for Exponential Backoff, DLQ, and Polling Optimization
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS published_at TIMESTAMP WITH TIME ZONE;

-- Create composite index for polling pending outbox events ordered by creation time
CREATE INDEX IF NOT EXISTS idx_outbox_polling ON outbox_events(status, next_attempt_at, created_at);

-- Create index for aggregate-level querying and ordering
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON outbox_events(aggregate_type, aggregate_id, created_at);
