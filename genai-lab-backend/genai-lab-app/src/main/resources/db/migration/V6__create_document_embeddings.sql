-- ============================================================
-- V6: Create Document Embeddings Table
-- ============================================================
-- Embeddings are numerical vector representations of chunk text.
-- Each embedding is a list of 1536 floating-point numbers that
-- captures the semantic meaning of the chunk.
--
-- Two chunks that mean the same thing will have similar vectors
-- (high cosine similarity), even if they use different words.
-- This is the power of semantic search vs keyword search.
--
-- Design decisions:
--
-- WHY a separate table from document_chunks?
--   We deliberately separate the text (document_chunks) from the
--   vector (document_embeddings). Reasons:
--
--   1. Re-embedding: if we switch embedding models (e.g. from
--      text-embedding-3-small to text-embedding-3-large), we can
--      delete all embeddings and re-generate them WITHOUT losing
--      the original chunks or re-extracting text.
--
--   2. Multiple embeddings: in the future we might store embeddings
--      from multiple models side by side for comparison. A separate
--      table makes this possible without schema changes.
--
--   3. Size: vector(1536) stores 1536 float4 values = 6,144 bytes per row.
--      Keeping them in a separate table means the chunks table stays
--      lean for non-vector queries.
--
-- vector(1536):
--   This is a pgvector column type. 1536 is the output dimension of
--   OpenAI's text-embedding-3-small model. If we switch to a model
--   with different dimensions, we'd add a new column (e.g. vector(3072)
--   for text-embedding-3-large).
--
--   The number in vector(N) MUST match the model's output exactly.
--   Inserting a vector of wrong dimensionality will error at the DB level.
--
-- embedding_model VARCHAR:
--   Records WHICH model produced this embedding.
--   Essential when you have embeddings from multiple models or need
--   to identify which embeddings to invalidate when changing models.
--   Example: "text-embedding-3-small", "text-embedding-3-large"
-- ============================================================

CREATE TABLE document_embeddings (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    chunk_id        UUID        NOT NULL,
    document_id     UUID        NOT NULL,
    embedding       vector(1536) NOT NULL,
    embedding_model VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT document_embeddings_chunk_fk
        FOREIGN KEY (chunk_id) REFERENCES document_chunks (id)
        ON DELETE CASCADE,

    CONSTRAINT document_embeddings_document_fk
        FOREIGN KEY (document_id) REFERENCES documents (id)
        ON DELETE CASCADE,

    CONSTRAINT document_embeddings_chunk_model_unique
        UNIQUE (chunk_id, embedding_model)
        -- One embedding per chunk per model. Prevents duplicates if the
        -- embedding pipeline is accidentally run twice on the same document.
);

-- ============================================================
-- Vector Similarity Search Index
-- ============================================================
-- This is the most important index in the entire schema.
-- Without it, every vector similarity search does a full table scan
-- (comparing the query vector to EVERY row) — O(n) cost.
-- With it, approximate nearest-neighbour search is O(log n).
--
-- Index type: IVFFlat (Inverted File with Flat quantization)
--   - Divides vectors into "lists" (clusters)
--   - At search time, only searches the most relevant lists
--   - Approximate (not exact) — trades tiny accuracy loss for huge speed gain
--   - Good choice for up to ~1M vectors
--
-- Alternative: HNSW (Hierarchical Navigable Small World)
--   - Generally faster queries than IVFFlat
--   - Higher memory usage and slower index builds
--   - Better for larger datasets
--   - We start with IVFFlat (simpler, lower resource usage for development)
--   - Can switch to HNSW later via a new migration without data loss
--
-- vector_cosine_ops:
--   The operator class tells pgvector WHICH similarity measure to optimise.
--   cosine similarity is standard for text embeddings because it measures
--   the angle between vectors (semantic direction) rather than magnitude.
--   OpenAI recommends cosine similarity for their embedding models.
--
--   Other options: vector_l2_ops (Euclidean distance), vector_ip_ops (dot product)
--
-- lists = 100:
--   Number of clusters for IVFFlat.
--   Rule of thumb: sqrt(number_of_rows)
--   100 is reasonable for up to 10,000 embeddings.
--   For production with millions of rows, increase to 1000+.
--   This can be changed later by dropping and recreating the index.
--
-- NOTE: This index requires data to already exist in the table to be
-- effective. An IVFFlat index on an empty table will be inefficient.
-- It's fine to build it now — pgvector handles empty tables gracefully.
-- ============================================================

CREATE INDEX idx_document_embeddings_vector
    ON document_embeddings
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- Standard B-tree index for non-vector lookups:
-- "get all embeddings for document X" (used during re-embedding)
CREATE INDEX idx_document_embeddings_document_id
    ON document_embeddings (document_id);
