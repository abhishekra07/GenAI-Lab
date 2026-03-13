-- ============================================================
-- V4: Create Documents Table
-- ============================================================
-- Documents represent uploaded files in the Document Analyzer app.
-- A document goes through a processing pipeline after upload:
--   UPLOADED → PROCESSING → READY (or FAILED)
--
-- Design decisions:
--
-- status as VARCHAR with CHECK (not enum):
--   Same reasoning as messages.role — easier to extend via migration.
--   States: UPLOADED, PROCESSING, READY, FAILED
--
--   UPLOADED:   File received, stored in MinIO/local, not yet processed.
--   PROCESSING: Text extraction + chunking + embedding in progress.
--               Documents stay here if the server crashes mid-processing.
--               A scheduled job should detect stale PROCESSING documents
--               and re-queue them. (We'll implement this later.)
--   READY:      All chunks embedded, vector search available.
--   FAILED:     Processing failed. error_message explains why.
--
-- original_filename vs storage_path:
--   original_filename: what the user uploaded ("My Report Q3 2024.pdf")
--   storage_key: the path/key we assigned in MinIO/local storage
--                (UUID-based, e.g. "documents/abc123/report.pdf")
--   We never use the original filename as the storage key because:
--   - Filenames can contain path traversal characters (../../etc/passwd)
--   - Two users might upload files with the same name
--   - Spaces and special chars cause issues in S3/file paths
--
-- file_size_bytes BIGINT:
--   BIGINT not INT because files can exceed 2GB (INT max ~2.1 billion).
--   A 3GB PDF would overflow INT.
--
-- file_type VARCHAR:
--   Stored content-type or extension: "application/pdf", "docx", "txt"
--   Used by the text extraction layer to pick the right extractor.
--
-- page_count (nullable):
--   Populated after extraction. NULL until processing completes.
--   Used to show "X pages" in the UI.
--
-- error_message TEXT (nullable):
--   Only populated when status = 'FAILED'. Stores the exception
--   message or a human-readable explanation of what went wrong.
--
-- processed_at (nullable):
--   Timestamp when status moved to READY or FAILED.
--   NULL while still UPLOADED or PROCESSING.
--   Lets us calculate processing duration and detect stuck documents.
-- ============================================================

CREATE TABLE documents (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL,
    original_filename VARCHAR(500) NOT NULL,
    storage_key       VARCHAR(1000) NOT NULL,
    file_type         VARCHAR(100) NOT NULL,
    file_size_bytes   BIGINT       NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED',
    page_count        INTEGER,
    error_message     TEXT,
    processed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT documents_user_fk
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT documents_status_check
        CHECK (status IN ('UPLOADED', 'PROCESSING', 'READY', 'FAILED')),

    CONSTRAINT documents_storage_key_unique
        UNIQUE (storage_key)
        -- Storage keys must be unique globally — no two documents
        -- should map to the same storage object.
);

CREATE TRIGGER documents_updated_at
    BEFORE UPDATE ON documents
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- Indexes
-- ============================================================

-- Primary access pattern: "get all documents for user X"
CREATE INDEX idx_documents_user_id
    ON documents (user_id, created_at DESC);

-- Status monitoring: find all documents stuck in PROCESSING
-- (useful for a background job that detects and retries failed processing)
CREATE INDEX idx_documents_status
    ON documents (status, created_at) WHERE status IN ('PROCESSING', 'FAILED');
