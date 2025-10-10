-- ShedLock table for distributed scheduler coordination
-- Ensures only one instance processes scheduled jobs at a time

CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

CREATE INDEX idx_shedlock_lock_until ON shedlock(lock_until);

-- Comments for clarity
COMMENT ON TABLE shedlock IS 'Distributed lock table for ShedLock scheduler coordination';
COMMENT ON COLUMN shedlock.name IS 'Unique name of the scheduled task';
COMMENT ON COLUMN shedlock.lock_until IS 'Timestamp when the lock expires';
COMMENT ON COLUMN shedlock.locked_at IS 'Timestamp when the lock was acquired';
COMMENT ON COLUMN shedlock.locked_by IS 'Identifier of the instance that acquired the lock';
