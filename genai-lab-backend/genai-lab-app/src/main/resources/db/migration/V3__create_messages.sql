-- ============================================================
-- V3: Create Messages Table
-- ============================================================
-- Messages are the individual turns in a conversation.
-- Each message has a role (user/assistant/system) and content.
--
-- Design decisions:
--
-- role as VARCHAR with CHECK constraint (not an enum type):
--   PostgreSQL has a native ENUM type, but we avoid it here.
--   WHY? ALTER TYPE to add a new enum value requires a table rewrite
--   in older PostgreSQL versions and is always a DDL change requiring
--   a new migration. A CHECK constraint is easier to extend:
--   just add a new migration that alters the check constraint.
--   Possible values: 'user', 'assistant', 'system'
--
-- content TEXT NOT NULL:
--   Messages can be very long (especially AI responses). TEXT has
--   no length limit in PostgreSQL. VARCHAR(n) would risk truncation.
--
-- token_count_prompt / token_count_completion (nullable INT):
--   Token counts come from the OpenAI API response. They're NULL for
--   user messages (we don't know prompt tokens until the AI responds)
--   and populated for assistant messages.
--   Storing both separately lets us calculate cost:
--     cost = (prompt_tokens * input_price) + (completion_tokens * output_price)
--
-- model_used VARCHAR (nullable):
--   Which model generated this response. NULL for user messages.
--   Stored per-message (not just per-conversation) because a conversation
--   could theoretically switch models mid-way.
--
-- is_error BOOLEAN:
--   If an AI call fails, we store an error message as an assistant
--   message with is_error=TRUE. This way the UI can display the error
--   in context rather than losing the conversation state.
--
-- parent_message_id (nullable self-reference):
--   For future support of message regeneration / branching.
--   When a user regenerates a response, the new message references
--   the original. NULL for normal sequential messages.
--   We add this now because retrofitting tree-structure onto a flat
--   table later is painful.
-- ============================================================

CREATE TABLE messages (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id      UUID         NOT NULL,
    role                 VARCHAR(20)  NOT NULL,
    content              TEXT         NOT NULL,
    token_count_prompt   INTEGER,
    token_count_completion INTEGER,
    model_used           VARCHAR(100),
    is_error             BOOLEAN      NOT NULL DEFAULT FALSE,
    parent_message_id    UUID,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- Messages are immutable once created — no updated_at needed.
    -- Edits create new messages, they don't modify existing ones.

    CONSTRAINT messages_conversation_fk
        FOREIGN KEY (conversation_id) REFERENCES conversations (id)
        ON DELETE CASCADE,

    CONSTRAINT messages_parent_fk
        FOREIGN KEY (parent_message_id) REFERENCES messages (id)
        ON DELETE SET NULL,
        -- ON DELETE SET NULL: if a parent message is deleted,
        -- child messages lose their parent reference but are not deleted.

    CONSTRAINT messages_role_check
        CHECK (role IN ('user', 'assistant', 'system'))
);

-- ============================================================
-- Indexes
-- ============================================================

-- Primary access pattern: "get all messages for conversation X in order"
-- Covers: WHERE conversation_id = ? ORDER BY created_at ASC
CREATE INDEX idx_messages_conversation_id
    ON messages (conversation_id, created_at ASC);

-- Token usage reporting: sum tokens by conversation or by model
CREATE INDEX idx_messages_model_used
    ON messages (model_used) WHERE model_used IS NOT NULL;
