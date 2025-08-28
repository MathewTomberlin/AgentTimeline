package com.agenttimeline.service;

import com.agenttimeline.model.OllamaEmbeddingRequest;
import com.agenttimeline.model.OllamaEmbeddingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final WebClient webClient;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.embedding-model:sam860/dolphin3-qwen2.5:3b}")
    private String embeddingModel;

    @Value("${ollama.timeout:30000}")
    private int timeoutMs;

    /**
     * Generate embedding vector for a single text chunk
     * @param text The text to embed
     * @return Array of doubles representing the embedding vector
     */
    public double[] generateEmbedding(String text) {
        log.debug("Generating embedding for text: '{}'", text != null ? text.substring(0, Math.min(50, text.length())) : "null");

        if (text == null || text.trim().isEmpty()) {
            log.warn("Attempted to generate embedding for empty text");
            return new double[0];
        }

        OllamaEmbeddingRequest request = new OllamaEmbeddingRequest(embeddingModel, text, false);
        log.debug("Using embedding model: {}", embeddingModel);

        try {
            log.debug("Making request to Ollama API: {}", ollamaBaseUrl + "/api/embeddings");

            OllamaEmbeddingResponse response = webClient.post()
                .uri(ollamaBaseUrl + "/api/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OllamaEmbeddingResponse.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .block();

            log.debug("Received response from Ollama API");

            if (response != null && response.getEmbedding() != null) {
                List<Double> embeddingList = response.getEmbedding();
                double[] embeddingArray = new double[embeddingList.size()];
                for (int i = 0; i < embeddingList.size(); i++) {
                    embeddingArray[i] = embeddingList.get(i);
                }
                log.debug("Generated embedding with {} dimensions for text: {} chars",
                    embeddingArray.length, text.length());
                return embeddingArray;
            } else {
                log.error("Failed to generate embedding - response or embedding list is null");
                return new double[0];
            }
        } catch (Exception e) {
            log.error("Error generating embedding for text: {}", e.getMessage(), e);
            return new double[0];
        }
    }

    /**
     * Generate embeddings for multiple text chunks
     * @param texts List of text chunks to embed
     * @return List of embedding vectors
     */
    public List<double[]> generateEmbeddings(List<String> texts) {
        log.info("Generating embeddings for {} text chunks", texts.size());

        return texts.stream()
            .map(this::generateEmbedding)
            .toList();
    }

    /**
     * Generate embedding for a query text (used for similarity search)
     * @param query The query text
     * @return Embedding vector for the query
     */
    public double[] generateQueryEmbedding(String query) {
        log.debug("Generating query embedding for: '{}'", query);
        double[] embedding = generateEmbedding(query);
        log.debug("Query embedding result: {} dimensions", embedding.length);
        return embedding;
    }

    /**
     * Check if the embedding service is available
     * @return true if service is available
     */
    public boolean isAvailable() {
        try {
            webClient.get()
                .uri(ollamaBaseUrl + "/api/tags")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(5000))
                .block();
            return true;
        } catch (Exception e) {
            log.warn("Embedding service not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get the embedding model being used
     * @return The embedding model name
     */
    public String getEmbeddingModel() {
        return embeddingModel;
    }

    /**
     * Get the expected dimension of embedding vectors
     * This is a rough estimate - actual dimension depends on the model
     * @return Expected embedding dimension
     */
    public int getEmbeddingDimension() {
        // nomic-embed-text has 768 dimensions
        return 768;
    }

    /**
     * Calculate cosine similarity between two vectors
     * @param a First vector
     * @param b Second vector
     * @return Cosine similarity score between 0.0 and 1.0
     */
    public double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
