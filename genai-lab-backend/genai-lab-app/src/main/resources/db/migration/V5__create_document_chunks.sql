-- ============================================================
-- V5: Create Document Chunks Table
-- ============================================================
-- After text is extracted from a document, it's split into chunks.
-- Each chunk is a piece of text that will be independently embedded
-- and stored as a vector for semantic search.
--
-- Design decisions:
--
-- WHY split documents into chunks at all?
--   AI models have a context window limit (e.g. GPT-4o: 128k tokens).
--   A large PDF might have 200,000 tokens. We can't send the whole
--   document to the AI for every query. Instead we:
--     1. Split into chunks (e.g. 512 tokens each with 50-token overlap)
--     2. Embed each chunk as a vector
--     3. At query time, find the top-K most similar chunks
--     4. Send only those chunks as context to the AI
--   This is the core of RAG (Retrieval Augmented Generation).
--
-- chunk_index INTEGER:
--   The position of this chunk within the document (0-based).
--   Critical for reconstructing reading order and for showing
--   "source: page 3, paragraph 2" context citations to the user.
--
-- content TEXT NOT NULL:
--   The actual text of this chunk. This is what gets embedded
--   and what gets sent to the AI as context when retrieved.
--
-- token_count INTEGER:
--   How many tokens this chunk contains. Populated during chunking.
--   Used to ensure we don't exceed the AI model's context window
--   when assembling multiple retrieved chunks.
--
-- metadata JSONB:
--   Flexible key-value store for chunk-specific information.
--   Different file types produce different metadata:
--     PDF:  {"page_number": 3, "section": "Introduction"}
--     DOCX: {"heading": "Chapter 2", "paragraph_index": 5}
--     TXT:  {"line_start": 100, "line_end": 150}
--   JSONB lets us add new metadata fields without schema changes.
--   JSONB (binary JSON) is preferred over JSON (text) because:
--   - Indexed (we can create GIN indexes on JSONB fields)
--   - Faster to query
--   - Deduplicates keys automatically
--
-- start_char / end_char (nullable):
--   Character offsets within the original extracted text.
--   Used to highlight the source passage in the document viewer.
-- ============================================================

CREATE TABLE document_chunks (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID        NOT NULL,
    chunk_index INTEGER     NOT NULL,
    content     TEXT        NOT NULL,
    token_count INTEGER,
    start_char  INTEGER,
    end_char    INTEGER,
    metadata    JSONB       NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT document_chunks_document_fk
        FOREIGN KEY (document_id) REFERENCES documents (id)
        ON DELETE CASCADE,
        -- ON DELETE CASCADE: deleting a document deletes all its chunks.
        -- This is correct — chunks have no meaning without their parent document.

    CONSTRAINT document_chunks_index_unique
        UNIQUE (document_id, chunk_index)
        -- Each chunk position within a document must be unique.
        -- Prevents duplicate chunks if the processing pipeline runs twice.
);

-- ============================================================
-- Indexes
-- ============================================================

-- Retrieve all chunks for a document in order (used during embedding pipeline
-- and when displaying source attribution)
CREATE INDEX idx_document_chunks_document_id
    ON document_chunks (document_id, chunk_index ASC);

-- GIN index on metadata JSONB — enables fast queries like:
--   WHERE metadata @> '{"page_number": 3}'
-- Useful when filtering retrieved chunks by page or section.
CREATE INDEX idx_document_chunks_metadata
    ON document_chunks USING GIN (metadata);
