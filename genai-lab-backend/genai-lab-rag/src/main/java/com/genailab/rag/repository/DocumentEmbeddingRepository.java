package com.genailab.rag.repository;

import com.genailab.rag.domain.DocumentEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentEmbeddingRepository extends JpaRepository<DocumentEmbedding, UUID> {

    /**
     * Check if embeddings already exist for a document.
     * Used before re-embedding to avoid duplicates.
     */
    boolean existsByDocumentId(UUID documentId);

    /**
     * Delete all embeddings for a document.
     * Used when reprocessing or deleting a document.
     */
    void deleteByDocumentId(UUID documentId);

    /**
     * Vector similarity search — the core of RAG retrieval.
     *
     * <p>Uses pgvector's cosine distance operator (<=>) to find the
     * top-K most semantically similar chunks to the query embedding.
     *
     * <p>The query:
     * - Filters by document_id (only search within this document)
     * - Orders by cosine distance ascending (most similar = smallest distance)
     * - Limits to top-K results
     * - Returns chunk_id and distance score
     *
     * <p>cosine distance = 1 - cosine_similarity
     * So distance 0.0 = identical, distance 2.0 = opposite
     * We filter by distance < threshold to exclude irrelevant chunks.
     */
    @Query(value = """
            SELECT de.chunk_id, (de.embedding <=> CAST(:queryEmbedding AS vector)) AS distance
            FROM document_embeddings de
            WHERE de.document_id = :documentId
            AND (de.embedding <=> CAST(:queryEmbedding AS vector)) < :distanceThreshold
            ORDER BY de.embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<Object[]> findSimilarChunks(
            @Param("documentId") UUID documentId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("topK") int topK,
            @Param("distanceThreshold") double distanceThreshold
    );
}