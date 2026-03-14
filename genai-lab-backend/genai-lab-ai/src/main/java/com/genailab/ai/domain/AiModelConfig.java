package com.genailab.ai.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Maps to the ai_model_configs table created in V7__create_ai_model_configs.sql.
 *
 * <p>This is the source of truth for:
 * <ul>
 *   <li>Which models are available to users</li>
 *   <li>Which provider handles each model</li>
 *   <li>Model metadata shown in the UI (display name, capabilities)</li>
 * </ul>
 *
 * <p>The provider field is the routing key used by AiProviderRegistry
 * to select the correct AiChatClient implementation at request time.
 */
@Entity
@Table(name = "ai_model_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Unique model identifier — what the frontend sends in requests.
     * Examples: "gpt-4o-mini", "gpt-4o", "mock"
     */
    @Column(name = "model_key", nullable = false, unique = true, length = 100)
    private String modelKey;

    /**
     * Human-readable name shown in the UI dropdown.
     * Examples: "GPT-4o Mini", "GPT-4o", "Mock AI (Dev)"
     */
    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    /**
     * Provider that handles this model.
     * Used by AiProviderRegistry to route to the correct AiChatClient.
     * Examples: "openai", "anthropic", "ollama", "mock"
     */
    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    /**
     * Maximum tokens this model can process (prompt + completion combined).
     * Used to enforce limits during context assembly in the RAG pipeline.
     */
    @Column(name = "context_window", nullable = false)
    private int contextWindow;

    /**
     * Flexible JSONB field for model-specific properties.
     * Examples: pricing, supported features, description.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> capabilities = new java.util.HashMap<>();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}