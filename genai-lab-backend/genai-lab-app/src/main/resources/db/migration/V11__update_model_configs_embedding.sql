-- This links chat models to their embedding counterparts:
--   gpt-4o-mini  → text-embedding-3-small
--   gpt-4o       → text-embedding-3-small
--   mock         → mock-embedding-model
-- ============================================================

-- OpenAI models — use text-embedding-3-small
-- 1536 dimensions, fast, cost-effective, excellent quality
UPDATE ai_model_configs
SET capabilities = capabilities || '{
    "embeddingModel": "text-embedding-3-small",
    "embeddingDimensions": 1536
}'::jsonb
WHERE provider = 'openai';

-- Mock model — use mock embedding
UPDATE ai_model_configs
SET capabilities = capabilities || '{
    "embeddingModel": "mock",
    "embeddingDimensions": 1536
}'::jsonb
WHERE provider = 'mock';