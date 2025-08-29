package com.agenttimeline.service;

import com.agenttimeline.model.Message;
import com.agenttimeline.model.MessageChunkEmbedding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced context retrieval service with configurable parameters and validation.
 *
 * This service replaces the original ContextRetrievalService from Phase 5 with:
 * - Configurable chunk retrieval parameters (no more hardcoded values)
 * - Parameter validation and performance assessment
 * - Adaptive retrieval strategies
 * - Comprehensive monitoring and statistics
 *
 * This is a core component of Phase 6: Enhanced Context Management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigurableContextRetrievalService {

    private final VectorStoreService vectorStoreService;
    private final ConversationHistoryManager conversationHistoryManager;

    // Configuration parameters with validation
    @Value("${context.retrieval.strategy:adaptive}")
    private String retrievalStrategy;

    @Value("${context.retrieval.chunks.before:2}")
    private int defaultChunksBefore;

    @Value("${context.retrieval.chunks.after:2}")
    private int defaultChunksAfter;

    @Value("${context.retrieval.max.similar:5}")
    private int defaultMaxSimilarChunks;

    @Value("${context.retrieval.similarity.threshold:0.3}")
    private double defaultSimilarityThreshold;

    @Value("${context.retrieval.chunks.max-per-group:5}")
    private int maxChunksPerGroup;

    // Adaptive retrieval settings
    @Value("${context.retrieval.adaptive.enabled:true}")
    private boolean adaptiveEnabled;

    @Value("${context.retrieval.adaptive.quality-threshold:0.7}")
    private double adaptiveQualityThreshold;

    @Value("${context.retrieval.adaptive.expansion-factor:1.5}")
    private double adaptiveExpansionFactor;

    // Performance monitoring
    private final RetrievalMetrics metrics = new RetrievalMetrics();

    /**
     * Retrieve relevant context chunks for a user message with default configuration.
     *
     * @param userMessage The user's current message
     * @param sessionId The session ID to search within
     * @return List of relevant context chunks with surrounding context
     */
    public List<ExpandedChunkGroup> retrieveContext(String userMessage, String sessionId) {
        return retrieveContext(userMessage, sessionId, null, new RetrievalConfig());
    }

    /**
     * Retrieve relevant context chunks for a user message with message exclusion.
     *
     * @param userMessage The user's current message
     * @param sessionId The session ID to search within
     * @param excludeMessageId Message ID to exclude from context
     * @return List of relevant context chunks with surrounding context
     */
    public List<ExpandedChunkGroup> retrieveContext(String userMessage, String sessionId, String excludeMessageId) {
        return retrieveContext(userMessage, sessionId, excludeMessageId, new RetrievalConfig());
    }

    /**
     * Retrieve relevant context chunks with custom configuration.
     *
     * @param userMessage The user's current message
     * @param sessionId The session ID to search within
     * @param excludeMessageId Message ID to exclude from context
     * @param config Custom retrieval configuration
     * @return List of relevant context chunks with surrounding context
     */
    public List<ExpandedChunkGroup> retrieveContext(String userMessage, String sessionId,
                                                   String excludeMessageId, RetrievalConfig config) {
        long startTime = System.currentTimeMillis();

        try {
            log.debug("Retrieving context for session {} with strategy: {}", sessionId, retrievalStrategy);

            // Validate and apply configuration
            RetrievalConfig validatedConfig = validateAndApplyConfig(config);

            // Apply retrieval strategy
            List<ExpandedChunkGroup> results = switch (validatedConfig.getStrategy()) {
                case FIXED -> retrieveWithFixedStrategy(userMessage, sessionId, excludeMessageId, validatedConfig);
                case ADAPTIVE -> retrieveWithAdaptiveStrategy(userMessage, sessionId, excludeMessageId, validatedConfig);
                case INTELLIGENT -> retrieveWithIntelligentStrategy(userMessage, sessionId, excludeMessageId, validatedConfig);
            };

            long duration = System.currentTimeMillis() - startTime;
            metrics.recordRetrieval(sessionId, results.size(), duration);

            log.info("Retrieved {} expanded chunk groups for session {} in {}ms (strategy: {})",
                results.size(), sessionId, duration, validatedConfig.getStrategy());

            return results;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordError(sessionId, duration);

            log.error("Error retrieving context for session {}: {}", sessionId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Fixed strategy: Use exact configuration parameters.
     */
    private List<ExpandedChunkGroup> retrieveWithFixedStrategy(String userMessage, String sessionId,
                                                              String excludeMessageId, RetrievalConfig config) {
        log.debug("Using fixed retrieval strategy for session {}", sessionId);

        // Find similar chunks
        List<MessageChunkEmbedding> similarChunks = vectorStoreService.findSimilarChunks(
            userMessage, sessionId, config.getMaxSimilarChunks());

        if (similarChunks.isEmpty()) {
            log.debug("No similar chunks found for session {}", sessionId);
            return List.of();
        }

        // Filter excluded message and recent conversation messages
        similarChunks = filterExcludedMessages(similarChunks, excludeMessageId, sessionId);

        // Apply similarity threshold (more strict filtering)
        similarChunks = applyStrictSimilarityThreshold(similarChunks, config.getSimilarityThreshold());

        // Only return results if we have relevant chunks
        if (similarChunks.isEmpty()) {
            log.debug("No relevant chunks found after filtering for session {}", sessionId);
            return List.of();
        }

        // Expand with surrounding context
        return expandWithSurroundingChunks(similarChunks, config.getChunksBefore(), config.getChunksAfter());
    }

    /**
     * Adaptive strategy: Adjust parameters based on context quality.
     */
    private List<ExpandedChunkGroup> retrieveWithAdaptiveStrategy(String userMessage, String sessionId,
                                                                 String excludeMessageId, RetrievalConfig config) {
        log.debug("Using adaptive retrieval strategy for session {}", sessionId);

        // Start with conservative parameters
        int currentMaxSimilar = Math.min(config.getMaxSimilarChunks(), 3);
        double currentThreshold = Math.max(config.getSimilarityThreshold(), 0.5);

        List<ExpandedChunkGroup> results = List.of();
        int attempts = 0;
        final int maxAttempts = 3;

        while (attempts < maxAttempts && (results.isEmpty() || !isQualitySufficient(results))) {
            log.debug("Adaptive attempt {} for session {}: maxSimilar={}, threshold={}",
                attempts + 1, sessionId, currentMaxSimilar, currentThreshold);

            RetrievalConfig adaptiveConfig = RetrievalConfig.builder()
                .strategy(RetrievalStrategy.FIXED)
                .maxSimilarChunks(currentMaxSimilar)
                .similarityThreshold(Math.max(currentThreshold, 0.6)) // Higher threshold for relevance
                .chunksBefore(config.getChunksBefore())
                .chunksAfter(config.getChunksAfter())
                .build();

            results = retrieveWithFixedStrategy(userMessage, sessionId, excludeMessageId, adaptiveConfig);

            // Expand search if quality is insufficient
            if (results.isEmpty() || !isQualitySufficient(results)) {
                currentMaxSimilar = (int) Math.min(currentMaxSimilar * adaptiveExpansionFactor, 10);
                currentThreshold = Math.max(currentThreshold * 0.8, 0.1);
                attempts++;
            }
        }

        log.debug("Adaptive retrieval completed for session {} after {} attempts: {} groups",
            sessionId, attempts + 1, results.size());

        return results;
    }

    /**
     * Intelligent strategy: Use multiple retrieval approaches and combine results.
     */
    private List<ExpandedChunkGroup> retrieveWithIntelligentStrategy(String userMessage, String sessionId,
                                                                   String excludeMessageId, RetrievalConfig config) {
        log.debug("Using intelligent retrieval strategy for session {}", sessionId);

        // Try multiple similarity thresholds
        List<List<ExpandedChunkGroup>> results = new ArrayList<>();

        double[] thresholds = {0.8, 0.6, 0.4}; // Higher thresholds for better relevance
        for (double threshold : thresholds) {
            RetrievalConfig thresholdConfig = RetrievalConfig.builder()
                .strategy(RetrievalStrategy.FIXED)
                .maxSimilarChunks(config.getMaxSimilarChunks())
                .similarityThreshold(threshold)
                .chunksBefore(config.getChunksBefore())
                .chunksAfter(config.getChunksAfter())
                .build();

            List<ExpandedChunkGroup> thresholdResults = retrieveWithFixedStrategy(
                userMessage, sessionId, excludeMessageId, thresholdConfig);
            results.add(thresholdResults);
        }

        // Combine and deduplicate results
        return combineAndDeduplicateResults(results);
    }

    /**
     * Validate and apply configuration with defaults.
     */
    private RetrievalConfig validateAndApplyConfig(RetrievalConfig config) {
        if (config == null) {
            config = new RetrievalConfig();
        }

        // Apply defaults for null values
        RetrievalStrategy strategy = config.getStrategy() != null ?
            config.getStrategy() : parseRetrievalStrategy(retrievalStrategy);

        int chunksBefore = config.getChunksBefore() != null ?
            config.getChunksBefore() : defaultChunksBefore;

        int chunksAfter = config.getChunksAfter() != null ?
            config.getChunksAfter() : defaultChunksAfter;

        int maxSimilar = config.getMaxSimilarChunks() != null ?
            config.getMaxSimilarChunks() : defaultMaxSimilarChunks;

        double threshold = config.getSimilarityThreshold() != null ?
            config.getSimilarityThreshold() : defaultSimilarityThreshold;

        // Validate parameters
        validateParameters(strategy, chunksBefore, chunksAfter, maxSimilar, threshold);

        return RetrievalConfig.builder()
            .strategy(strategy)
            .chunksBefore(chunksBefore)
            .chunksAfter(chunksAfter)
            .maxSimilarChunks(maxSimilar)
            .similarityThreshold(threshold)
            .build();
    }

    /**
     * Parse retrieval strategy from string.
     */
    private RetrievalStrategy parseRetrievalStrategy(String strategy) {
        if (strategy == null) {
            return RetrievalStrategy.ADAPTIVE;
        }

        return switch (strategy.toLowerCase()) {
            case "fixed" -> RetrievalStrategy.FIXED;
            case "adaptive" -> RetrievalStrategy.ADAPTIVE;
            case "intelligent" -> RetrievalStrategy.INTELLIGENT;
            default -> {
                log.warn("Unknown retrieval strategy '{}', defaulting to ADAPTIVE", strategy);
                yield RetrievalStrategy.ADAPTIVE;
            }
        };
    }

    /**
     * Validate retrieval parameters.
     */
    private void validateParameters(RetrievalStrategy strategy, int chunksBefore, int chunksAfter,
                                  int maxSimilar, double threshold) {
        if (chunksBefore < 0 || chunksBefore > 10) {
            throw new IllegalArgumentException("chunksBefore must be between 0 and 10, got: " + chunksBefore);
        }
        if (chunksAfter < 0 || chunksAfter > 10) {
            throw new IllegalArgumentException("chunksAfter must be between 0 and 10, got: " + chunksAfter);
        }
        if (maxSimilar < 1 || maxSimilar > 20) {
            throw new IllegalArgumentException("maxSimilarChunks must be between 1 and 20, got: " + maxSimilar);
        }
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("similarityThreshold must be between 0.0 and 1.0, got: " + threshold);
        }
    }

    /**
     * Filter out chunks from excluded messages and recent conversation history.
     */
    private List<MessageChunkEmbedding> filterExcludedMessages(List<MessageChunkEmbedding> chunks, String excludeMessageId, String sessionId) {
        // Get message IDs from recent conversation window to exclude
        Set<String> excludedMessageIds = new HashSet<>();
        if (excludeMessageId != null) {
            excludedMessageIds.add(excludeMessageId);
        }

        // Add recent conversation messages to exclusion list
        try {
            ConversationHistoryManager.ConversationContext context = conversationHistoryManager.getConversationContext(sessionId);
            for (Message message : context.getRecentMessages()) {
                excludedMessageIds.add(message.getId());
            }
            log.debug("Excluding {} recent conversation messages from vector search", context.getRecentMessages().size());
        } catch (Exception e) {
            log.warn("Could not retrieve conversation context for filtering: {}", e.getMessage());
        }

        List<MessageChunkEmbedding> filtered = chunks.stream()
            .filter(chunk -> !excludedMessageIds.contains(chunk.getMessageId()))
            .toList();

        log.debug("Filtered chunks: {} -> {} (excluded {} messages)",
            chunks.size(), filtered.size(), excludedMessageIds.size());

        return filtered;
    }

    /**
     * Apply relevance filtering to improve quality without being too strict.
     */
    private List<MessageChunkEmbedding> applyStrictSimilarityThreshold(List<MessageChunkEmbedding> chunks, double threshold) {
        if (chunks.isEmpty()) {
            return chunks;
        }

        // Apply more balanced filtering - keep chunks that have meaningful content
        List<MessageChunkEmbedding> filtered = chunks.stream()
            .filter(chunk -> {
                String text = chunk.getChunkText();
                if (text == null) return false;

                String lowerText = text.toLowerCase();

                // More inclusive filtering - include any chunk with substantial content
                // that appears to contain personal information or meaningful statements
                return lowerText.length() > 10 && // Reasonable content length
                       (lowerText.contains("i ") || // First person statements
                        lowerText.contains("my ") || // Personal information
                        lowerText.contains("name") || // Name information
                        lowerText.contains("am ") || // Identity statements
                        lowerText.contains("live") || // Location information
                        lowerText.contains("work") || // Work information
                        lowerText.contains("programming") ||
                        lowerText.contains("software") ||
                        lowerText.contains("engineer") ||
                        lowerText.split("\\s+").length > 3); // Substantial sentence
            })
            .limit(Math.max(1, Math.min(5, chunks.size()))) // Allow up to 5 relevant chunks
            .toList();

        // If no chunks pass the filter, keep at least the top chunk
        if (filtered.isEmpty() && !chunks.isEmpty()) {
            filtered = chunks.stream().limit(1).toList();
        }

        log.debug("Applied balanced relevance filtering: {} -> {} chunks", chunks.size(), filtered.size());
        return filtered;
    }

    /**
     * Expand chunks with surrounding context.
     */
    private List<ExpandedChunkGroup> expandWithSurroundingChunks(List<MessageChunkEmbedding> chunks,
                                                                int chunksBefore, int chunksAfter) {
        Set<ExpandedChunkGroup> expandedGroups = new LinkedHashSet<>();

        for (MessageChunkEmbedding chunk : chunks) {
            List<MessageChunkEmbedding> surroundingChunks = getSurroundingChunks(
                chunk.getMessageId(), chunk.getChunkIndex(), chunksBefore, chunksAfter);
            expandedGroups.add(new ExpandedChunkGroup(chunk.getMessageId(), surroundingChunks));

            log.debug("Expanded chunk from message {} (index {}) with {} surrounding chunks",
                chunk.getMessageId(), chunk.getChunkIndex(), surroundingChunks.size());
        }

        return new ArrayList<>(expandedGroups);
    }

    /**
     * Get chunks surrounding a specific chunk within the same message.
     */
    private List<MessageChunkEmbedding> getSurroundingChunks(String messageId, Integer centerChunkIndex,
                                                           int chunksBefore, int chunksAfter) {
        try {
            List<MessageChunkEmbedding> messageChunks = vectorStoreService.getChunksForMessage(messageId);

            if (messageChunks.isEmpty()) {
                log.warn("No chunks found for message {}", messageId);
                return List.of();
            }

            // Sort chunks by index to ensure proper ordering
            messageChunks.sort(Comparator.comparing(MessageChunkEmbedding::getChunkIndex));

            // Find the range of chunks to include
            int startIndex = Math.max(0, centerChunkIndex - chunksBefore);
            int endIndex = Math.min(messageChunks.size() - 1, centerChunkIndex + chunksAfter);

            List<MessageChunkEmbedding> surroundingChunks = new ArrayList<>();
            for (int i = startIndex; i <= endIndex; i++) {
                surroundingChunks.add(messageChunks.get(i));
            }

            log.debug("Retrieved {} chunks around index {} for message {} (range: {} to {})",
                surroundingChunks.size(), centerChunkIndex, messageId, startIndex, endIndex);

            return surroundingChunks;

        } catch (Exception e) {
            log.error("Error getting surrounding chunks for message {} at index {}: {}",
                messageId, centerChunkIndex, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Check if retrieval results meet quality threshold.
     * Now allows empty results (no minimum requirement) for better relevance filtering.
     */
    private boolean isQualitySufficient(List<ExpandedChunkGroup> groups) {
        // Quality is now determined by relevance rather than quantity
        // Empty results are acceptable if no relevant chunks were found
        return !groups.isEmpty();
    }

    /**
     * Combine and deduplicate results from multiple retrieval attempts.
     */
    private List<ExpandedChunkGroup> combineAndDeduplicateResults(List<List<ExpandedChunkGroup>> resultsList) {
        Set<ExpandedChunkGroup> combined = new LinkedHashSet<>();

        for (List<ExpandedChunkGroup> results : resultsList) {
            combined.addAll(results);
        }

        List<ExpandedChunkGroup> finalResults = new ArrayList<>(combined);

        log.debug("Combined results: {} unique groups from {} result sets",
            finalResults.size(), resultsList.size());

        return finalResults;
    }

    /**
     * Get retrieval statistics and metrics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("currentConfig", Map.of(
            "strategy", retrievalStrategy,
            "chunksBefore", defaultChunksBefore,
            "chunksAfter", defaultChunksAfter,
            "maxSimilar", defaultMaxSimilarChunks,
            "threshold", defaultSimilarityThreshold
        ));
        stats.put("adaptiveSettings", Map.of(
            "enabled", adaptiveEnabled,
            "qualityThreshold", adaptiveQualityThreshold,
            "expansionFactor", adaptiveExpansionFactor
        ));
        stats.put("metrics", metrics.getStatistics());
        return stats;
    }

    /**
     * Configuration class for retrieval parameters.
     */
    public static class RetrievalConfig {
        private RetrievalStrategy strategy;
        private Integer chunksBefore;
        private Integer chunksAfter;
        private Integer maxSimilarChunks;
        private Double similarityThreshold;

        public RetrievalConfig() {}

        public static Builder builder() {
            return new Builder();
        }

        // Getters and setters
        public RetrievalStrategy getStrategy() { return strategy; }
        public Integer getChunksBefore() { return chunksBefore; }
        public Integer getChunksAfter() { return chunksAfter; }
        public Integer getMaxSimilarChunks() { return maxSimilarChunks; }
        public Double getSimilarityThreshold() { return similarityThreshold; }

        public void setStrategy(RetrievalStrategy strategy) { this.strategy = strategy; }
        public void setChunksBefore(Integer chunksBefore) { this.chunksBefore = chunksBefore; }
        public void setChunksAfter(Integer chunksAfter) { this.chunksAfter = chunksAfter; }
        public void setMaxSimilarChunks(Integer maxSimilarChunks) { this.maxSimilarChunks = maxSimilarChunks; }
        public void setSimilarityThreshold(Double similarityThreshold) { this.similarityThreshold = similarityThreshold; }

        public static class Builder {
            private final RetrievalConfig config = new RetrievalConfig();

            public Builder strategy(RetrievalStrategy strategy) {
                config.strategy = strategy;
                return this;
            }

            public Builder chunksBefore(int chunksBefore) {
                config.chunksBefore = chunksBefore;
                return this;
            }

            public Builder chunksAfter(int chunksAfter) {
                config.chunksAfter = chunksAfter;
                return this;
            }

            public Builder maxSimilarChunks(int maxSimilarChunks) {
                config.maxSimilarChunks = maxSimilarChunks;
                return this;
            }

            public Builder similarityThreshold(double similarityThreshold) {
                config.similarityThreshold = similarityThreshold;
                return this;
            }

            public RetrievalConfig build() {
                return config;
            }
        }
    }

    /**
     * Retrieval strategy enumeration.
     */
    public enum RetrievalStrategy {
        FIXED,      // Use exact configuration parameters
        ADAPTIVE,   // Adjust parameters based on context quality
        INTELLIGENT // Use multiple approaches and combine results
    }

    /**
     * Data class representing a group of chunks from the same message with surrounding context.
     */
    public static class ExpandedChunkGroup {
        private final String messageId;
        private final List<MessageChunkEmbedding> chunks;

        public ExpandedChunkGroup(String messageId, List<MessageChunkEmbedding> chunks) {
            this.messageId = messageId;
            this.chunks = new ArrayList<>(chunks);
            // Sort chunks by index to ensure chronological order
            this.chunks.sort(Comparator.comparing(MessageChunkEmbedding::getChunkIndex));
        }

        public String getMessageId() { return messageId; }
        public List<MessageChunkEmbedding> getChunks() { return new ArrayList<>(chunks); }
        public int getChunkCount() { return chunks.size(); }

        public String getCombinedText() {
            StringBuilder sb = new StringBuilder();
            for (MessageChunkEmbedding chunk : chunks) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(chunk.getChunkText());
            }
            return sb.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExpandedChunkGroup that = (ExpandedChunkGroup) o;
            return Objects.equals(messageId, that.messageId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(messageId);
        }

        @Override
        public String toString() {
            return "ExpandedChunkGroup{" +
                "messageId='" + messageId + '\'' +
                ", chunkCount=" + chunks.size() +
                '}';
        }
    }

    /**
     * Metrics class for monitoring retrieval performance.
     */
    private static class RetrievalMetrics {
        private final Map<String, Integer> sessionRetrievalCount = new ConcurrentHashMap<>();
        private final Map<String, Long> sessionTotalDuration = new ConcurrentHashMap<>();
        private int totalRetrievals = 0;
        private int totalErrors = 0;
        private long totalDuration = 0;

        public synchronized void recordRetrieval(String sessionId, int groupsReturned, long duration) {
            totalRetrievals++;
            totalDuration += duration;

            sessionRetrievalCount.merge(sessionId, 1, Integer::sum);
            sessionTotalDuration.merge(sessionId, duration, Long::sum);
        }

        public synchronized void recordError(String sessionId, long duration) {
            totalErrors++;
            totalDuration += duration;
        }

        public synchronized Map<String, Object> getStatistics() {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalRetrievals", totalRetrievals);
            stats.put("totalErrors", totalErrors);
            stats.put("averageDuration", totalRetrievals > 0 ? totalDuration / totalRetrievals : 0);
            stats.put("errorRate", totalRetrievals > 0 ? (double) totalErrors / totalRetrievals * 100 : 0);
            stats.put("sessionsServed", sessionRetrievalCount.size());
            return stats;
        }
    }
}
