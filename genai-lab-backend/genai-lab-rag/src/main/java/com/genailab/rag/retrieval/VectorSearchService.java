package com.genailab.rag.retrieval;

import com.genailab.ai.embedding.EmbeddingClient;
import com.genailab.ai.registry.AiProviderRegistry;
import com.genailab.ai.repository.AiModelConfigRepository;
import com.genailab.document.domain.DocumentChunk;
import com.pgvector.PGvector;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import com.genailab.document.repository.DocumentChunkRepository;
import com.genailab.rag.repository.DocumentEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Performs semantic vector search over document embeddings.
 *
 * <p>Given a user query and a document, finds the most semantically
 * relevant chunks using cosine similarity in pgvector.
 *
 * <p>Flow:
 * <ol>
 *   <li>Embed the user's query using the same model used for document chunks</li>
 *   <li>Run cosine similarity search against stored embeddings</li>
 *   <li>Load the actual chunk text for the top-K results</li>
 *   <li>Return ranked chunks with their similarity scores</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorSearchService {

    private final DocumentEmbeddingRepository embeddingRepository;
    private final DocumentChunkRepository chunkRepository;
    private final AiProviderRegistry aiProviderRegistry;
    private final AiModelConfigRepository modelConfigRepository;
    private final DataSource dataSource;

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

        // Register PGvector type and create PGvector parameter for the query
        try (Connection conn = dataSource.getConnection()) {
            PGvector.addVectorType(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register PGvector type", e);
        }

        float[] queryFloats = toFloatArray(queryEmbedding);
        PGvector pgVector = new PGvector(queryFloats);

        // cosine distance threshold = 1 - similarity threshold
        // similarity 0.7 → distance 0.3
        double distanceThreshold = 1.0 - similarityThreshold;

        // Run vector similarity search
        List<Object[]> results = embeddingRepository.findSimilarChunks(
                documentId,
                pgVector,
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
                    // Use provider to look up embedding client — same logic as EmbeddingPipeline
                    String provider = config.getProvider();
                    EmbeddingClient client = aiProviderRegistry.getEmbeddingClientByProvider(provider);
                    if (client != null) return client;
                    return aiProviderRegistry.getDefaultEmbeddingClient();
                })
                .orElseGet(() -> aiProviderRegistry.getDefaultEmbeddingClient());
    }

    private float[] toFloatArray(List<Float> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
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