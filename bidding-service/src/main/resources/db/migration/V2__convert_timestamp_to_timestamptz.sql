-- Migration: Convert bid timestamp from TIMESTAMP to TIMESTAMPTZ (timezone-aware)
-- Rationale: Ensures correct event ordering and timezone handling across distributed services
-- Impact: Existing data is preserved and interpreted as UTC

-- Convert bid placement timestamp
ALTER TABLE bids
    ALTER COLUMN timestamp TYPE TIMESTAMPTZ USING timestamp AT TIME ZONE 'UTC';

-- Note: Index idx_timestamp is automatically maintained by PostgreSQL
