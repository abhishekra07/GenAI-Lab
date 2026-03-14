package com.genailab.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateConversationRequest {

    @Size(max = 500, message = "Title cannot exceed 500 characters")
    private String title;                    // optional — defaults to "New Conversation"

    @NotBlank(message = "Model ID is required")
    private String modelId;

    @Size(max = 5000, message = "System prompt cannot exceed 5000 characters")
    private String systemPrompt;
}