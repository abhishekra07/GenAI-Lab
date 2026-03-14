package com.genailab.rag.pipeline;

import com.genailab.ai.embedding.EmbeddingClient;
import com.genailab.ai.registry.AiProviderRegistry;
import com.genailab.ai.repository.AiModelConfigRepository;
import com.genailab.document.domain.DocumentChunk;
import com.genailab.document.repository.DocumentChunkRepository;
import com.genailab.rag.domain.DocumentEmbedding;
import com.genailab.rag.repository.DocumentEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates and stores vector embeddings for document chunks.
 *
 * <p>Called by DocumentProcessingService after chunks are saved.
 * For each chunk, this pipeline:
 * <ol>
 *   <li>Resolves the embedding model from the active model config</li>
 *   <li>Calls the embedding client in batches</li>
 *   <li>Converts float lists to float arrays</li>
 *   <li>Saves embeddings to document_embeddings table</li>
 * </ol>
 *
 * <p>Batching is important — embedding APIs charge per token and have
 * rate limits. Sending 50 chunks one-by-one would be 50 API calls.
 * Batching sends them all in a few calls, much more efficient.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingPipeline {

    // Batch size for embedding API calls.
    // OpenAI allows up to 2048 inputs per request — we use a
    // conservative 20 to stay well within rate limits.
    private static final int BATCH_SIZE = 20;

    // Tracks the count from the last embedDocument() call.
    // Read by EmbeddingEventListener after pipeline completes.
    private int lastEmbeddingCount = 0;

    private final DocumentChunkRepository chunkRepository;
    private final DocumentEmbeddingRepository embeddingRepository;
    private final AiProviderRegistry aiProviderRegistry;
    private final AiModelConfigRepository modelConfigRepository;

    @Value("${genailab.ai.default-model:gpt-4o-mini}")
    private String defaultModelId;

    /**
     * Generate embeddings for all chunks of a document.
     *
     * <p>Resolves the embedding model from the chat model config —
     * e.g. gpt-4o-mini → text-embedding-3-small.
     *
     * @param documentId the document whose chunks need embedding
     * @param modelId    the chat model selected for this document
     *                   (used to resolve the associated embedding model)
     */
    @Transactional
    public void embedDocument(UUID documentId, String modelId) {
        log.info("Starting embedding pipeline for document: {}", documentId);

        // Delete existing embeddings if re-processing
        if (embeddingRepository.existsByDocumentId(documentId)) {
            log.info("Removing existing embeddings for document: {}", documentId);
            embeddingRepository.deleteByDocumentId(documentId);
        }

        // Resolve the embedding model from the model config
        String resolvedModelId = modelId != null ? modelId : defaultModelId;
        EmbeddingClient embeddingClient = resolveEmbeddingClient(resolvedModelId);
        String embeddingModelName = embeddingClient.getModelName();

        // Load all chunks for this document
        List<DocumentChunk> chunks = chunkRepository
                .findByDocumentIdOrderByChunkIndexAsc(documentId);

        if (chunks.isEmpty()) {
            log.warn("No chunks found for document: {} — skipping embedding", documentId);
            return;
        }

        log.info("Embedding {} chunks for document {} using model: {}",
                chunks.size(), documentId, embeddingModelName);

        // Process in batches
        List<DocumentEmbedding> allEmbeddings = new ArrayList<>();
        int totalBatches = (int) Math.ceil((double) chunks.size() / BATCH_SIZE);

        for (int batchNum = 0; batchNum < totalBatches; batchNum++) {
            int fromIndex = batchNum * BATCH_SIZE;
            int toIndex = Math.min(fromIndex + BATCH_SIZE, chunks.size());
            List<DocumentChunk> batch = chunks.subList(fromIndex, toIndex);

            log.debug("Processing batch {}/{} ({} chunks)",
                    batchNum + 1, totalBatches, batch.size());

            // Extract text content from chunks for embedding
            List<String> texts = batch.stream()
                    .map(DocumentChunk::getContent)
                    .toList();

            // Call embedding API — one API call for the whole batch
            List<List<Float>> batchEmbeddings = embeddingClient.embedAll(texts);

            // Convert to DocumentEmbedding entities
            for (int i = 0; i < batch.size(); i++) {
                DocumentChunk chunk = batch.get(i);
                List<Float> embeddingVector = batchEmbeddings.get(i);

                allEmbeddings.add(DocumentEmbedding.builder()
                        .chunkId(chunk.getId())
                        .documentId(documentId)
                        .embedding(toFloatArray(embeddingVector))
                        .embeddingModel(embeddingModelName)
                        .build());
            }
        }

        // Save all embeddings in one batch
        embeddingRepository.saveAll(allEmbeddings);
        lastEmbeddingCount = allEmbeddings.size();

        log.info("Embedding complete for document {}: {} embeddings saved",
                documentId, allEmbeddings.size());
    }

    /** Returns the embedding count from the most recent embedDocument() call. */
    public int getLastEmbeddingCount() {
        return lastEmbeddingCount;
    }

    // =========================================================
    // Private helpers
    // =========================================================

    /**
     * Resolve the embedding client from the chat model config.
     *
     * <p>Looks up the model in ai_model_configs, reads the
     * embeddingModel from capabilities JSONB, then gets the
     * matching EmbeddingClient from the registry.
     *
     * <p>Falls back to the default model if the specified model
     * has no embeddingModel capability configured.
     */
    private EmbeddingClient resolveEmbeddingClient(String modelId) {
        return modelConfigRepository.findByModelKey(modelId)
                .map(config -> {
                    Object embeddingModelObj = config.getCapabilities().get("embeddingModel");
                    if (embeddingModelObj != null) {
                        String embeddingModel = embeddingModelObj.toString();
                        log.debug("Resolved embedding model: {} for chat model: {}",
                                embeddingModel, modelId);
                        return aiProviderRegistry.getEmbeddingClientForModel(embeddingModel);
                    }
                    log.warn("No embeddingModel capability found for model: {} — using default",
                            modelId);
                    return aiProviderRegistry.getDefaultEmbeddingClient();
                })
                .orElseGet(() -> {
                    log.warn("Model config not found for: {} — using default embedding client",
                            modelId);
                    return aiProviderRegistry.getDefaultEmbeddingClient();
                });
    }

    private float[] toFloatArray(List<Float> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }
}