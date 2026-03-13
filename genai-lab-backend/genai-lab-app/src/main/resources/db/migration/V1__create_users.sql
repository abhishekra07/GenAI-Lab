-- ============================================================
-- V1: Create Users Table
-- ============================================================
-- The users table is the root of all per-user data isolation.
-- Every conversation, document, and uploaded file will have a
-- foreign key path back to a user row.
-- ============================================================

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT users_email_unique UNIQUE (email)
);

-- ============================================================
-- Automatic updated_at trigger
-- ============================================================
-- Rather than relying on the application to set updated_at on every save,
-- we use a PostgreSQL trigger to do it automatically at the DB level.
-- ============================================================
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Step 2: Attach the trigger to the users table.
-- BEFORE UPDATE means it runs before the row is written,
-- so our NEW.updated_at value is included in the update.
CREATE TRIGGER users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- Indexes
-- ============================================================
-- We always create an index on email because:
--   1. Login lookups are always by email (WHERE email = ?)
--   2. The UNIQUE constraint creates an index, but naming it
--      explicitly makes it visible in query plans

-- Index for active user lookups (common filter in auth checks)
CREATE INDEX idx_users_is_active ON users (is_active) WHERE is_active = TRUE;
