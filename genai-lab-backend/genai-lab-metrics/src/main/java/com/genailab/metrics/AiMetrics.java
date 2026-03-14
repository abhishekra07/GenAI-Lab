package com.genailab.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Centralized AI metrics instrumentation.
 *
 * <p>All custom AI-related metrics are recorded here.
 * Individual AI clients (OpenAiChatClientImpl, MockAiChatClientImpl)
 * inject this bean and call its methods — they never touch MeterRegistry directly.
 *
 * <p>Available metrics:
 * <ul>
 *   <li>genailab.ai.requests.total — counter by provider, model, status</li>
 *   <li>genailab.ai.request.latency — timer by provider, model, status</li>
 *   <li>genailab.ai.tokens.used — counter by provider, model, type (prompt/completion)</li>
 *   <li>genailab.ai.stream.completed — counter by provider, model</li>
 *   <li>genailab.ai.embeddings.generated — counter by provider, model</li>
 * </ul>
 *
 * <p>Prometheus query examples:
 * <pre>
 *   genailab_ai_requests_total{status="success"}
 *   rate(genailab_ai_tokens_used_total[5m])
 *   histogram_quantile(0.95, rate(genailab_ai_request_latency_seconds_bucket[5m]))
 * </pre>
 */
@Component
@Slf4j
public class AiMetrics {

    private final MeterRegistry registry;

    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // =========================================================
    // Chat metrics
    // =========================================================

    public void recordRequest(String provider, String model, String status) {
        Counter.builder("genailab.ai.requests.total")
                .description("Total AI chat requests")
                .tag("provider", provider)
                .tag("model", nullSafe(model))
                .tag("status", status)
                .register(registry)
                .increment();
    }

    public void recordLatency(String provider, String model, String status, long durationMs) {
        Timer.builder("genailab.ai.request.latency")
                .description("AI chat request latency")
                .tag("provider", provider)
                .tag("model", nullSafe(model))
                .tag("status", status)
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordTokenUsage(String provider, String model, int promptTokens, int completionTokens) {
        Counter.builder("genailab.ai.tokens.used")
                .description("AI tokens used")
                .tag("provider", provider)
                .tag("model", nullSafe(model))
                .tag("type", "prompt")
                .register(registry)
                .increment(promptTokens);

        Counter.builder("genailab.ai.tokens.used")
                .description("AI tokens used")
                .tag("provider", provider)
                .tag("model", nullSafe(model))
                .tag("type", "completion")
                .register(registry)
                .increment(completionTokens);
    }

    public void recordStreamCompleted(String provider, String model) {
        Counter.builder("genailab.ai.stream.completed")
                .description("AI streaming responses completed")
                .tag("provider", provider)
                .tag("model", nullSafe(model))
                .register(registry)
                .increment();
    }

    // =========================================================
    // Embedding metrics
    // =========================================================

    public void recordEmbeddingsGenerated(String provider, String model, int count) {
        Counter.builder("genailab.ai.embeddings.generated")
                .description("Total embeddings generated")
                .tag("provider", provider)
                .tag("model", nullSafe(model))
                .register(registry)
                .increment(count);
    }

    // =========================================================
    // Document metrics
    // =========================================================

    public void recordDocumentProcessed(String fileType, String status) {
        Counter.builder("genailab.document.processed.total")
                .description("Documents processed")
                .tag("file_type", nullSafe(fileType))
                .tag("status", status)
                .register(registry)
                .increment();
    }

    public void recordDocumentChunks(String fileType, int chunkCount) {
        Counter.builder("genailab.document.chunks.total")
                .description("Document chunks created")
                .tag("file_type", nullSafe(fileType))
                .register(registry)
                .increment(chunkCount);
    }

    // =========================================================
    // RAG metrics
    // =========================================================

    public void recordRagQuery(String model, int chunksRetrieved) {
        Counter.builder("genailab.rag.queries.total")
                .description("Total RAG queries executed")
                .tag("model", nullSafe(model))
                .register(registry)
                .increment();

        if (chunksRetrieved == 0) {
            Counter.builder("genailab.rag.queries.no_results")
                    .description("RAG queries that returned no relevant chunks")
                    .tag("model", nullSafe(model))
                    .register(registry)
                    .increment();
        }
    }

    // =========================================================
    // Private helpers
    // =========================================================

    private String nullSafe(String value) {
        return value != null ? value : "unknown";
    }
}