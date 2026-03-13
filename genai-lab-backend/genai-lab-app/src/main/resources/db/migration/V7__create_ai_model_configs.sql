-- ============================================================
-- V7: Create AI Model Configs Table
-- ============================================================
-- Stores the available AI models that can be selected in the UI.
-- This table drives the model selector dropdown in the frontend.
--
-- Design decisions:
--
-- WHY store model configs in the database?
--   Alternative: hardcode models in application.yml or Java code.
--   Problem with hardcoding:
--     - Adding a new model requires a code deploy
--     - Frontend can't dynamically query available models
--     - Can't disable a model without a deploy (e.g. if API key is revoked)
--   With a DB table:
--     - Add/disable models without redeployment
--     - Frontend calls GET /api/v1/models to get the current list
--     - is_active flag lets us toggle models at runtime
--
-- model_key VARCHAR UNIQUE:
--   The internal identifier used in our code and stored in
--   conversations.model_id. Examples: "gpt-4o-mini", "gpt-4o"
--   This is what gets sent to the AI provider's API.
--
-- display_name VARCHAR:
--   Human-readable name shown in the UI dropdown.
--   Example: "GPT-4o Mini (Fast)", "GPT-4o (Powerful)"
--
-- provider VARCHAR:
--   Which AI provider owns this model.
--   Examples: "openai", "anthropic", "ollama"
--   Used by the AI abstraction layer to route requests to
--   the correct AiChatClient implementation.
--
-- capabilities JSONB:
--   Flexible field for model-specific properties that vary
--   between providers and evolve over time:
--   {
--     "maxContextTokens": 128000,
--     "supportsVision": true,
--     "supportsFunctionCalling": true,
--     "inputPricePerMToken": 0.15,
--     "outputPricePerMToken": 0.60
--   }
--   Using JSONB means we don't need a migration every time
--   a model gets a new capability or pricing change.
--
-- is_active BOOLEAN:
--   Quick toggle to disable a model without deleting its config.
--   Disabled models don't appear in the frontend model selector.
--
-- is_default BOOLEAN:
--   One model can be marked as the default — used when a new
--   conversation is created without specifying a model.
--   Enforced as "at most one default" via a partial unique index.
--
-- context_window INTEGER:
--   Maximum tokens the model can process in a single call
--   (prompt + completion combined). Used to enforce limits
--   during context assembly in the RAG pipeline.
-- ============================================================

CREATE TABLE ai_model_configs (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    model_key      VARCHAR(100) NOT NULL,
    display_name   VARCHAR(200) NOT NULL,
    provider       VARCHAR(50)  NOT NULL,
    context_window INTEGER      NOT NULL,
    capabilities   JSONB        NOT NULL DEFAULT '{}',
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order     INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT ai_model_configs_key_unique
        UNIQUE (model_key)
);

CREATE TRIGGER ai_model_configs_updated_at
    BEFORE UPDATE ON ai_model_configs
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- Partial unique index: enforce at most ONE default model
-- ============================================================
-- A regular unique index on is_default would prevent having more
-- than one TRUE value but also only allow one FALSE value.
-- A PARTIAL unique index on (is_default) WHERE is_default = TRUE
-- means: "only one row can have is_default = TRUE, but any number
-- of rows can have is_default = FALSE".
-- This is a clean PostgreSQL pattern for "exactly one default" logic.
CREATE UNIQUE INDEX idx_ai_model_configs_one_default
    ON ai_model_configs (is_default)
    WHERE is_default = TRUE;

CREATE INDEX idx_ai_model_configs_active
    ON ai_model_configs (provider, sort_order)
    WHERE is_active = TRUE;
