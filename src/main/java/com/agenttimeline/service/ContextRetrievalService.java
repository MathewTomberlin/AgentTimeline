package com.agenttimeline.service;

import com.agenttimeline.model.MessageChunkEmbedding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for retrieving relevant conversation context using vector similarity search
 * and expanding with surrounding chunks for augmented generation.
 *
 * This service is a core component of Phase 5: Context-Augmented Generation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContextRetrievalService {

    private final VectorStoreService vectorStoreService;

    // Configuration properties with defaults
    @Value("${context.chunks.before:2}")
    private int chunksBefore;

    @Value("${context.chunks.after:2}")
    private int chunksAfter;

    @Value("${context.max.similar:5}")
    private int maxSimilarChunks;

    @Value("${context.similarity.threshold:0.3}")
    private double similarityThreshold;

    /**
     * Retrieve relevant context chunks for a user message within a session.
     *
     * @param userMessage The user's current message
     * @param sessionId The session ID to search within
     * @return List of relevant context chunks with surrounding context
     */
    public List<ExpandedChunkGroup> retrieveContext(String userMessage, String sessionId) {
        log.debug("Retrieving context for user message in session {}: '{}'",
            sessionId, userMessage.substring(0, Math.min(50, userMessage.length())));

        try {
            // 1. Find similar chunks using vector search
            List<MessageChunkEmbedding> similarChunks = vectorStoreService.findSimilarChunks(
                userMessage, sessionId, maxSimilarChunks);

            if (similarChunks.isEmpty()) {
                log.debug("No similar chunks found for session {}", sessionId);
                return List.of();
            }

            log.debug("Found {} similar chunks for context retrieval", similarChunks.size());

            // 2. Expand each chunk with surrounding context
            Set<ExpandedChunkGroup> expandedGroups = new LinkedHashSet<>();
            for (MessageChunkEmbedding chunk : similarChunks) {
                List<MessageChunkEmbedding> surroundingChunks = getSurroundingChunks(
                    chunk.getMessageId(), chunk.getChunkIndex(), chunksBefore, chunksAfter);
                expandedGroups.add(new ExpandedChunkGroup(chunk.getMessageId(), surroundingChunks));
                log.debug("Expanded chunk from message {} (index {}) with {} surrounding chunks",
                    chunk.getMessageId(), chunk.getChunkIndex(), surroundingChunks.size());
            }

            List<ExpandedChunkGroup> result = new ArrayList<>(expandedGroups);
            log.info("Retrieved {} expanded chunk groups for session {}", result.size(), sessionId);

            return result;

        } catch (Exception e) {
            log.error("Error retrieving context for session {}: {}", sessionId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Get chunks surrounding a specific chunk within the same message.
     *
     * @param messageId The message ID to get chunks from
     * @param centerChunkIndex The index of the center chunk
     * @param chunksBefore Number of chunks to get before the center
     * @param chunksAfter Number of chunks to get after the center
     * @return List of surrounding chunks including the center chunk
     */
    private List<MessageChunkEmbedding> getSurroundingChunks(String messageId, Integer centerChunkIndex,
                                                           int chunksBefore, int chunksAfter) {
        try {
            // Get all chunks for this message
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

        public String getMessageId() {
            return messageId;
        }

        public List<MessageChunkEmbedding> getChunks() {
            return new ArrayList<>(chunks);
        }

        public int getChunkCount() {
            return chunks.size();
        }

        /**
         * Get the combined text of all chunks in this group.
         */
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

    // Getters for configuration values (useful for debugging and monitoring)
    public int getChunksBefore() {
        return chunksBefore;
    }

    public int getChunksAfter() {
        return chunksAfter;
    }

    public int getMaxSimilarChunks() {
        return maxSimilarChunks;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }
}
