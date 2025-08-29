package com.agenttimeline.service;

import com.agenttimeline.model.MessageChunkEmbedding;
import com.agenttimeline.model.OllamaRequest;
import com.agenttimeline.model.OllamaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced Ollama service that constructs context-enriched prompts for augmented generation.
 *
 * This service extends the basic OllamaService with Phase 5 context augmentation capabilities:
 * - Constructs prompts with relevant conversation context
 * - Manages context window limits and truncation
 * - Formats context chunks for optimal LLM understanding
 *
 * This is a core component of Phase 5: Context-Augmented Generation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedOllamaService {

    private final WebClient webClient;
    private final ChunkGroupManager chunkGroupManager; // Reserved for future group processing enhancements

    @Value("${ollama.model:sam860/dolphin3-qwen2.5:3b}")
    private String defaultModel;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    // Phase 5 Configuration
    @Value("${context.max.groups:3}")
    private int maxContextGroups;

    @Value("${context.max.total.chunks:20}")
    private int maxTotalChunks;

    @Value("${context.max.prompt.length:4000}")
    private int maxPromptLength;

    @Value("${context.enable.truncation:true}")
    private boolean enableTruncation;

    /**
     * Construct enhanced prompt with context (for debugging and inspection)
     */
    public String constructEnhancedPrompt(String userMessage, List<ChunkGroupManager.ContextChunkGroup> contextGroups) {
        return buildEnhancedPrompt(userMessage, contextGroups);
    }

    /**
     * Generate a response using context-enhanced prompts.
     *
     * @param userMessage The user's current message
     * @param contextGroups List of relevant context chunk groups
     * @param sessionId Session ID for logging
     * @return Enhanced AI response with context awareness
     */
    public Mono<OllamaResponse> generateResponseWithContext(String userMessage,
                                                           List<ChunkGroupManager.ContextChunkGroup> contextGroups,
                                                           String sessionId) {
        log.debug("Generating enhanced response for session {} with {} context groups",
            sessionId, contextGroups != null ? contextGroups.size() : 0);

        try {
            // Construct enhanced prompt with context
            String enhancedPrompt = buildEnhancedPrompt(userMessage, contextGroups);

            log.debug("Enhanced prompt length: {} characters", enhancedPrompt.length());

            OllamaRequest request = new OllamaRequest(defaultModel, enhancedPrompt, false);

            return webClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .doOnNext(response -> log.debug("Enhanced Ollama response received for session {}", sessionId))
                    .doOnError(error -> log.error("Error calling enhanced Ollama API for session {}: {}",
                        sessionId, error.getMessage(), error));

        } catch (Exception e) {
            log.error("Error generating enhanced response for session {}: {}", sessionId, e.getMessage(), e);
            // Fallback to basic response without context
            return generateBasicResponse(userMessage, sessionId);
        }
    }

    /**
     * Construct an enhanced prompt with relevant conversation context.
     */
    private String buildEnhancedPrompt(String userMessage,
                                     List<ChunkGroupManager.ContextChunkGroup> contextGroups) {
        StringBuilder promptBuilder = new StringBuilder();

        // Add context header if we have context groups
        if (contextGroups != null && !contextGroups.isEmpty()) {
            promptBuilder.append("Previous relevant conversation context:\n");

            // Process and add context groups
            List<ChunkGroupManager.ContextChunkGroup> processedGroups =
                processContextGroups(contextGroups);

            for (int i = 0; i < processedGroups.size(); i++) {
                ChunkGroupManager.ContextChunkGroup group = processedGroups.get(i);

                promptBuilder.append("\nContext ").append(i + 1).append(":\n");
                String contextText = formatContextGroup(group);
                promptBuilder.append(contextText).append("\n");
            }

            promptBuilder.append("\n");
        }

        // Add current user message
        promptBuilder.append("Current user message: ").append(userMessage);

        String enhancedPrompt = promptBuilder.toString();

        // Apply truncation if enabled and necessary
        if (enableTruncation && enhancedPrompt.length() > maxPromptLength) {
            log.debug("Prompt length {} exceeds max {}, applying truncation", enhancedPrompt.length(), maxPromptLength);
            enhancedPrompt = truncatePrompt(enhancedPrompt);
        }

        return enhancedPrompt;
    }

    /**
     * Process context groups to fit within limits and prioritize quality.
     */
    private List<ChunkGroupManager.ContextChunkGroup> processContextGroups(
            List<ChunkGroupManager.ContextChunkGroup> contextGroups) {

        if (contextGroups == null || contextGroups.isEmpty()) {
            return List.of();
        }

        // Limit number of groups
        List<ChunkGroupManager.ContextChunkGroup> limitedGroups = contextGroups.stream()
            .limit(maxContextGroups)
            .toList();

        // Check total chunk count and truncate if necessary
        int totalChunks = limitedGroups.stream()
            .mapToInt(ChunkGroupManager.ContextChunkGroup::getTotalChunks)
            .sum();

        if (totalChunks > maxTotalChunks) {
            log.debug("Total chunks {} exceeds max {}, applying group-level truncation", totalChunks, maxTotalChunks);
            limitedGroups = truncateGroupsByChunks(limitedGroups);
        }

        return limitedGroups;
    }

    /**
     * Format a context group for inclusion in the prompt.
     */
    private String formatContextGroup(ChunkGroupManager.ContextChunkGroup group) {
        StringBuilder formatted = new StringBuilder();

        // Add group metadata
        formatted.append("From conversation: ").append(group.getMessageId()).append("\n");

        // Add chunk content
        List<MessageChunkEmbedding> chunks = group.getChunks();
        for (int i = 0; i < chunks.size(); i++) {
            MessageChunkEmbedding chunk = chunks.get(i);
            formatted.append("• ").append(chunk.getChunkText());

            // Add separator between chunks (except for the last one)
            if (i < chunks.size() - 1) {
                formatted.append(" ");
            }
        }

        return formatted.toString();
    }

    /**
     * Truncate groups when total chunk count exceeds limit.
     * Prioritizes keeping more recent and complete groups.
     */
    private List<ChunkGroupManager.ContextChunkGroup> truncateGroupsByChunks(
            List<ChunkGroupManager.ContextChunkGroup> groups) {

        // Sort groups by chunk count (ascending) to prioritize smaller groups for removal
        List<ChunkGroupManager.ContextChunkGroup> sortedGroups = groups.stream()
            .sorted(Comparator.comparingInt(ChunkGroupManager.ContextChunkGroup::getTotalChunks))
            .collect(Collectors.toList());

        List<ChunkGroupManager.ContextChunkGroup> result = new ArrayList<>();
        int totalChunks = 0;

        // Add groups until we hit the chunk limit
        for (ChunkGroupManager.ContextChunkGroup group : sortedGroups) {
            if (totalChunks + group.getTotalChunks() <= maxTotalChunks) {
                result.add(group);
                totalChunks += group.getTotalChunks();
            } else {
                break;
            }
        }

        // If we still have space, try to add partial groups (not implemented in this version)
        // This is a simplified approach - could be enhanced to split large groups

        log.debug("Truncated from {} to {} groups, {} total chunks",
            groups.size(), result.size(), totalChunks);

        return result;
    }

    /**
     * Apply intelligent truncation to a prompt that exceeds length limits.
     */
    private String truncatePrompt(String prompt) {
        if (prompt.length() <= maxPromptLength) {
            return prompt;
        }

        // Strategy: Keep the user message intact, truncate context proportionally
        String userMessageMarker = "\nCurrent user message: ";
        int userMessageStart = prompt.lastIndexOf(userMessageMarker);

        if (userMessageStart == -1) {
            // Fallback: simple truncation
            return prompt.substring(0, maxPromptLength - 3) + "...";
        }

        // Extract user message (keep it intact)
        String userMessage = prompt.substring(userMessageStart);
        String contextPart = prompt.substring(0, userMessageStart);

        // Calculate how much context we can keep
        int availableLength = maxPromptLength - userMessage.length() - 3; // 3 for "..."

        if (availableLength <= 0) {
            // Not enough space even for user message, extreme truncation
            return prompt.substring(0, maxPromptLength - 3) + "...";
        }

        // Truncate context proportionally
        String truncatedContext;
        if (contextPart.length() <= availableLength) {
            truncatedContext = contextPart;
        } else {
            // Keep beginning and end of context, truncate middle
            int keepStart = availableLength / 3;
            int keepEnd = availableLength / 3;

            truncatedContext = contextPart.substring(0, keepStart) +
                             "\n[...context truncated...]\n" +
                             contextPart.substring(contextPart.length() - keepEnd);
        }

        return truncatedContext + userMessage;
    }

    /**
     * Fallback method to generate basic response without context.
     */
    private Mono<OllamaResponse> generateBasicResponse(String userMessage, String sessionId) {
        log.warn("Falling back to basic response generation for session {}", sessionId);

        OllamaRequest request = new OllamaRequest(defaultModel, userMessage, false);

        return webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OllamaResponse.class);
    }

    // Configuration getters for monitoring and debugging
    public int getMaxContextGroups() {
        return maxContextGroups;
    }

    public int getMaxTotalChunks() {
        return maxTotalChunks;
    }

    public int getMaxPromptLength() {
        return maxPromptLength;
    }

    public boolean isEnableTruncation() {
        return enableTruncation;
    }
}
