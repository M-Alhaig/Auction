-- V1: Create user_profiles table
-- Core user entity containing profile information
-- Separated from auth credentials to support multiple auth methods per user

CREATE TABLE user_profiles (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email          VARCHAR(255) NOT NULL UNIQUE,
    display_name   VARCHAR(100) NOT NULL,
    avatar_url     VARCHAR(500),
    role           VARCHAR(20) NOT NULL DEFAULT 'BIDDER',
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at  TIMESTAMPTZ,
    version        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_role CHECK (role IN ('ADMIN', 'SELLER', 'BIDDER'))
);

-- Index for email lookups (login, registration checks)
CREATE INDEX idx_user_profiles_email ON user_profiles (email);

-- Index for enabled users (active user queries)
CREATE INDEX idx_user_profiles_enabled ON user_profiles (enabled) WHERE enabled = TRUE;

COMMENT ON TABLE user_profiles IS 'Core user entity with profile information, separated from auth credentials';
COMMENT ON COLUMN user_profiles.email IS 'Unique email address used for login and notifications';
COMMENT ON COLUMN user_profiles.role IS 'User role: BIDDER (default), SELLER (can create auctions), ADMIN (full access)';
COMMENT ON COLUMN user_profiles.email_verified IS 'Required for bidding and selling actions';
COMMENT ON COLUMN user_profiles.version IS 'Optimistic locking version for concurrent update protection';
