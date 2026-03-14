package com.genailab.ai.repository;

import com.genailab.ai.domain.AiModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiModelConfigRepository extends JpaRepository<AiModelConfig, UUID> {

    Optional<AiModelConfig> findByModelKey(String modelKey);

    List<AiModelConfig> findByIsActiveTrueOrderBySortOrderAsc();

    Optional<AiModelConfig> findByIsDefaultTrueAndIsActiveTrue();
}