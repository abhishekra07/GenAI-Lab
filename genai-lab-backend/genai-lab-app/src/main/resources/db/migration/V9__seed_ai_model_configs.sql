-- ============================================================
-- V9: Seed Default AI Model Configurations
-- ============================================================
-- Inserts the default set of OpenAI models available at launch.
-- This data migration runs once after the schema is created.
--
-- WHY put seed data in a migration?
--   The ai_model_configs table is reference data that the application
--   REQUIRES to function. Without at least one active model, users
--   can't create conversations.
--
--   Putting it in a migration means:
--   - Every environment (dev, staging, prod) gets the same baseline data
--   - No manual setup steps after deployment
--   - New team members get working data automatically
--
--   This is different from test fixtures (which belong in test code)
--   or user data (which should never be in migrations).
--   Reference/configuration data that the app needs to function
--   is the right thing to seed in migrations.
--
-- Model selection rationale:
--
-- gpt-4o-mini:
--   Fast, cheap, capable. Best default for most use cases.
--   Cost: ~$0.15/1M input tokens, $0.60/1M output tokens (as of 2024).
--   Context: 128,000 tokens.
--
-- gpt-4o:
--   More capable, slower, more expensive. For complex tasks.
--   Cost: ~$2.50/1M input tokens, $10/1M output tokens (as of 2024).
--   Context: 128,000 tokens.
--
-- NOTE: Pricing changes over time. We store it in the capabilities JSONB
-- column so it can be updated with a new migration without schema changes.
-- ============================================================

INSERT INTO ai_model_configs
    (model_key, display_name, provider, context_window, capabilities, is_active, is_default, sort_order)
VALUES
    (
        'gpt-4o-mini',
        'GPT-4o Mini',
        'openai',
        128000,
        '{
            "description": "Fast and affordable model for everyday tasks",
            "supportsVision": true,
            "supportsFunctionCalling": true,
            "supportsStreaming": true,
            "inputPricePerMToken": 0.15,
            "outputPricePerMToken": 0.60,
            "recommended": true
        }'::jsonb,
        TRUE,
        TRUE,   -- DEFAULT model
        1
    ),
    (
        'gpt-4o',
        'GPT-4o',
        'openai',
        128000,
        '{
            "description": "Most capable GPT-4 model for complex tasks",
            "supportsVision": true,
            "supportsFunctionCalling": true,
            "supportsStreaming": true,
            "inputPricePerMToken": 2.50,
            "outputPricePerMToken": 10.00
        }'::jsonb,
        TRUE,
        FALSE,
        2
    );
