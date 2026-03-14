package com.genailab.rag.repository;

import com.genailab.rag.domain.DocumentEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.pgvector.PGvector;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentEmbeddingRepository extends JpaRepository<DocumentEmbedding, UUID> {

    boolean existsByDocumentId(UUID documentId);

    void deleteByDocumentId(UUID documentId);

    /**
     * Vector similarity search using pgvector cosine distance operator.
     * Returns chunk_id and distance score ordered by similarity.
     */
    /**
     * Vector similarity search using pgvector cosine distance operator.
     *
     * The queryEmbedding is passed as a PGvector object — the pgvector-java
     * library handles the JDBC type binding correctly so no manual casting needed.
     */
    @Query(value = """
            SELECT de.chunk_id, (de.embedding <=> :queryEmbedding) AS distance
            FROM document_embeddings de
            WHERE de.document_id = :documentId
            AND (de.embedding <=> :queryEmbedding) < :distanceThreshold
            ORDER BY de.embedding <=> :queryEmbedding
            LIMIT :topK
            """, nativeQuery = true)
    List<Object[]> findSimilarChunks(
            @Param("documentId") UUID documentId,
            @Param("queryEmbedding") PGvector queryEmbedding,
            @Param("topK") int topK,
            @Param("distanceThreshold") double distanceThreshold
    );
}