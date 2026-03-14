package com.genailab.rag.repository;

import com.genailab.rag.domain.DocumentEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for DocumentEmbedding metadata operations.
 *
 * <p>Vector similarity search is NOT done here — it is handled by
 * VectorSearchService using JdbcTemplate with inline vector literals,
 * which bypasses Hibernate's type system that cannot handle pgvector.
 */
@Repository
public interface DocumentEmbeddingRepository extends JpaRepository<DocumentEmbedding, UUID> {

    boolean existsByDocumentId(UUID documentId);

    void deleteByDocumentId(UUID documentId);
}