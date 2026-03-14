package com.genailab.document.service;

import com.genailab.document.domain.Document;
import com.genailab.document.domain.DocumentStatus;
import com.genailab.document.dto.DocumentResponse;
import com.genailab.document.repository.DocumentChunkRepository;
import com.genailab.document.repository.DocumentRepository;
import com.genailab.storage.dto.StorageResult;
import com.genailab.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

/**
 * Manages document lifecycle — upload, list, get, delete.
 *
 * <p>Upload flow:
 * <ol>
 *   <li>Validate file type and size</li>
 *   <li>Store file via StorageService (MinIO or local)</li>
 *   <li>Save Document record with status=UPLOADED</li>
 *   <li>Trigger async processing via DocumentProcessingService</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private static final Set<String> ALLOWED_TYPES = Set.of("pdf", "docx", "txt");
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final StorageService storageService;
    private final DocumentProcessingService documentProcessingService;

    /**
     * Accept an uploaded file, persist it, and trigger async processing.
     *
     * <p>Returns immediately after saving — processing happens in the background.
     * The client should poll GET /documents/{id} to track processing status.
     */
    @Transactional
    public DocumentResponse upload(MultipartFile file, UUID userId) {
        validateFile(file);

        String fileType = resolveFileType(file);

        // Store the file — this calls MinIO or local FS depending on config
        StorageResult storageResult = storageService.store(file, "documents");

        Document document = Document.builder()
                .userId(userId)
                .originalFilename(file.getOriginalFilename())
                .storageKey(storageResult.getStorageKey())
                .fileType(fileType)
                .fileSizeBytes(file.getSize())
                .status(DocumentStatus.UPLOADED)
                .build();

        Document saved = documentRepository.save(document);
        log.info("Document uploaded: {} ({}) for user {}",
                saved.getId(), file.getOriginalFilename(), userId);

        // Trigger async processing — returns immediately
        // Processing runs on a separate thread via @Async
        documentProcessingService.processAsync(saved.getId());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> listDocuments(UUID userId, Pageable pageable) {
        return documentRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(UUID documentId, UUID userId) {
        Document document = findOwnedDocument(documentId, userId);
        return toResponse(document);
    }

    @Transactional
    public void deleteDocument(UUID documentId, UUID userId) {
        Document document = findOwnedDocument(documentId, userId);

        // Delete file from storage
        storageService.delete(document.getStorageKey());

        // Delete chunks (embeddings cascade via FK)
        documentChunkRepository.deleteByDocumentId(documentId);

        // Delete document record
        documentRepository.delete(document);

        log.info("Document deleted: {} for user {}", documentId, userId);
    }

    /**
     * Find a document and verify ownership — used by other services
     * (e.g. RAG module) that need to access document data.
     */
    public Document findOwnedDocument(UUID documentId, UUID userId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Document not found: " + documentId));
    }

    // =========================================================
    // Private helpers
    // =========================================================

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File exceeds maximum size of 50MB");
        }
        String type = resolveFileType(file);
        if (!ALLOWED_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "Unsupported file type: " + type +
                            ". Supported types: " + ALLOWED_TYPES);
        }
    }

    private String resolveFileType(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.contains(".")) {
            return "unknown";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private DocumentResponse toResponse(Document d) {
        return DocumentResponse.builder()
                .id(d.getId())
                .originalFilename(d.getOriginalFilename())
                .fileType(d.getFileType())
                .fileSizeBytes(d.getFileSizeBytes())
                .status(d.getStatus())
                .pageCount(d.getPageCount())
                .errorMessage(d.getErrorMessage())
                .processedAt(d.getProcessedAt())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }
}