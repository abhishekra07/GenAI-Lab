-- ==========================================================
-- PostgreSQL Initialization Script
-- ==========================================================
-- This script runs ONCE when the PostgreSQL container starts
-- for the very first time (when the data volume is empty).
--
-- WHY do we need this?
-- The pgvector extension is installed in the PostgreSQL binary
-- (it comes with the pgvector/pgvector Docker image), but it
-- must be explicitly enabled in each database that uses it.
-- CREATE EXTENSION registers the extension's data types,
-- functions, and operators into the target database.
--
-- Without this, PostgreSQL won't recognize:
--   - The "vector" column type
--   - The "<=>" cosine distance operator
--   - The "vector_cosine_ops" index operator class
-- ==========================================================

-- Enable pgvector in our application database
CREATE EXTENSION IF NOT EXISTS vector;

-- Enable pg_trgm for potential future full-text search features
-- (trigram-based similarity search — useful for document search)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Enable uuid-ossp for UUID generation in SQL (we may need this in migrations)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Verify extensions were created
DO $$
BEGIN
    RAISE NOTICE 'Extensions enabled:';
    RAISE NOTICE '  - vector (pgvector): %', (SELECT extversion FROM pg_extension WHERE extname = 'vector');
    RAISE NOTICE '  - pg_trgm: %', (SELECT extversion FROM pg_extension WHERE extname = 'pg_trgm');
    RAISE NOTICE '  - uuid-ossp: %', (SELECT extversion FROM pg_extension WHERE extname = 'uuid-ossp');
END $$;
