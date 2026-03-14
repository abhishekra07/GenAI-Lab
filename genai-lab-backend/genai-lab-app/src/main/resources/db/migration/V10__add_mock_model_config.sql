-- Inserts a mock model entry so AiProviderRegistry can resolve
-- modelId="mock" → provider="mock" from the database.
--
-- This model is intended for development and testing only.
-- ============================================================

INSERT INTO ai_model_configs
    (model_key, display_name, provider, context_window, capabilities, is_active, is_default, sort_order)
VALUES
    (
        'mock',
        'Mock AI (Development Only)',
        'mock',
        999999,
        '{
            "description": "Mock AI provider for development and testing. Returns instant fake responses.",
            "supportsVision": false,
            "supportsFunctionCalling": false,
            "supportsStreaming": true,
            "inputPricePerMToken": 0,
            "outputPricePerMToken": 0,
            "devOnly": true
        }'::jsonb,
        TRUE,
        FALSE,
        99
    );