package com.genailab.ai.model.openai;

import com.genailab.ai.model.*;
import com.genailab.metrics.AiMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OpenAI implementation of {@link AiChatClient}.
 *
 * <p>This is the ONLY class in the entire codebase that imports
 * Spring AI or OpenAI-specific types. Everything else uses our
 * abstraction interfaces.
 */
@Slf4j
public class OpenAiChatClientImpl implements AiChatClient {

    private static final String PROVIDER = "openai";

    private final ChatModel chatModel;
    private final AiMetrics aiMetrics;

    public OpenAiChatClientImpl(ChatModel chatModel, AiMetrics aiMetrics) {
        this.chatModel = chatModel;
        this.aiMetrics = aiMetrics;
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        log.debug("Sending chat request to OpenAI. Model: {}, Messages: {}",
                request.getModelId(), request.getMessages().size());

        long startTime = System.currentTimeMillis();

        try {
            Prompt prompt = buildPrompt(request);
            ChatResponse response = chatModel.call(prompt);

            String content = response.getResult().getOutput().getText();
            AiChatResponse.TokenUsage tokenUsage = extractTokenUsage(response);
            long durationMs = System.currentTimeMillis() - startTime;

            aiMetrics.recordRequest(PROVIDER, request.getModelId(), "success");
            aiMetrics.recordLatency(PROVIDER, request.getModelId(), "success", durationMs);
            if (tokenUsage != null) {
                aiMetrics.recordTokenUsage(PROVIDER, request.getModelId(),
                        tokenUsage.getPromptTokens(), tokenUsage.getCompletionTokens());
            }

            log.debug("OpenAI response received. Tokens used: {}",
                    tokenUsage != null ? tokenUsage.getTotalTokens() : 0);

            return AiChatResponse.builder()
                    .content(content)
                    .modelUsed(request.getModelId())
                    .tokenUsage(tokenUsage)
                    .build();

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            aiMetrics.recordRequest(PROVIDER, request.getModelId(), "error");
            aiMetrics.recordLatency(PROVIDER, request.getModelId(), "error", durationMs);
            log.error("OpenAI chat call failed for model {}: {}", request.getModelId(), e.getMessage());
            throw new RuntimeException("AI chat call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<AiStreamChunk> streamChat(AiChatRequest request) {
        log.debug("Starting streaming chat request to OpenAI. Model: {}", request.getModelId());

        Prompt prompt = buildPrompt(request);
        AtomicInteger chunkCount = new AtomicInteger(0);

        return chatModel.stream(prompt)
                .map(response -> {
                    String rawText = response.getResult().getOutput().getText();
                    final String chunkText = rawText != null ? rawText : "";
                    chunkCount.incrementAndGet();
                    return AiStreamChunk.of(chunkText);
                })
                .concatWith(Flux.defer(() -> {
                    log.debug("Stream completed. Total chunks: {}", chunkCount.get());
                    return Flux.just(AiStreamChunk.done(null));
                }))
                .doOnError(e ->
                        log.error("OpenAI stream failed for model {}: {}",
                                request.getModelId(), e.getMessage())
                )
                .doOnComplete(() ->
                        aiMetrics.recordStreamCompleted(PROVIDER, request.getModelId())
                );
    }

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    // =========================================================
    // Private helpers
    // =========================================================

    private Prompt buildPrompt(AiChatRequest request) {
        List<Message> messages = request.getMessages().stream()
                .map(this::toSpringAiMessage)
                .toList();

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder();

        if (request.getModelId() != null) {
            optionsBuilder.model(request.getModelId());
        }
        if (request.getTemperature() != null) {
            optionsBuilder.temperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            optionsBuilder.maxTokens(request.getMaxTokens());
        }

        return new Prompt(messages, optionsBuilder.build());
    }

    private Message toSpringAiMessage(AiMessage message) {
        return switch (message.getRole()) {
            case USER -> new UserMessage(message.getContent());
            case ASSISTANT -> new AssistantMessage(message.getContent());
            case SYSTEM -> new SystemMessage(message.getContent());
        };
    }

    private AiChatResponse.TokenUsage extractTokenUsage(ChatResponse response) {
        try {
            var usage = response.getMetadata().getUsage();
            if (usage != null) {
                int prompt = usage.getPromptTokens() != null
                        ? usage.getPromptTokens().intValue() : 0;
                int completion = usage.getCompletionTokens() != null
                        ? usage.getCompletionTokens().intValue() : 0;
                return AiChatResponse.TokenUsage.builder()
                        .promptTokens(prompt)
                        .completionTokens(completion)
                        .totalTokens(prompt + completion)
                        .build();
            }
        } catch (Exception e) {
            log.warn("Could not extract token usage from response: {}", e.getMessage());
        }
        return AiChatResponse.TokenUsage.builder().build();
    }
}