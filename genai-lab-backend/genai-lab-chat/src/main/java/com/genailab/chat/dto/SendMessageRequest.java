package com.genailab.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotBlank(message = "Message content is required")
    @Size(max = 32000, message = "Message cannot exceed 32000 characters")
    private String content;

    /**
     * Optional model override — allows switching models mid-conversation.
     * If null, the conversation's default modelId is used.
     */
    private String modelId;

    /**
     * Whether to stream the response via SSE.
     * Defaults to true — streaming is the preferred mode for chat UI.
     */
    private boolean stream = true;
}