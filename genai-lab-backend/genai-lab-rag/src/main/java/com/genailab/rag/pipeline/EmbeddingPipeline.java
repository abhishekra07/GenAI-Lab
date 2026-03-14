package com.genailab.rag.pipeline;

import com.genailab.ai.embedding.EmbeddingClient;
import com.genailab.ai.registry.AiProviderRegistry;
import com.genailab.ai.repository.AiModelConfigRepository;
import com.genailab.document.domain.DocumentChunk;
import com.genailab.document.repository.DocumentChunkRepository;
import com.genailab.rag.repository.DocumentEmbeddingRepository;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Generates and stores vector embeddings for document chunks.
 *
 * <p>Uses the pgvector-java library (PGvector class) to properly bind
 * vector values as JDBC parameters. This is the correct, standard way
 * to insert pgvector data from Java — no string formatting, no manual
 * casting, no SQL injection risk.
 *
 * <p>The PGvector type must be registered on the PostgreSQL JDBC connection
 * before use. We do this once via DataSource.getConnection() at startup.
 */
@Service
@Slf4j
public class EmbeddingPipeline {

    private static final int BATCH_SIZE = 20;

    private final DocumentChunkRepository chunkRepository;
    private final DocumentEmbeddingRepository embeddingRepository;
    private final AiProviderRegistry aiProviderRegistry;
    private final AiModelConfigRepository modelConfigRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Value("${genailab.ai.default-model:gpt-4o-mini}")
    private String defaultModelId;

    private int lastEmbeddingCount = 0;

    public EmbeddingPipeline(
            DocumentChunkRepository chunkRepository,
            DocumentEmbeddingRepository embeddingRepository,
            AiProviderRegistry aiProviderRegistry,
            AiModelConfigRepository modelConfigRepository,
            JdbcTemplate jdbcTemplate,
            DataSource dataSource) {
        this.chunkRepository = chunkRepository;
        this.embeddingRepository = embeddingRepository;
        this.aiProviderRegistry = aiProviderRegistry;
        this.modelConfigRepository = modelConfigRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Transactional
    public void embedDocument(UUID documentId, String modelId) {
        log.info("Starting embedding pipeline for document: {}", documentId);

        if (embeddingRepository.existsByDocumentId(documentId)) {
            log.info("Removing existing embeddings for document: {}", documentId);
            embeddingRepository.deleteByDocumentId(documentId);
        }

        String resolvedModelId = modelId != null ? modelId : defaultModelId;
        EmbeddingClient embeddingClient = resolveEmbeddingClient(resolvedModelId);
        String embeddingModelName = embeddingClient.getModelName();

        List<DocumentChunk> chunks = chunkRepository
                .findByDocumentIdOrderByChunkIndexAsc(documentId);

        if (chunks.isEmpty()) {
            log.warn("No chunks found for document: {} — skipping embedding", documentId);
            lastEmbeddingCount = 0;
            return;
        }

        log.info("Embedding {} chunks for document {} using model: {}",
                chunks.size(), documentId, embeddingModelName);

        // Register PGvector type on a connection — required once per DataSource
        // so PostgreSQL JDBC driver knows how to handle the vector type
        registerPgVectorType();

        int totalInserted = 0;
        int totalBatches = (int) Math.ceil((double) chunks.size() / BATCH_SIZE);

        for (int batchNum = 0; batchNum < totalBatches; batchNum++) {
            int fromIndex = batchNum * BATCH_SIZE;
            int toIndex = Math.min(fromIndex + BATCH_SIZE, chunks.size());
            List<DocumentChunk> batch = chunks.subList(fromIndex, toIndex);

            log.debug("Processing batch {}/{} ({} chunks)",
                    batchNum + 1, totalBatches, batch.size());

            List<String> texts = batch.stream()
                    .map(DocumentChunk::getContent)
                    .toList();

            List<List<Float>> batchEmbeddings = embeddingClient.embedAll(texts);

            for (int i = 0; i < batch.size(); i++) {
                DocumentChunk chunk = batch.get(i);
                List<Float> embeddingValues = batchEmbeddings.get(i);

                // PGvector handles all type conversion internally.
                // The PostgreSQL JDBC driver knows exactly how to bind this.
                PGvector pgVector = new PGvector(toFloatArray(embeddingValues));

                // Use Timestamp.from(Instant) — JDBC does not know how to
                // bind java.time.Instant directly; it needs java.sql.Timestamp.
                // UUID is handled natively by the PostgreSQL JDBC driver.
                jdbcTemplate.update(
                        "INSERT INTO document_embeddings " +
                                "(id, chunk_id, document_id, embedding, embedding_model, created_at) " +
                                "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?)",
                        chunk.getId(),
                        documentId,
                        pgVector,
                        embeddingModelName,
                        Timestamp.from(Instant.now())
                );
                totalInserted++;
            }
        }

        lastEmbeddingCount = totalInserted;
        log.info("Embedding complete for document {}: {} embeddings saved",
                documentId, totalInserted);
    }

    public int getLastEmbeddingCount() {
        return lastEmbeddingCount;
    }

    // =========================================================
    // Private helpers
    // =========================================================

    /**
     * Register PGvector with the PostgreSQL JDBC connection.
     *
     * <p>This is required by the pgvector-java library before any
     * vector values can be bound as JDBC parameters. Without this,
     * the driver doesn't know how to serialize/deserialize the vector type.
     *
     * <p>We call this once before batch processing. The registration
     * is connection-scoped — HikariCP manages connection pooling so
     * we use a dedicated connection just for registration.
     */
    private void registerPgVectorType() {
        try (Connection conn = dataSource.getConnection()) {
            PGvector.addVectorType(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register PGvector type", e);
        }
    }

    private EmbeddingClient resolveEmbeddingClient(String modelId) {
        return modelConfigRepository.findByModelKey(modelId)
                .map(config -> {
                    String provider = config.getProvider();
                    log.debug("Resolving embedding client for chat model: {} via provider: {}",
                            modelId, provider);
                    EmbeddingClient client = aiProviderRegistry.getEmbeddingClientByProvider(provider);
                    if (client != null) return client;
                    log.warn("No embedding client for provider: {} — using default", provider);
                    return aiProviderRegistry.getDefaultEmbeddingClient();
                })
                .orElseGet(() -> {
                    log.warn("Model config not found for: {} — using default", modelId);
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