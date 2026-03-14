package com.genailab.rag.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores a vector embedding for a single document chunk.
 *
 * <p>The embedding column uses pgvector's vector type (1536 dimensions).
 * Hibernate maps this via @JdbcTypeCode(SqlTypes.VECTOR) with @Array(length = 1536).
 *
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

    /**
     * The vector embedding — 1536 float values for text-embedding-3-small.
     *
     * <p>Stored as pgvector's vector(1536) type.
     * @Array(length = 1536) tells Hibernate the array size.
     * @JdbcTypeCode(SqlTypes.VECTOR) maps it to pgvector's vector SQL type.
     */
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column(name = "embedding", nullable = false, columnDefinition = "vector(1536)")
    private float[] embedding;

    /**
     * Which embedding model produced this vector.
     * Example: "text-embedding-3-small", "mock-embedding-model"
     * Stored for traceability — if you switch models, you know
     * which embeddings need to be regenerated.
     */
    @Column(name = "embedding_model", nullable = false, length = 100)
    private String embeddingModel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}