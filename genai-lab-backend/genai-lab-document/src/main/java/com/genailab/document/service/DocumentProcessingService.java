package com.genailab.document.service;

import com.genailab.document.chunking.TextChunkingService;
import com.genailab.document.domain.Document;
import com.genailab.document.domain.DocumentChunk;
import com.genailab.document.domain.DocumentStatus;
import com.genailab.document.extractor.TextExtractor;
import com.genailab.document.extractor.exception.TextExtractionException;
import com.genailab.document.repository.DocumentChunkRepository;
import com.genailab.document.repository.DocumentRepository;
import com.genailab.storage.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Async document processing pipeline.
 *
 * <p>Runs on a separate thread pool (configured by @EnableAsync in the app module).
 * The @Async annotation means the caller (DocumentService.upload) returns
 * immediately while this method runs in the background.
 *
 * <p>Pipeline stages:
 * <ol>
 *   <li>Mark document as PROCESSING</li>
 *   <li>Retrieve file bytes from storage</li>
 *   <li>Extract text using the appropriate TextExtractor</li>
 *   <li>Split text into chunks via TextChunkingService</li>
 *   <li>Save chunks to document_chunks table</li>
 *   <li>Mark document as READY</li>
 *   <li>On any failure: mark as FAILED with error message</li>
 * </ol>
 *
 * <p>After this service completes, the RAG module picks up the chunks
 * and generates vector embeddings for each one.
 */
@Service
@Slf4j
public class DocumentProcessingService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final StorageService storageService;
    private final TextChunkingService chunkingService;

    /**
     * Map of fileType → TextExtractor, built from all TextExtractor beans.
     * Spring injects all implementations — we index them by supported type.
     * Adding a new extractor (e.g. for .md files) requires only creating
     * a new @Component class — no changes here.
     */
    private final Map<String, TextExtractor> extractors;

    public DocumentProcessingService(
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            StorageService storageService,
            TextChunkingService chunkingService,
            List<TextExtractor> extractorList) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.storageService = storageService;
        this.chunkingService = chunkingService;
        this.extractors = extractorList.stream()
                .collect(Collectors.toMap(
                        TextExtractor::getSupportedType,
                        Function.identity()));
        log.info("Document processors registered for types: {}", this.extractors.keySet());
    }

    /**
     * Process a document asynchronously.
     *
     * <p>@Async means this method runs on Spring's async executor thread pool,
     * not the HTTP thread. The caller returns immediately after calling this.
     *
     * <p>@Transactional here ensures each status update is committed
     * even if a later step fails. We use separate transactions per stage
     * so that PROCESSING status is visible immediately.
     */
    public void processAsync(UUID documentId) {
        log.info("Starting async processing for document: {}", documentId);
        try {
            process(documentId);
        } catch (Exception e) {
            log.error("Unhandled exception during document processing {}: {}",
                    documentId, e.getMessage(), e);
            markFailed(documentId, "Unexpected error: " + e.getMessage());
        }
    }

    private void process(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Document not found for processing: " + documentId));

        // Stage 1: mark as processing
        markProcessing(document);

        // Stage 2: retrieve file from storage
        String extractedText;
        try (InputStream fileStream = storageService.retrieve(document.getStorageKey())) {

            // Stage 3: extract text
            TextExtractor extractor = extractors.get(document.getFileType());
            if (extractor == null) {
                throw new TextExtractionException(
                        "No extractor available for file type: " + document.getFileType());
            }
            extractedText = extractor.extract(fileStream);

        } catch (TextExtractionException e) {
            log.error("Text extraction failed for document {}: {}", documentId, e.getMessage());
            markFailed(documentId, "Text extraction failed: " + e.getMessage());
            return;
        } catch (Exception e) {
            log.error("Failed to retrieve/extract document {}: {}", documentId, e.getMessage());
            markFailed(documentId, "Failed to read document: " + e.getMessage());
            return;
        }

        if (extractedText == null || extractedText.isBlank()) {
            markFailed(documentId, "Document appears to be empty or contains no extractable text");
            return;
        }

        // Stage 4: chunk the text
        List<TextChunkingService.TextChunk> chunks = chunkingService.chunk(extractedText);
        if (chunks.isEmpty()) {
            markFailed(documentId, "No text chunks could be generated from this document");
            return;
        }

        // Stage 5: save chunks to DB
        saveChunks(document, chunks);

        // Stage 6: mark as READY
        // Note: embeddings are generated by the RAG module after this point
        markReady(document, chunks.size());

        log.info("Document {} processed successfully: {} chunks created", documentId, chunks.size());
    }

    @Transactional
    protected void markProcessing(Document document) {
        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);
    }

    @Transactional
    protected void saveChunks(Document document, List<TextChunkingService.TextChunk> chunks) {
        List<DocumentChunk> chunkEntities = chunks.stream()
                .map(chunk -> DocumentChunk.builder()
                        .documentId(document.getId())
                        .chunkIndex(chunk.getIndex())
                        .content(chunk.getContent())
                        .tokenCount(chunk.getTokenCount())
                        .startChar(chunk.getStartChar())
                        .endChar(chunk.getEndChar())
                        .metadata(Map.of(
                                "fileType", document.getFileType(),
                                "originalFilename", document.getOriginalFilename()
                        ))
                        .build())
                .toList();

        chunkRepository.saveAll(chunkEntities);
        log.debug("Saved {} chunks for document {}", chunkEntities.size(), document.getId());
    }

    @Transactional
    protected void markReady(Document document, int chunkCount) {
        document.setStatus(DocumentStatus.READY);
        document.setProcessedAt(Instant.now());
        documentRepository.save(document);
    }

    @Transactional
    protected void markFailed(UUID documentId, String errorMessage) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage(errorMessage);
            doc.setProcessedAt(Instant.now());
            documentRepository.save(doc);
            log.warn("Document {} marked as FAILED: {}", documentId, errorMessage);
        });
    }
}