package com.genailab.storage.local;

import com.genailab.storage.StorageException;
import com.genailab.storage.StorageResult;
import com.genailab.storage.StorageService;
import com.genailab.storage.config.StorageProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

/**
 * Local filesystem storage implementation.
 *
 * <p>Files are stored under basePath/folder/yyyy/MM/uuid.extension
 * The date-based subdirectory structure prevents any single directory
 * from accumulating too many files, which degrades filesystem performance.
 *
 * <p>This implementation is useful for:
 * <ul>
 *   <li>Local development without MinIO running</li>
 *   <li>Single-server deployments with a mounted volume</li>
 *   <li>Testing — no external service dependency</li>
 * </ul>
 *
 */
@Slf4j
public class LocalStorageService implements StorageService {

    private static final DateTimeFormatter DATE_PATH_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");

    private final Path basePath;

    public LocalStorageService(StorageProperties properties) {
        this.basePath = Paths.get(properties.getLocal().getBasePath()).toAbsolutePath().normalize();
        initBaseDirectory();
    }

    @Override
    public StorageResult store(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new StorageException("Cannot store empty file");
        }

        try {
            String extension = getExtension(file.getOriginalFilename());
            String datePath = LocalDate.now().format(DATE_PATH_FORMAT);
            String uniqueFilename = UUID.randomUUID() + extension;

            // Storage key: folder/yyyy/MM/uuid.ext
            // Same format as MinIO key — swapping implementations
            // does not require updating stored keys in the DB
            String storageKey = folder + "/" + datePath + "/" + uniqueFilename;

            Path targetPath = basePath.resolve(storageKey).normalize();

            // Security check: ensure the resolved path is within basePath.
            // Prevents path traversal attacks if folder contains "../"
            if (!targetPath.startsWith(basePath)) {
                throw new StorageException("Storage path traversal detected: " + storageKey);
            }

            Files.createDirectories(targetPath.getParent());

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            log.debug("Stored file locally: {} ({} bytes)", storageKey, file.getSize());

            return StorageResult.builder()
                    .storageKey(storageKey)
                    .originalFilename(file.getOriginalFilename())
                    .sizeBytes(file.getSize())
                    .contentType(file.getContentType())
                    .build();

        } catch (IOException e) {
            throw new StorageException("Failed to store file: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream retrieve(String storageKey) {
        try {
            Path filePath = resolveAndValidate(storageKey);
            if (!Files.exists(filePath)) {
                throw new StorageException("File not found: " + storageKey);
            }
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new StorageException("Failed to retrieve file: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Path filePath = resolveAndValidate(storageKey);
            Files.deleteIfExists(filePath);
            log.debug("Deleted file: {}", storageKey);
        } catch (IOException e) {
            log.warn("Failed to delete file {}: {}", storageKey, e.getMessage());
        }
    }

    @Override
    public boolean exists(String storageKey) {
        try {
            Path filePath = resolveAndValidate(storageKey);
            return Files.exists(filePath);
        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================
    // Private helpers
    // =========================================================

    private void initBaseDirectory() {
        try {
            Files.createDirectories(basePath);
            log.info("Local storage initialized at: {}", basePath);
        } catch (IOException e) {
            throw new StorageException("Cannot create storage base directory: " + basePath, e);
        }
    }

    private Path resolveAndValidate(String storageKey) {
        Path resolved = basePath.resolve(storageKey).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new StorageException("Invalid storage key — path traversal detected");
        }
        return resolved;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return "." + filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}