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
public class MessageResponse {

    private UUID id;
    private UUID conversationId;
    private String role;
    private String content;
    private Integer tokenCountPrompt;
    private Integer tokenCountCompletion;
    private String modelUsed;
    private boolean isError;
    private Instant createdAt;
}