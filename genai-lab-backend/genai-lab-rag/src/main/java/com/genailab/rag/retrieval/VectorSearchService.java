package com.genailab.rag.retrieval;

import com.genailab.ai.embedding.EmbeddingClient;
import com.genailab.ai.registry.AiProviderRegistry;
import com.genailab.ai.repository.AiModelConfigRepository;
import com.genailab.document.domain.DocumentChunk;
import com.genailab.document.repository.DocumentChunkRepository;
import com.genailab.rag.repository.DocumentEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Performs semantic vector search over document embeddings.
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorSearchService {

    private final DocumentEmbeddingRepository embeddingRepository;
    private final DocumentChunkRepository chunkRepository;
    private final AiProviderRegistry aiProviderRegistry;
    private final AiModelConfigRepository modelConfigRepository;

    @Value("${genailab.rag.retrieval.top-k:5}")
    private int defaultTopK;

    @Value("${genailab.rag.retrieval.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Value("${genailab.ai.default-model:gpt-4o-mini}")
    private String defaultModelId;

    /**
     * Find the most relevant chunks for a query within a document.
     *
     * @param documentId the document to search within
     * @param query      the user's natural language question
     * @param modelId    the chat model (used to resolve embedding model)
     * @return ranked list of relevant chunks, most similar first
     */
    public List<RetrievedChunk> search(UUID documentId, String query, String modelId) {
        log.debug("Vector search for document: {}, query length: {}", documentId, query.length());

        // Embed the query using same model as document chunks
        EmbeddingClient embeddingClient = resolveEmbeddingClient(modelId);
        List<Float> queryEmbedding = embeddingClient.embed(query);

        // Convert to pgvector format string: "[0.1, 0.2, 0.3, ...]"
        String pgVectorString = toPgVectorString(queryEmbedding);

        // cosine distance threshold = 1 - similarity threshold
        // similarity 0.7 -> distance 0.3
        double distanceThreshold = 1.0 - similarityThreshold;

        // Run vector similarity search
        List<Object[]> results = embeddingRepository.findSimilarChunks(
                documentId,
                pgVectorString,
                defaultTopK,
                distanceThreshold
        );

        if (results.isEmpty()) {
            log.debug("No similar chunks found for query in document: {}", documentId);
            return List.of();
        }

        log.debug("Found {} similar chunks", results.size());

        // Load actual chunk content for the results
        return results.stream()
                .map(row -> {
                    UUID chunkId = (UUID) row[0];
                    double distance = ((Number) row[1]).doubleValue();
                    double similarity = 1.0 - distance;

                    return chunkRepository.findById(chunkId)
                            .map(chunk -> new RetrievedChunk(chunk, similarity))
                            .orElse(null);
                })
                .filter(chunk -> chunk != null)
                .toList();
    }

    // =========================================================
    // Private helpers
    // =========================================================

    private EmbeddingClient resolveEmbeddingClient(String modelId) {
        String resolvedModelId = modelId != null ? modelId : defaultModelId;
        return modelConfigRepository.findByModelKey(resolvedModelId)
                .map(config -> {
                    Object embeddingModelObj = config.getCapabilities().get("embeddingModel");
                    if (embeddingModelObj != null) {
                        return aiProviderRegistry.getEmbeddingClientForModel(
                                embeddingModelObj.toString());
                    }
                    return aiProviderRegistry.getDefaultEmbeddingClient();
                })
                .orElseGet(() -> aiProviderRegistry.getDefaultEmbeddingClient());
    }

    /**
     * Convert a float list to pgvector string format.
     * pgvector expects: "[0.1,0.2,0.3]"
     */
    private String toPgVectorString(List<Float> embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            sb.append(embedding.get(i));
            if (i < embedding.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * A retrieved chunk with its similarity score.
     */
    public record RetrievedChunk(DocumentChunk chunk, double similarity) {

        /**
         * Format for inclusion in the AI context prompt.
         * Includes source attribution so the AI can cite its sources.
         */
        public String toContextString() {
            return String.format(
                    "[Source: %s, chunk %d, similarity: %.2f]\n%s",
                    chunk.getDocumentId(),
                    chunk.getChunkIndex(),
                    similarity,
                    chunk.getContent()
            );
        }
    }
}