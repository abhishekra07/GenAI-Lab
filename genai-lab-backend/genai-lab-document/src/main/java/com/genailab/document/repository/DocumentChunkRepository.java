package com.genailab.document.repository;

import com.genailab.document.domain.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    /**
     * Load all chunks for a document in order.
     * Used by the RAG embedding pipeline to generate vectors for each chunk.
     */
    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

    /**
     * Count chunks for a document — used after processing to
     * confirm all expected chunks were saved.
     */
    long countByDocumentId(UUID documentId);

    /**
     * Delete all chunks for a document — used when reprocessing
     * a document or when the document itself is deleted.
     */
    void deleteByDocumentId(UUID documentId);
}