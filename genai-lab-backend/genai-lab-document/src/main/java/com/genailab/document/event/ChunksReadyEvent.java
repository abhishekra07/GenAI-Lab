package com.genailab.document.event;

import java.util.UUID;

/**
 * Published by DocumentProcessingService after all chunks have been
 * saved to the document_chunks table.
 *
 * <p>The RAG module listens for this event and generates embeddings.
 * This keeps the dependency direction clean:
 * genai-lab-rag → genai-lab-document (correct)
 * genai-lab-document ✗→ genai-lab-rag (avoided via events)
 *
 * @param documentId  the document whose chunks are ready for embedding
 * @param modelId     the model associated with this document,
 *                    used to resolve the correct embedding model
 */
public record ChunksReadyEvent(UUID documentId, String modelId) {
}