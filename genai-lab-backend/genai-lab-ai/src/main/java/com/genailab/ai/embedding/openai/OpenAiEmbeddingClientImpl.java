package com.genailab.ai.embedding.openai;

import com.genailab.ai.embedding.EmbeddingClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OpenAI implementation of {@link EmbeddingClient}.
 *
 * <p>Wraps Spring AI's {@link EmbeddingModel} for the OpenAI
 * text-embedding-3-small model. Produces 1536-dimensional vectors
 * that are stored in the document_embeddings table.
 *
 */
@Component
@Slf4j
public class OpenAiEmbeddingClientImpl implements EmbeddingClient {

    private static final int DIMENSIONS = 1536;

    private final EmbeddingModel embeddingModel;
    private final String modelName;

    public OpenAiEmbeddingClientImpl(EmbeddingModel embeddingModel,
                @Value("${spring.ai.openai.embedding.options.model:text-embedding-3-small}") String modelName) {
        this.embeddingModel = embeddingModel;
        this.modelName = modelName;
    }

    @Override
    public List<Float> embed(String text) {
        log.debug("Generating embedding for text of length: {}", text.length());

        EmbeddingRequest request = new EmbeddingRequest(
                List.of(text),
                OpenAiEmbeddingOptions.builder()
                        .model(modelName)
                        .build()
        );

        var response = embeddingModel.call(request);
        float[] embeddingArray = response.getResult().getOutput();

        return toFloatList(embeddingArray);
    }

    @Override
    public List<List<Float>> embedAll(List<String> texts) {
        log.debug("Generating embeddings for {} texts", texts.size());

        EmbeddingRequest request = new EmbeddingRequest(
                texts,
                OpenAiEmbeddingOptions.builder()
                        .model(modelName)
                        .build()
        );

        var response = embeddingModel.call(request);

        return response.getResults().stream()
                .map(result -> toFloatList(result.getOutput()))
                .toList();
    }

    @Override
    public int getDimensions() {
        return DIMENSIONS;
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    private List<Float> toFloatList(float[] array) {
        List<Float> result = new java.util.ArrayList<>(array.length);
        for (float f : array) {
            result.add(f);
        }
        return result;
    }
}