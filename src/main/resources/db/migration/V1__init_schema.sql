-- =============================================================================
-- Migration: V1__init_schema.sql
-- Description: Initialize foundational PostgreSQL extensions for Card Platform
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
