package com.genailab.rag.service;

import com.genailab.ai.model.*;
import com.genailab.ai.registry.AiProviderRegistry;
import com.genailab.metrics.AiMetrics;
import com.genailab.document.domain.Document;
import com.genailab.document.domain.DocumentStatus;
import com.genailab.document.service.DocumentService;
import com.genailab.rag.dto.DocumentQueryRequest;
import com.genailab.rag.dto.DocumentQueryResponse;
import com.genailab.rag.retrieval.VectorSearchService;
import com.genailab.rag.retrieval.VectorSearchService.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates the full RAG pipeline for document querying.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Validate document is READY and owned by user</li>
 *   <li>Retrieve relevant chunks via vector similarity search</li>
 *   <li>Assemble context from retrieved chunks</li>
 *   <li>Build prompt with context + user question</li>
 *   <li>Call AI model for answer generation</li>
 *   <li>Return answer with source citations</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private final VectorSearchService vectorSearchService;
    private final DocumentService documentService;
    private final AiProviderRegistry aiProviderRegistry;
    private final AiMetrics aiMetrics;

    @Value("${genailab.ai.default-model:gpt-4o-mini}")
    private String defaultModelId;

    /**
     * Answer a question about a document using RAG.
     */
    public DocumentQueryResponse query(
            UUID documentId,
            DocumentQueryRequest request,
            UUID userId) {

        // Validate document exists and belongs to user
        Document document = documentService.findOwnedDocument(documentId, userId);

        // Document must be fully processed before querying
        if (document.getStatus() != DocumentStatus.READY) {
            throw new IllegalStateException(
                    "Document is not ready for querying. Current status: "
                            + document.getStatus() +
                            ". Please wait for processing to complete.");
        }

        String modelId = request.getModelId() != null
                ? request.getModelId()
                : defaultModelId;

        log.info("RAG query for document: {}, model: {}, question length: {}",
                documentId, modelId, request.getQuestion().length());

        // Step 1: Retrieve relevant chunks
        List<RetrievedChunk> retrievedChunks = vectorSearchService.search(
                documentId, request.getQuestion(), modelId);

        if (retrievedChunks.isEmpty()) {
            log.warn("No relevant chunks found for query in document: {}", documentId);
            return DocumentQueryResponse.builder()
                    .answer("I could not find relevant information in this document " +
                            "to answer your question. Please try rephrasing your question.")
                    .modelUsed(modelId)
                    .chunksUsed(0)
                    .sources(List.of())
                    .build();
        }

        // Step 2: Assemble context from retrieved chunks
        String context = assembleContext(retrievedChunks);

        // Step 3: Build the RAG prompt
        List<AiMessage> messages = buildRagPrompt(context, request.getQuestion());

        // Step 4: Call AI model
        AiChatRequest aiRequest = AiChatRequest.builder()
                .modelId(modelId)
                .messages(messages)
                .build();

        AiChatClient chatClient = aiProviderRegistry.getChatClientForModel(modelId);
        AiChatResponse aiResponse = chatClient.chat(aiRequest);

        log.info("RAG query complete. Chunks used: {}, Tokens: {}",
                retrievedChunks.size(),
                aiResponse.getTokenUsage() != null
                        ? aiResponse.getTokenUsage().getTotalTokens() : 0);

        // Step 5: Record metrics and build response
        aiMetrics.recordRagQuery(modelId, retrievedChunks.size());
        return buildResponse(aiResponse, retrievedChunks, modelId);
    }

    // =========================================================
    // Private helpers
    // =========================================================

    /**
     * Assemble retrieved chunks into a context string for the prompt.
     *
     * <p>Each chunk is labelled with its index and similarity score
     * so the AI can reference specific passages in its answer.
     */
    private String assembleContext(List<RetrievedChunk> chunks) {
        return chunks.stream()
                .map(RetrievedChunk::toContextString)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /**
     * Build the RAG prompt messages.
     *
     * <p>The system prompt instructs the AI to:
     * - Answer ONLY from the provided context
     * - Cite sources by chunk index
     * - Admit when it doesn't know rather than hallucinating
     *
     * <p>This is the most important part of RAG quality —
     * a well-crafted system prompt prevents the model from
     * ignoring the context and using its training data instead.
     */
    private List<AiMessage> buildRagPrompt(String context, String question) {
        String systemPrompt = """
                You are a precise document analysis assistant.
                Answer the user's question based EXCLUSIVELY on the provided document context below.

                Rules:
                - Only use information from the provided context
                - If the context does not contain enough information to answer, say so clearly
                - Do not use your training knowledge to fill gaps
                - Reference the source chunk numbers when citing specific information
                - Be concise and accurate

                Document Context:
                """ + context;

        return List.of(
                AiMessage.system(systemPrompt),
                AiMessage.user(question)
        );
    }

    private DocumentQueryResponse buildResponse(
            AiChatResponse aiResponse,
            List<RetrievedChunk> retrievedChunks,
            String modelId) {

        // Build source citations — show first 200 chars of each chunk as excerpt
        List<DocumentQueryResponse.SourceChunk> sources = retrievedChunks.stream()
                .map(rc -> DocumentQueryResponse.SourceChunk.builder()
                        .chunkIndex(rc.chunk().getChunkIndex())
                        .similarity(Math.round(rc.similarity() * 100.0) / 100.0)
                        .excerpt(rc.chunk().getContent().length() > 200
                                ? rc.chunk().getContent().substring(0, 200) + "..."
                                : rc.chunk().getContent())
                        .build())
                .toList();

        DocumentQueryResponse.TokenUsage tokenUsage = null;
        if (aiResponse.getTokenUsage() != null) {
            tokenUsage = DocumentQueryResponse.TokenUsage.builder()
                    .promptTokens(aiResponse.getTokenUsage().getPromptTokens())
                    .completionTokens(aiResponse.getTokenUsage().getCompletionTokens())
                    .totalTokens(aiResponse.getTokenUsage().getTotalTokens())
                    .build();
        }

        return DocumentQueryResponse.builder()
                .answer(aiResponse.getContent())
                .modelUsed(modelId)
                .chunksUsed(retrievedChunks.size())
                .tokenUsage(tokenUsage)
                .sources(sources)
                .build();
    }
}