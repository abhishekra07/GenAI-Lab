-- ============================================================
-- V8: Create Refresh Tokens Table
-- ============================================================
-- JWT access tokens are short-lived (24h). Refresh tokens are
-- long-lived (7 days) and allow obtaining a new access token
-- without re-entering credentials.
--
-- Design decisions:
--
-- WHY store refresh tokens in the database?
--   Access tokens are stateless JWTs — we never store them.
--   Refresh tokens MUST be stored in the database. Here's why:
--
--   If a user logs out or their device is stolen, we need to
--   REVOKE the refresh token so it can't be used to get new
--   access tokens. Stateless JWTs can't be revoked — once issued,
--   they're valid until expiry. Refresh tokens in the DB CAN be
--   deleted (revoked) on logout.
--
--   This is the standard pattern:
--     - Short-lived access token (stateless JWT, 15min-24h)
--     - Long-lived refresh token (stored in DB, 7 days)
--     - On logout: delete the refresh token from DB
--     - On token refresh: validate token exists in DB, issue new access token
--
-- token VARCHAR UNIQUE:
--   A cryptographically random string (not a JWT).
--   We use a random token (not JWT) for refresh tokens because:
--   - We must look them up in the DB anyway (to check revocation)
--   - Random tokens are shorter and simpler than JWTs for this use case
--   - Generated with SecureRandom in the application layer
--
-- expires_at TIMESTAMPTZ:
--   When this refresh token expires. Checked on every use.
--   Expired tokens are rejected even if they exist in the DB.
--
-- is_revoked BOOLEAN:
--   Explicit revocation flag. Set to TRUE on logout.
--   WHY not just delete the row?
--   Keeping revoked tokens with is_revoked=TRUE provides an audit trail.
--   A background job can clean up expired/revoked tokens periodically.
--
-- device_info VARCHAR (nullable):
--   Optional description of where this token was issued.
--   Populated from the User-Agent header: "Chrome on Mac", "Mobile App".
--   Lets users see "active sessions" and revoke specific devices.
-- ============================================================

CREATE TABLE refresh_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    token       VARCHAR(500) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    is_revoked  BOOLEAN      NOT NULL DEFAULT FALSE,
    device_info VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT refresh_tokens_user_fk
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,
        -- ON DELETE CASCADE: when user is deleted, all their refresh tokens go too.

    CONSTRAINT refresh_tokens_token_unique
        UNIQUE (token)
);

-- ============================================================
-- Indexes
-- ============================================================

-- Token lookup: the primary operation on this table
-- "Is this token valid? Who does it belong to?"
CREATE INDEX idx_refresh_tokens_token
    ON refresh_tokens (token) WHERE is_revoked = FALSE;

-- User's active tokens: "show all active sessions for this user"
CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens (user_id, created_at DESC) WHERE is_revoked = FALSE;

-- Cleanup job: "find all expired or revoked tokens older than 30 days"
CREATE INDEX idx_refresh_tokens_cleanup
    ON refresh_tokens (expires_at) WHERE is_revoked = TRUE;
