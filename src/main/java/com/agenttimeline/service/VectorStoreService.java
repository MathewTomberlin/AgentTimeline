package com.agenttimeline.service;

import com.agenttimeline.model.MessageChunkEmbedding;
import com.agenttimeline.repository.MessageChunkEmbeddingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final MessageChunkEmbeddingRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Process a message by chunking it, generating embeddings, and storing everything
     * @param messageId The ID of the original message
     * @param messageText The text content of the message
     * @param sessionId The session ID
     * @return Number of chunks created and stored
     */
    @Transactional
    public int processAndStoreMessage(String messageId, String messageText, String sessionId) {
        log.info("Processing message {} for vector storage (session: {})", messageId, sessionId);

        try {
            // Chunk the message
            List<String> chunks = chunkingService.chunkTextWithOverlap(messageText);
            log.debug("Generated {} chunks for message {}", chunks.size(), messageId);

            if (chunks.isEmpty()) {
                log.warn("No chunks generated for message {}", messageId);
                return 0;
            }

            // Log first few chunks for debugging
            if (!chunks.isEmpty()) {
                log.debug("First chunk: '{}'", chunks.get(0).substring(0, Math.min(50, chunks.get(0).length())));
            }

            // Generate embeddings for all chunks
            log.debug("Starting embedding generation for {} chunks", chunks.size());
            List<double[]> embeddings = embeddingService.generateEmbeddings(chunks);
            log.debug("Embedding generation completed, got {} embeddings", embeddings.size());

            if (embeddings.size() != chunks.size()) {
                log.error("Mismatch between chunks ({}) and embeddings ({}) for message {}",
                    chunks.size(), embeddings.size(), messageId);
                return 0;
            }

            // Check if embeddings are valid (non-empty)
            int validEmbeddings = 0;
            for (double[] embedding : embeddings) {
                if (embedding != null && embedding.length > 0) {
                    validEmbeddings++;
                }
            }
            log.debug("Valid embeddings: {}/{}", validEmbeddings, embeddings.size());

            // Create and save chunk embeddings
            List<MessageChunkEmbedding> chunkEmbeddings = IntStream.range(0, chunks.size())
                .mapToObj(i -> {
                    double[] embedding = embeddings.get(i);
                    String embeddingJson = null;

                    // Manually serialize the embedding to JSON
                    try {
                        embeddingJson = objectMapper.writeValueAsString(embedding);
                        log.debug("Chunk {} - serialized embedding to JSON, length: {}", i, embeddingJson.length());
                    } catch (Exception e) {
                        log.error("Failed to serialize embedding for chunk {}: {}", i, e.getMessage());
                    }

                    MessageChunkEmbedding chunk = MessageChunkEmbedding.builder()
                        .messageId(messageId)
                        .chunkText(chunks.get(i))
                        .chunkIndex(i)
                        .sessionId(sessionId)
                        .build();

                    // Manually set the JSON string
                    chunk.setEmbeddingVectorJson(embeddingJson);

                    log.debug("Chunk {} created - embeddingJson length: {}", i,
                        embeddingJson != null ? embeddingJson.length() : 0);

                    return chunk;
                })
                .toList();

            log.debug("About to save {} chunk embeddings", chunkEmbeddings.size());
            List<MessageChunkEmbedding> savedChunks = repository.saveAll(chunkEmbeddings);
            log.debug("Saved {} chunk embeddings successfully", savedChunks.size());

            log.info("Successfully stored {} chunks for message {} (session: {})",
                savedChunks.size(), messageId, sessionId);

            // Debug: Check first saved chunk
            if (!savedChunks.isEmpty()) {
                MessageChunkEmbedding firstChunk = savedChunks.get(0);
                log.debug("First saved chunk - ID: {}, embeddingVectorJson length: {}, embeddingVector: {}",
                    firstChunk.getId(),
                    firstChunk.getEmbeddingVectorJson() != null ? firstChunk.getEmbeddingVectorJson().length() : 0,
                    firstChunk.getEmbeddingVector() != null ? firstChunk.getEmbeddingVector().length : "null");

                // Additional debug: Test deserialization immediately after save
                try {
                    double[] testEmbedding = firstChunk.getEmbeddingVector();
                    log.debug("Immediate deserialization test - embedding length: {}, first few values: [{}, {}, {}]",
                        testEmbedding != null ? testEmbedding.length : 0,
                        testEmbedding != null && testEmbedding.length > 0 ? testEmbedding[0] : "null",
                        testEmbedding != null && testEmbedding.length > 1 ? testEmbedding[1] : "null",
                        testEmbedding != null && testEmbedding.length > 2 ? testEmbedding[2] : "null");
                } catch (Exception e) {
                    log.error("Error during immediate deserialization test: {}", e.getMessage());
                }
            }

            return savedChunks.size();

        } catch (Exception e) {
            log.error("Error processing message {} for vector storage: {}", messageId, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Find similar chunks for a query within a specific session
     * @param query The search query
     * @param sessionId The session ID to search within
     * @param limit Maximum number of results to return
     * @return List of similar message chunks with their embeddings
     */
    public List<MessageChunkEmbedding> findSimilarChunks(String query, String sessionId, int limit) {
        log.debug("Finding similar chunks for query '{}' in session {} (limit: {})", query, sessionId, limit);

        try {
            double[] queryEmbedding = embeddingService.generateQueryEmbedding(query);
            log.debug("Generated query embedding with {} dimensions", queryEmbedding.length);

            if (queryEmbedding.length == 0) {
                log.warn("Failed to generate query embedding for: {}", query);
                return List.of();
            }

            List<MessageChunkEmbedding> sessionChunks = repository.findBySessionId(sessionId);
            log.debug("Found {} chunks in session {}", sessionChunks.size(), sessionId);

            if (sessionChunks.isEmpty()) {
                log.warn("No chunks found in session {}", sessionId);
                return List.of();
            }

            // Debug: Check first chunk
            if (!sessionChunks.isEmpty()) {
                MessageChunkEmbedding firstChunk = sessionChunks.get(0);
                log.debug("First chunk - ID: {}, MessageID: {}, Text length: {}, EmbeddingVectorJson length: {}",
                    firstChunk.getId(),
                    firstChunk.getMessageId(),
                    firstChunk.getChunkText() != null ? firstChunk.getChunkText().length() : 0,
                    firstChunk.getEmbeddingVectorJson() != null ? firstChunk.getEmbeddingVectorJson().length() : 0);

                double[] embedding = firstChunk.getEmbeddingVector();
                log.debug("First chunk embedding vector: {} (length: {})",
                    embedding != null ? "present" : "null",
                    embedding != null ? embedding.length : 0);
            }

            List<SimilarityScore> similarityScores = sessionChunks.stream()
                .filter(chunk -> {
                    double[] embedding = chunk.getEmbeddingVector();
                    boolean hasEmbedding = embedding != null && embedding.length > 0;
                    if (!hasEmbedding) {
                        log.debug("Chunk {} has no embedding vector", chunk.getId());
                    }
                    return hasEmbedding;
                })
                .map(chunk -> {
                    double similarity = cosineSimilarity(queryEmbedding, chunk.getEmbeddingVector());
                    log.debug("Chunk {} similarity: {} (text: '{}')", chunk.getId(), similarity,
                        chunk.getChunkText() != null ? chunk.getChunkText().substring(0, Math.min(30, chunk.getChunkText().length())) : "null");
                    return new SimilarityScore(chunk, similarity);
                })
                .sorted((a, b) -> Double.compare(b.similarity, a.similarity))
                .limit(limit)
                .toList();

            List<MessageChunkEmbedding> similarChunks = similarityScores.stream()
                .map(score -> score.chunk)
                .toList();

            log.debug("Found {} similar chunks for query in session {}", similarChunks.size(), sessionId);
            return similarChunks;

        } catch (Exception e) {
            log.error("Error finding similar chunks: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find similar chunks across all sessions
     * @param query The search query
     * @param limit Maximum number of results to return
     * @return List of similar message chunks with their embeddings
     */
    public List<MessageChunkEmbedding> findSimilarChunksGlobal(String query, int limit) {
        log.debug("Finding similar chunks globally for query (limit: {})", limit);

        try {
            double[] queryEmbedding = embeddingService.generateQueryEmbedding(query);
            if (queryEmbedding.length == 0) {
                log.warn("Failed to generate query embedding for: {}", query);
                return List.of();
            }

            List<MessageChunkEmbedding> allChunks = repository.findAll();
            List<SimilarityScore> similarityScores = allChunks.stream()
                .filter(chunk -> chunk.getEmbeddingVector() != null && chunk.getEmbeddingVector().length > 0)
                .map(chunk -> new SimilarityScore(chunk, cosineSimilarity(queryEmbedding, chunk.getEmbeddingVector())))
                .sorted((a, b) -> Double.compare(b.similarity, a.similarity))
                .limit(limit)
                .toList();

            List<MessageChunkEmbedding> similarChunks = similarityScores.stream()
                .map(score -> score.chunk)
                .toList();

            log.debug("Found {} similar chunks globally for query", similarChunks.size());
            return similarChunks;

        } catch (Exception e) {
            log.error("Error finding similar chunks globally: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find similar chunks within a similarity threshold
     * @param query The search query
     * @param sessionId The session ID to search within
     * @param threshold Similarity threshold (0.0 to 1.0, higher values are more similar)
     * @return List of similar message chunks within the threshold
     */
    public List<MessageChunkEmbedding> findSimilarChunksWithinThreshold(
            String query, String sessionId, double threshold) {
        log.debug("Finding similar chunks within threshold {} for query in session {}", threshold, sessionId);

        try {
            double[] queryEmbedding = embeddingService.generateQueryEmbedding(query);
            if (queryEmbedding.length == 0) {
                log.warn("Failed to generate query embedding for: {}", query);
                return List.of();
            }

            List<MessageChunkEmbedding> sessionChunks = repository.findBySessionId(sessionId);
            List<MessageChunkEmbedding> similarChunks = sessionChunks.stream()
                .filter(chunk -> chunk.getEmbeddingVector() != null && chunk.getEmbeddingVector().length > 0)
                .filter(chunk -> {
                    double similarity = cosineSimilarity(queryEmbedding, chunk.getEmbeddingVector());
                    log.debug("Chunk {} similarity score: {} (threshold: {})", chunk.getId(), similarity, threshold);
                    return similarity >= threshold;
                })
                .sorted((a, b) -> Double.compare(
                    cosineSimilarity(queryEmbedding, b.getEmbeddingVector()),
                    cosineSimilarity(queryEmbedding, a.getEmbeddingVector())))
                .toList();

            log.debug("Found {} similar chunks within threshold {} in session {}",
                similarChunks.size(), threshold, sessionId);
            return similarChunks;

        } catch (Exception e) {
            log.error("Error finding similar chunks within threshold: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Get all chunks for a specific message
     * @param messageId The message ID
     * @return List of chunks for the message
     */
    public List<MessageChunkEmbedding> getChunksForMessage(String messageId) {
        return repository.findByMessageId(messageId);
    }

    /**
     * Get all chunks for a specific session
     * @param sessionId The session ID
     * @return List of chunks for the session
     */
    public List<MessageChunkEmbedding> getChunksForSession(String sessionId) {
        return repository.findBySessionId(sessionId);
    }



    /**
     * Delete all chunks for a specific message
     * @param messageId The message ID
     */
    @Transactional
    public void deleteChunksForMessage(String messageId) {
        long deletedCount = repository.countByMessageId(messageId);
        repository.deleteByMessageId(messageId);
        log.info("Deleted {} chunks for message {}", deletedCount, messageId);
    }

    /**
     * Delete all chunks for a specific session
     * @param sessionId The session ID
     */
    @Transactional
    public void deleteChunksForSession(String sessionId) {
        long deletedCount = repository.countBySessionId(sessionId);
        repository.deleteBySessionId(sessionId);
        log.info("Deleted {} chunks for session {}", deletedCount, sessionId);
    }

    /**
     * Get statistics about stored chunks
     * @return Statistics about the vector store
     */
    public VectorStoreStatistics getStatistics() {
        long totalChunks = repository.count();
        long uniqueMessages = repository.findAll().stream()
            .map(MessageChunkEmbedding::getMessageId)
            .distinct()
            .count();
        long uniqueSessions = repository.findAll().stream()
            .map(MessageChunkEmbedding::getSessionId)
            .distinct()
            .count();

        return new VectorStoreStatistics(totalChunks, uniqueMessages, uniqueSessions);
    }

    /**
     * Statistics about the vector store
     */
    public record VectorStoreStatistics(long totalChunks, long uniqueMessages, long uniqueSessions) {}

    /**
     * Calculate cosine similarity between two vectors
     * @param a First vector
     * @param b Second vector
     * @return Cosine similarity score between 0.0 and 1.0
     */
    public double calculateCosineSimilarity(double[] a, double[] b) {
        return cosineSimilarity(a, b);
    }

    /**
     * Calculate cosine similarity between two vectors (private implementation)
     * @param a First vector
     * @param b Second vector
     * @return Cosine similarity score between 0.0 and 1.0
     */
    private double cosineSimilarity(double[] a, double[] b) {
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

    /**
     * Get the embedding service for testing purposes
     * @return The embedding service instance
     */
    public EmbeddingService getEmbeddingService() {
        return embeddingService;
    }

    /**
     * Test embedding generation directly within VectorStoreService
     * @param text The text to embed
     * @return The embedding result
     */
    public double[] testEmbeddingGeneration(String text) {
        log.info("Testing embedding generation within VectorStoreService for text: '{}'", text);
        try {
            double[] embedding = embeddingService.generateEmbedding(text);
            log.info("Embedding generation result: {} dimensions", embedding.length);
            return embedding;
        } catch (Exception e) {
            log.error("Error in embedding generation test: {}", e.getMessage(), e);
            return new double[0];
        }
    }



    /**
     * Helper record for similarity scoring
     */
    private record SimilarityScore(MessageChunkEmbedding chunk, double similarity) {}
}
