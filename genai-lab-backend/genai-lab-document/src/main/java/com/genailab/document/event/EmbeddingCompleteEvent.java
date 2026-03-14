package com.genailab.document.event;

import java.util.UUID;

/**
 * Published by the RAG module's EmbeddingEventListener after all
 * embeddings have been successfully generated and stored.
 *
 * <p>DocumentProcessingService listens for this event to mark
 * the document as READY — only after embeddings exist is the
 * document actually queryable via RAG.
 *
 * @param documentId    the document that has been fully embedded
 * @param embeddingCount number of embeddings generated
 * @param success        true if embedding succeeded, false if it failed
 * @param errorMessage   populated when success=false
 */
public record EmbeddingCompleteEvent(
        UUID documentId,
        int embeddingCount,
        boolean success,
        String errorMessage) {

    /** Convenience factory for successful completion. */
    public static EmbeddingCompleteEvent success(UUID documentId, int count) {
        return new EmbeddingCompleteEvent(documentId, count, true, null);
    }

    /** Convenience factory for failure. */
    public static EmbeddingCompleteEvent failure(UUID documentId, String error) {
        return new EmbeddingCompleteEvent(documentId, 0, false, error);
    }
}