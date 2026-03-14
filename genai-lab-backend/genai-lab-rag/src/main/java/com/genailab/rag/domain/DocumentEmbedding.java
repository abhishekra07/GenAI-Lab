package com.genailab.rag.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a stored vector embedding for a document chunk.
 *
 * <p>Maps to the document_embeddings table.
 *
 * <p>NOTE: The embedding column (vector type) is intentionally omitted
 * from this JPA entity. Hibernate cannot map float[] to pgvector's vector
 * type without additional integration — it serializes as bytea instead.
 *
 * <p>Vector inserts are handled by EmbeddingPipeline via JdbcTemplate
 * with an explicit CAST to vector type. This entity is used for
 * metadata queries (exists check, delete by document) only.
 */
@Entity
@Table(name = "document_embeddings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "chunk_id", nullable = false)
    private UUID chunkId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "embedding_model", nullable = false, length = 100)
    private String embeddingModel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}