package com.agenttimeline.service;

import com.agenttimeline.model.MessageChunkEmbedding;
import com.agenttimeline.repository.MessageChunkEmbeddingRepository;
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
            if (chunks.isEmpty()) {
                log.warn("No chunks generated for message {}", messageId);
                return 0;
            }

            // Generate embeddings for all chunks
            List<double[]> embeddings = embeddingService.generateEmbeddings(chunks);
            if (embeddings.size() != chunks.size()) {
                log.error("Mismatch between chunks ({}) and embeddings ({}) for message {}",
                    chunks.size(), embeddings.size(), messageId);
                return 0;
            }

            // Create and save chunk embeddings
            List<MessageChunkEmbedding> chunkEmbeddings = IntStream.range(0, chunks.size())
                .mapToObj(i -> MessageChunkEmbedding.builder()
                    .messageId(messageId)
                    .chunkText(chunks.get(i))
                    .embeddingVector(embeddings.get(i))
                    .chunkIndex(i)
                    .sessionId(sessionId)
                    .build())
                .toList();

            List<MessageChunkEmbedding> savedChunks = repository.saveAll(chunkEmbeddings);

            log.info("Successfully stored {} chunks for message {} (session: {})",
                savedChunks.size(), messageId, sessionId);
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
                    log.debug("Chunk {} similarity: {}", chunk.getId(), similarity);
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
     * Helper record for similarity scoring
     */
    private record SimilarityScore(MessageChunkEmbedding chunk, double similarity) {}
}
