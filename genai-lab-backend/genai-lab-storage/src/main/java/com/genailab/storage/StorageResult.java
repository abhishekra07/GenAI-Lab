package com.genailab.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a successful storage operation.
 *
 * <p>The storageKey is the most important field — it is what gets
 * persisted in the documents table (storage_key column) and is used
 * to retrieve or delete the file later.
 *
 * <p>The storageKey format differs per implementation:
 * <ul>
 *   <li>Local: relative path from base dir — "documents/2024/abc123.pdf"</li>
 *   <li>MinIO: object key in the bucket — "documents/2024/abc123.pdf"</li>
 * </ul>
 *
 * Both implementations use the same key format intentionally —
 * migrating from local to MinIO does not require updating stored keys.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageResult {

    /**
     * The unique key identifying this file in storage.
     * Persisted to DB. Used for all future retrieve/delete operations.
     */
    private String storageKey;

    /**
     * Original filename as uploaded by the user.
     * Stored for display purposes only — never used as the storage key.
     */
    private String originalFilename;

    /** File size in bytes. */
    private long sizeBytes;

    /** Detected or provided content type. */
    private String contentType;
}