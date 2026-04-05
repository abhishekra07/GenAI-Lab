package com.genailab.storage.service;

import com.genailab.storage.dto.StorageResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * Abstraction for file storage operations.
 *
 * <p>Implementations: LocalStorageService, MinioStorageService.
 * Selected at runtime via StorageConfig based on genailab.storage.provider.
 */
public interface StorageService {

    StorageResult store(MultipartFile file, String folder);

    /**
     * Retrieve a file as an InputStream.
     * Caller is responsible for closing the stream — use try-with-resources.
     */
    InputStream retrieve(String storageKey);

    void delete(String storageKey);

    boolean exists(String storageKey);

    /**
     * Get the content type (MIME type) for a given storage key.
     * Used when streaming files to the frontend.
     * Default implementation infers from file extension.
     */
    default String getContentType(String storageKey) {
        if (storageKey == null) return "application/octet-stream";
        String lower = storageKey.toLowerCase();
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".txt"))  return "text/plain";
        return "application/octet-stream";
    }
}