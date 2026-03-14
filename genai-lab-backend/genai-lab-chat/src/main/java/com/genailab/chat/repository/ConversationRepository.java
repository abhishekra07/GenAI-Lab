package com.genailab.chat.repository;

import com.genailab.chat.domain.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /**
     * Get all conversations for a user, ordered by most recent message first.
     * Paginated — the sidebar doesn't need all conversations at once.
     */
    Page<Conversation> findByUserIdOrderByLastMessageAtDesc(UUID userId, Pageable pageable);

    /**
     * Find a specific conversation owned by a specific user.
     * The userId check enforces ownership — users cannot access each other's conversations.
     */
    Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Update last_message_at when a new message is added.
     * We use a targeted JPQL update rather than loading and saving the full entity —
     * more efficient for a frequently-called operation.
     */
    @Modifying
    @Query("UPDATE Conversation c SET c.lastMessageAt = :timestamp WHERE c.id = :id")
    void updateLastMessageAt(@Param("id") UUID id, @Param("timestamp") Instant timestamp);

    /**
     * Delete a conversation only if it belongs to the requesting user.
     * Returns the number of deleted rows — 0 means the conversation
     * either didn't exist or belonged to a different user.
     */
    long deleteByIdAndUserId(UUID id, UUID userId);
}