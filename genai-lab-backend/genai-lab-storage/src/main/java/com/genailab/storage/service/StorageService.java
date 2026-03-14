package com.genailab.storage.service;

import com.genailab.storage.dto.StorageResult;
import com.genailab.storage.exception.StorageException;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * Abstraction for file storage operations.
 *
 * <p>All document upload/retrieval code depends on this interface only.
 *
 * <p>Implementations are selected at runtime via StorageConfig
 * based on the genailab.storage.provider configuration property.
 */
public interface StorageService {

    /**
     * Store an uploaded file and return its storage result.
     *
     * @param file     the uploaded multipart file
     * @param folder   logical folder prefix, e.g. "documents" or "avatars"
     * @return result containing the storage key and file metadata
     */
    StorageResult store(MultipartFile file, String folder);

    /**
     * Retrieve a file as an InputStream.
     *
     * @param storageKey the key returned by {@link #store}
     * @return an InputStream of the file contents
     * @throws StorageException if the file cannot be found or read
     */
    InputStream retrieve(String storageKey);

    /**
     * Delete a file from storage.
     *
     * <p>Does not throw if the file does not exist — deletion is idempotent.
     *
     * @param storageKey the key returned by {@link #store}
     */
    void delete(String storageKey);

    /**
     * Check whether a file exists in storage.
     *
     * @param storageKey the key to check
     * @return true if the file exists
     */
    boolean exists(String storageKey);
}