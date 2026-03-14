package com.genailab.rag.event;

import com.genailab.document.event.ChunksReadyEvent;
import com.genailab.document.event.EmbeddingCompleteEvent;
import com.genailab.rag.pipeline.EmbeddingPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens for chunk-ready events from the document module and
 * triggers the embedding pipeline.
 *
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmbeddingEventListener {

    private final EmbeddingPipeline embeddingPipeline;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Triggered after chunks have been committed to the DB.
     *
     * <p>@Async — runs on a separate thread so document upload
     * HTTP response is not blocked.
     *
     * <p>@TransactionalEventListener(AFTER_COMMIT) — only fires
     * after the chunk-saving transaction commits, ensuring chunks
     * are visible when the embedding pipeline queries them.
     */
    @Async
    @EventListener
    public void onChunksReady(ChunksReadyEvent event) {
        log.info("Chunks ready event received for document: {} — starting embedding",
                event.documentId());
        try {
            embeddingPipeline.embedDocument(event.documentId(), event.modelId());
            eventPublisher.publishEvent(
                    EmbeddingCompleteEvent.success(event.documentId(),
                            embeddingPipeline.getLastEmbeddingCount()));
        } catch (Exception e) {
            log.error("Embedding failed for document {}: error: {}, cause : {}", event.documentId(), e.getMessage(), e.getCause());
            eventPublisher.publishEvent(
                    EmbeddingCompleteEvent.failure(event.documentId(), e.getMessage()));
        }
    }
}