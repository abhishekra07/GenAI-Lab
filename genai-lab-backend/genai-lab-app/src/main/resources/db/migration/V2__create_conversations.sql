-- ============================================================
-- V2: Create Conversations Table
-- ============================================================
-- A conversation is a named chat session belonging to a user.
-- It holds metadata about the chat: which model was used,
-- what system prompt was active, and when it was last active.
--
-- Design decisions:
--
-- model_id VARCHAR instead of FK to ai_model_configs:
--   We store the model identifier as a plain string rather than
--   a foreign key to a model config table. Why?
--   - Model configs may be added/removed over time
--   - We want historical conversations to remember which model
--     was used even if that model config is later deleted
--   - The model_id here is a snapshot of what was used at the time
--   Example values: "gpt-4o-mini", "gpt-4o", "claude-3-5-sonnet"
--
-- system_prompt TEXT (nullable):
--   The system prompt is optional. NULL means no custom system
--   prompt was set and the application default is used.
--   TEXT (not VARCHAR) because system prompts can be arbitrarily long.
--
-- title VARCHAR(500):
--   Conversation title shown in the sidebar. Generated from the
--   first message, or set manually by the user. 500 chars is generous.
--
-- is_pinned:
--   Users can pin important conversations to keep them at the top.
--   Simple boolean — no ordering within pinned conversations yet.
--
-- last_message_at:
--   Denormalised field for sorting conversations by recency without
--   doing a subquery or join to the messages table.
--   Updated by the application whenever a message is added.
--   WHY denormalise? Conversation list queries run very frequently
--   (every time the sidebar loads) and sorting by MAX(messages.created_at)
--   with a subquery on every page load is wasteful.
-- ============================================================

CREATE TABLE conversations (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL,
    title           VARCHAR(500) NOT NULL DEFAULT 'New Conversation',
    model_id        VARCHAR(100) NOT NULL,
    system_prompt   TEXT,
    is_pinned       BOOLEAN      NOT NULL DEFAULT FALSE,
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT conversations_user_fk
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE
        -- ON DELETE CASCADE: when a user is deleted, all their conversations
        -- are automatically deleted too. No orphaned data.
);

-- Trigger for updated_at (reuses the function created in V1)
CREATE TRIGGER conversations_updated_at
    BEFORE UPDATE ON conversations
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- Indexes
-- ============================================================

-- Primary access pattern: "get all conversations for user X, ordered by recency"
-- This covers: WHERE user_id = ? ORDER BY last_message_at DESC
CREATE INDEX idx_conversations_user_id
    ON conversations (user_id, last_message_at DESC NULLS LAST);

-- Pinned conversations filter: WHERE user_id = ? AND is_pinned = TRUE
CREATE INDEX idx_conversations_pinned
    ON conversations (user_id, is_pinned) WHERE is_pinned = TRUE;
