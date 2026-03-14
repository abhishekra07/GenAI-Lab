package com.genailab.ai.model.openai;

import com.genailab.ai.model.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenAI implementation of {@link AiChatClient}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Convert our {@link AiChatRequest} → Spring AI {@link Prompt}</li>
 *   <li>Call Spring AI's {@link ChatModel}</li>
 *   <li>Convert Spring AI's response → our {@link AiChatResponse} or {@link AiStreamChunk}</li>
 *   <li>Record metrics for every AI call</li>
 * </ul>
 *
 * <p>Spring AI's ChatModel is already configured by auto-configuration
 * using the {@code spring.ai.openai.*} properties in application.yml.
 * We just inject it and use it.
 */
@Component
@Slf4j
public class OpenAiChatClientImpl implements AiChatClient {

    private static final String PROVIDER = "openai";

    private final ChatModel chatModel;
    private final MeterRegistry meterRegistry;

    public OpenAiChatClientImpl(ChatModel chatModel, MeterRegistry meterRegistry) {
        this.chatModel = chatModel;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        log.debug("Sending chat request to OpenAI. Model: {}, Messages: {}",
                request.getModelId(), request.getMessages().size());

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            Prompt prompt = buildPrompt(request);
            ChatResponse response = chatModel.call(prompt);

            String content = response.getResult().getOutput().getText();

            // Extract token usage from the response metadata
            AiChatResponse.TokenUsage tokenUsage = extractTokenUsage(response);

            recordMetrics(sample, request.getModelId(), "success");
            recordTokenUsage(request.getModelId(), tokenUsage);

            log.debug("OpenAI response received. Tokens used: {}", tokenUsage.getTotalTokens());

            return AiChatResponse.builder()
                    .content(content)
                    .modelUsed(request.getModelId())
                    .tokenUsage(tokenUsage)
                    .build();

        } catch (Exception e) {
            recordMetrics(sample, request.getModelId(), "error");
            log.error("OpenAI chat call failed for model {}: {}", request.getModelId(), e.getMessage());
            throw new RuntimeException("AI chat call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<AiStreamChunk> streamChat(AiChatRequest request) {
        log.debug("Starting streaming chat request to OpenAI. Model: {}", request.getModelId());

        Prompt prompt = buildPrompt(request);

        // Accumulators for building token usage from the stream
        // We track the full content to estimate tokens on completion
        AtomicReference<String> fullContent = new AtomicReference<>("");
        AtomicInteger chunkCount = new AtomicInteger(0);

        return chatModel.stream(prompt)
                .map(response -> {
                    // Each response in the stream contains a partial text fragment
                    String rawText = response.getResult().getOutput().getText();
                    final String chunkText = rawText != null ? rawText : "";

                    fullContent.updateAndGet(existing -> existing + chunkText);
                    chunkCount.incrementAndGet();

                    return AiStreamChunk.of(chunkText);
                })
                .concatWith(Flux.defer(() -> {
                    // Emit the final "done" chunk after the stream completes.
                    // Token usage is approximate for streaming — OpenAI doesn't
                    // always return exact counts in streaming mode.
                    log.debug("Stream completed. Total chunks: {}", chunkCount.get());
                    return Flux.just(AiStreamChunk.done(null));
                }))
                .doOnError(e -> {
                    log.error("OpenAI stream failed for model {}: {}", request.getModelId(), e.getMessage());
                })
                .doOnComplete(() -> {
                    meterRegistry.counter("genailab.ai.stream.completed",
                            "provider", PROVIDER,
                            "model", request.getModelId()).increment();
                });
    }

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    // =========================================================
    // Private helpers
    // =========================================================

    /**
     * Convert our AiChatRequest into a Spring AI Prompt.
     *
     * <p>This is where we translate between our abstraction and
     * Spring AI's types. All the mapping is contained here.
     */
    private Prompt buildPrompt(AiChatRequest request) {
        List<Message> messages = request.getMessages().stream()
                .map(this::toSpringAiMessage)
                .toList();

        // Build OpenAI-specific options if parameters are provided
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

    /**
     * Convert our AiMessage to the appropriate Spring AI Message subtype.
     *
     * <p>Spring AI uses a class hierarchy for messages:
     * UserMessage, AssistantMessage, SystemMessage.
     * We map from our AiRole enum to the correct subtype.
     */
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

    private void recordMetrics(Timer.Sample sample, String modelId, String status) {
        sample.stop(Timer.builder("genailab.ai.request.latency")
                .tag("provider", PROVIDER)
                .tag("model", modelId != null ? modelId : "unknown")
                .tag("status", status)
                .register(meterRegistry));

        meterRegistry.counter("genailab.ai.requests.total",
                "provider", PROVIDER,
                "model", modelId != null ? modelId : "unknown",
                "status", status).increment();
    }

    private void recordTokenUsage(String modelId, AiChatResponse.TokenUsage usage) {
        if (usage == null) return;
        String model = modelId != null ? modelId : "unknown";

        meterRegistry.counter("genailab.ai.tokens.used",
                        "provider", PROVIDER, "model", model, "type", "prompt")
                .increment(usage.getPromptTokens());

        meterRegistry.counter("genailab.ai.tokens.used",
                        "provider", PROVIDER, "model", model, "type", "completion")
                .increment(usage.getCompletionTokens());
    }
}