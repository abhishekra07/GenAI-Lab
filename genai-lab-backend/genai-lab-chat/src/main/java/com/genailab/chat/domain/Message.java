package com.genailab.chat.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A single message turn in a conversation.
 *
 * <p>Messages are immutable once created — we never update them.
 * That's why there is no updated_at column and no @PreUpdate hook.
 * Regeneration creates a new message rather than updating the old one.
 */
@Entity
@Table(name = "messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    /**
     * Role of this message.
     * Stored as a plain string matching the CHECK constraint in the DB:
     * "user", "assistant", or "system".
     */
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "token_count_prompt")
    private Integer tokenCountPrompt;

    @Column(name = "token_count_completion")
    private Integer tokenCountCompletion;

    @Column(name = "model_used", length = 100)
    private String modelUsed;

    @Column(name = "is_error", nullable = false)
    @Builder.Default
    private boolean isError = false;

    @Column(name = "parent_message_id")
    private UUID parentMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}