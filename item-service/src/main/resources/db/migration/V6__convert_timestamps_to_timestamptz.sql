-- Migration: Convert all timestamp columns from TIMESTAMP to TIMESTAMPTZ (timezone-aware)
-- Rationale: Ensures correct timezone handling across distributed microservices
-- Impact: Existing data is preserved and interpreted as UTC

-- Convert auction scheduling timestamps
ALTER TABLE items
    ALTER COLUMN start_time TYPE TIMESTAMPTZ USING start_time AT TIME ZONE 'UTC';

ALTER TABLE items
    ALTER COLUMN end_time TYPE TIMESTAMPTZ USING end_time AT TIME ZONE 'UTC';

-- Convert audit timestamps
ALTER TABLE items
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE items
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

-- Note: Indexes (idx_seller_id, idx_status_endtime) are automatically maintained by PostgreSQL
