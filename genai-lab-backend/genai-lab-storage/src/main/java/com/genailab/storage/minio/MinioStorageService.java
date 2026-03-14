package com.genailab.storage.minio;

import com.genailab.storage.exception.StorageException;
import com.genailab.storage.dto.StorageResult;
import com.genailab.storage.service.StorageService;
import com.genailab.storage.config.StorageProperties;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * MinIO storage implementation — S3-compatible object storage.
 *
 * <p>Uses the MinIO Java SDK to interact with MinIO.
 * Since MinIO is S3-compatible, this implementation would work
 * with AWS S3 too by changing the endpoint and credentials —
 * no code changes required.
 *
 * <p>Object keys follow the same format as LocalStorageService:
 * folder/yyyy/MM/uuid.extension
 * This ensures storage keys stored in the DB are portable between
 * implementations.
 */
@Slf4j
public class MinioStorageService implements StorageService {

    private static final DateTimeFormatter DATE_PATH_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");

    private final MinioClient minioClient;
    private final String bucketName;

    public MinioStorageService(StorageProperties properties) {
        StorageProperties.Minio config = properties.getMinio();

        this.minioClient = MinioClient.builder()
                .endpoint(config.getEndpoint())
                .credentials(config.getAccessKey(), config.getSecretKey())
                .build();

        this.bucketName = config.getBucketName();
        log.info("MinIO storage initialized. Endpoint: {}, Bucket: {}", config.getEndpoint(), bucketName);
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
            String storageKey = folder + "/" + datePath + "/" + uniqueFilename;

            String contentType = file.getContentType() != null
                    ? file.getContentType()
                    : "application/octet-stream";

            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(storageKey)
                                .stream(inputStream, file.getSize(), -1)
                                .contentType(contentType)
                                .build()
                );
            }

            log.debug("Stored file in MinIO: {} ({} bytes)", storageKey, file.getSize());

            return StorageResult.builder()
                    .storageKey(storageKey)
                    .originalFilename(file.getOriginalFilename())
                    .sizeBytes(file.getSize())
                    .contentType(contentType)
                    .build();

        } catch (Exception e) {
            throw new StorageException("Failed to store file in MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream retrieve(String storageKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storageKey)
                            .build()
            );
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                throw new StorageException("File not found in MinIO: " + storageKey);
            }
            throw new StorageException("Failed to retrieve file from MinIO: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new StorageException("Failed to retrieve file from MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storageKey)
                            .build()
            );
            log.debug("Deleted file from MinIO: {}", storageKey);
        } catch (Exception e) {
            // Log but don't throw — delete is idempotent
            log.warn("Failed to delete file {} from MinIO: {}", storageKey, e.getMessage());
        }
    }

    @Override
    public boolean exists(String storageKey) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storageKey)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                return false;
            }
            log.warn("Error checking existence of {}: {}", storageKey, e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Error checking existence of {}: {}", storageKey, e.getMessage());
            return false;
        }
    }

    // =========================================================
    // Private helpers
    // =========================================================

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return "." + filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}