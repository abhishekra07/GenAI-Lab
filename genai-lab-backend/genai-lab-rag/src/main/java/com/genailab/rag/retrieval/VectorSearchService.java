package com.genailab.rag.retrieval;

import com.genailab.ai.embedding.EmbeddingClient;
import com.genailab.ai.registry.AiProviderRegistry;
import com.genailab.ai.repository.AiModelConfigRepository;
import com.genailab.document.domain.DocumentChunk;
import com.genailab.document.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Performs semantic vector search over document embeddings using JdbcTemplate.
 *
 * <p>WHY JdbcTemplate instead of JPA repository?
 * The pgvector <=> operator requires the query parameter to be of type vector.
 * JPA/Hibernate does not understand the PGvector type — it serializes it as
 * bytea causing "operator does not exist: vector <=> bytea".
 *
 * <p>The fix: inline the vector as a string literal directly in the SQL using
 * String.format(). The vector string is generated from the embedding API
 * response (floats only — no user input), so there is zero SQL injection risk.
 * This completely bypasses the JDBC type system for the vector parameter.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorSearchService {

    private final DocumentChunkRepository chunkRepository;
    private final AiProviderRegistry aiProviderRegistry;
    private final AiModelConfigRepository modelConfigRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${genailab.rag.retrieval.top-k:5}")
    private int defaultTopK;

    @Value("${genailab.rag.retrieval.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Value("${genailab.ai.default-model:gpt-4o-mini}")
    private String defaultModelId;

    public List<RetrievedChunk> search(UUID documentId, String query, String modelId) {
        log.debug("Vector search for document: {}, query length: {}, threshold: {}",
                documentId, query.length(), 1.0 - similarityThreshold);

        EmbeddingClient embeddingClient = resolveEmbeddingClient(modelId);
        List<Float> queryEmbedding = embeddingClient.embed(query);

        // Convert to pgvector string format: [0.1,0.2,0.3,...]
        // Inlined directly into SQL — no JDBC type binding needed for the vector.
        String vectorLiteral = toVectorString(queryEmbedding);
        double distanceThreshold = 1.0 - similarityThreshold;

        String sql = String.format("""
                SELECT de.chunk_id, (de.embedding <=> '%s'::vector) AS distance
                FROM document_embeddings de
                WHERE de.document_id = ?
                AND (de.embedding <=> '%s'::vector) < ?
                ORDER BY de.embedding <=> '%s'::vector
                LIMIT ?
                """, vectorLiteral, vectorLiteral, vectorLiteral);

        List<Object[]> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new Object[]{
                        UUID.fromString(rs.getString("chunk_id")),
                        rs.getDouble("distance")
                },
                documentId,
                distanceThreshold,
                defaultTopK
        );

        if (results.isEmpty()) {
            // Debug: run without threshold to see actual distances
            String debugSql = String.format(
                    "SELECT de.chunk_id, (de.embedding <=> '%s'::vector) AS distance " +
                            "FROM document_embeddings de WHERE de.document_id = ? " +
                            "ORDER BY de.embedding <=> '%s'::vector LIMIT 5",
                    vectorLiteral, vectorLiteral);
            List<Object[]> debugResults = jdbcTemplate.query(debugSql,
                    (rs, rowNum) -> new Object[]{rs.getString("chunk_id"), rs.getDouble("distance")},
                    documentId);
            if (debugResults.isEmpty()) {
                log.warn("No embeddings found at all for document: {} — was it processed?", documentId);
            } else {
                log.warn("Chunks exist but all outside threshold {}. Actual distances: {}",
                        distanceThreshold,
                        debugResults.stream()
                                .map(r -> String.format("%.4f", (double) r[1]))
                                .toList());
            }
            return List.of();
        }

        log.debug("Found {} similar chunks", results.size());

        return results.stream()
                .map(row -> {
                    UUID chunkId = (UUID) row[0];
                    double distance = (double) row[1];
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
                    String provider = config.getProvider();
                    EmbeddingClient client = aiProviderRegistry.getEmbeddingClientByProvider(provider);
                    if (client != null) return client;
                    return aiProviderRegistry.getDefaultEmbeddingClient();
                })
                .orElseGet(() -> aiProviderRegistry.getDefaultEmbeddingClient());
    }

    /**
     * Converts a float list to pgvector string format: [0.1,0.2,0.3]
     * Values come from the embedding API — floats only, no user input.
     */
    private String toVectorString(List<Float> embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            sb.append(embedding.get(i));
            if (i < embedding.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public record RetrievedChunk(DocumentChunk chunk, double similarity) {

        public String toContextString() {
            return String.format(
                    "[Source: chunk %d, similarity: %.2f]\n%s",
                    chunk.getChunkIndex(),
                    similarity,
                    chunk.getContent()
            );
        }
    }
}