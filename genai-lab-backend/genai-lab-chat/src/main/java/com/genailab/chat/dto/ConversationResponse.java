package com.genailab.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {

    private UUID id;
    private String title;
    private String modelId;
    private String systemPrompt;
    private boolean isPinned;
    private Instant lastMessageAt;
    private Instant createdAt;
    private Instant updatedAt;
}