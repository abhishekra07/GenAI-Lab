package com.genailab.document.event;

import com.genailab.document.domain.DocumentStatus;
import com.genailab.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Listens for EmbeddingCompleteEvent and updates document status.
 *
 * <p>This is the final step of the document processing pipeline.
 * The document is only marked READY after embeddings are stored —
 * at that point the document is fully queryable via RAG.
 *
 * <p>Uses @EventListener (not @TransactionalEventListener) because
 * EmbeddingCompleteEvent is published outside a transaction context
 * (from the async embedding thread).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentStatusListener {

    private final DocumentRepository documentRepository;

    @EventListener
    @Transactional
    public void onEmbeddingComplete(EmbeddingCompleteEvent event) {
        documentRepository.findById(event.documentId()).ifPresent(document -> {
            if (event.success()) {
                document.setStatus(DocumentStatus.READY);
                document.setProcessedAt(Instant.now());
                documentRepository.save(document);
                log.info("Document {} is now READY. {} embeddings generated.",
                        event.documentId(), event.embeddingCount());
            } else {
                document.setStatus(DocumentStatus.FAILED);
                document.setErrorMessage("Embedding failed: " + event.errorMessage());
                document.setProcessedAt(Instant.now());
                documentRepository.save(document);
                log.error("Document {} FAILED during embedding: {}",
                        event.documentId(), event.errorMessage());
            }
        });
    }
}