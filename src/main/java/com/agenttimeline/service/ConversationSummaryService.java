package com.agenttimeline.service;

import com.agenttimeline.model.Message;
import com.agenttimeline.model.OllamaRequest;
import com.agenttimeline.model.OllamaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for generating intelligent summaries of conversation windows using LLM.
 *
 * This service is a core component of Phase 6: Enhanced Context Management.
 * It provides:
 * - LLM-powered conversation summarization
 * - Context-aware summary generation
 * - Progressive summary updates
 * - Structured prompt engineering for summarization
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationSummaryService {

    private final WebClient webClient;

    @Value("${ollama.model:sam860/dolphin3-qwen2.5:3b}")
    private String defaultModel;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${conversation.summary.max-input-length:2000}")
    private int maxInputLength;

    @Value("${conversation.summary.temperature:0.3}")
    private double summaryTemperature;

    // Date formatter for conversation timestamps
    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * Generate a summary of the provided conversation messages.
     *
     * @param messages List of messages to summarize
     * @param sessionId Session ID for logging
     * @return Generated summary text
     */
    public String generateSummary(List<Message> messages, String sessionId) {
        if (messages == null || messages.isEmpty()) {
            log.debug("No messages to summarize for session {}", sessionId);
            return null;
        }

        log.info("Generating summary for {} messages in session {}", messages.size(), sessionId);

        try {
            // Format messages for summarization
            String conversationText = formatMessagesForSummary(messages);

            // Check if conversation is too long
            if (conversationText.length() > maxInputLength) {
                log.debug("Conversation text too long ({} chars), truncating for summary",
                    conversationText.length());
                conversationText = truncateForSummary(conversationText);
            }

            // Generate summary using LLM
            String summary = generateLLMSummary(conversationText, sessionId);

            log.info("Generated summary for session {}: {} chars", sessionId,
                summary != null ? summary.length() : 0);

            return summary;

        } catch (Exception e) {
            log.error("Error generating summary for session {}: {}", sessionId, e.getMessage(), e);
            return generateFallbackSummary(messages);
        }
    }

    /**
     * Update an existing summary with new messages.
     *
     * @param existingSummary The current summary
     * @param newMessages New messages to incorporate
     * @param sessionId Session ID for logging
     * @return Updated summary
     */
    public String updateSummary(String existingSummary, List<Message> newMessages, String sessionId) {
        if (newMessages == null || newMessages.isEmpty()) {
            return existingSummary;
        }

        if (existingSummary == null || existingSummary.trim().isEmpty()) {
            return generateSummary(newMessages, sessionId);
        }

        log.debug("Updating summary for session {} with {} new messages", sessionId, newMessages.size());

        try {
            String newMessagesText = formatMessagesForSummary(newMessages);
            String updatePrompt = buildSummaryUpdatePrompt(existingSummary, newMessagesText);

            // Check length constraints
            if (updatePrompt.length() > maxInputLength) {
                log.debug("Update prompt too long ({} chars), using full regeneration",
                    updatePrompt.length());
                return generateSummary(newMessages, sessionId);
            }

            String updatedSummary = generateLLMSummary(updatePrompt, sessionId + "-update");

            log.debug("Updated summary for session {}: {} -> {} chars", sessionId,
                existingSummary.length(), updatedSummary != null ? updatedSummary.length() : 0);

            return updatedSummary;

        } catch (Exception e) {
            log.error("Error updating summary for session {}, keeping existing summary: {}",
                sessionId, e.getMessage());
            return existingSummary;
        }
    }

    /**
     * Format messages into a readable conversation format for summarization.
     */
    private String formatMessagesForSummary(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("Conversation History:\n\n");

        for (Message message : messages) {
            String timestamp = message.getTimestamp().format(TIME_FORMATTER);
            String role = message.getRole().toString().toLowerCase();
            String content = message.getContent();

            sb.append(String.format("[%s] %s: %s\n\n", timestamp, role, content));
        }

        return sb.toString();
    }

    /**
     * Truncate conversation text if it's too long for summarization.
     */
    private String truncateForSummary(String conversationText) {
        // Keep the most recent part of the conversation
        int keepLength = maxInputLength - 500; // Leave room for prompt
        if (conversationText.length() <= keepLength) {
            return conversationText;
        }

        // Find a good truncation point (end of a message)
        int truncationPoint = findTruncationPoint(conversationText, keepLength);

        String truncated = conversationText.substring(truncationPoint);
        log.debug("Truncated conversation from {} to {} chars", conversationText.length(), truncated.length());

        return "[Earlier messages truncated for length...]\n\n" + truncated;
    }

    /**
     * Find a good truncation point near the specified length.
     */
    private int findTruncationPoint(String text, int targetLength) {
        // Look for message boundaries (double newlines) near the target length
        int searchStart = Math.max(0, targetLength - 200);
        int searchEnd = Math.min(text.length(), targetLength + 200);

        for (int i = searchStart; i < searchEnd; i++) {
            if (i >= 2 && text.substring(i - 2, i).equals("\n\n")) {
                return i;
            }
        }

        // Fallback to character-based truncation
        return Math.min(targetLength, text.length());
    }

    /**
     * Generate summary using LLM.
     */
    private String generateLLMSummary(String inputText, String contextId) {
        String prompt = buildSummaryPrompt(inputText);

        log.debug("Generating LLM summary for context {}: input length {}", contextId, inputText.length());

        try {
            OllamaRequest request = new OllamaRequest(defaultModel, prompt, false);

            Mono<OllamaResponse> responseMono = webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OllamaResponse.class)
                .doOnNext(response -> log.debug("Received LLM summary response for context {}", contextId))
                .doOnError(error -> log.error("Error calling LLM for summary context {}: {}",
                    contextId, error.getMessage()));

            OllamaResponse response = responseMono.block();
            if (response != null && response.getResponse() != null) {
                String summary = response.getResponse().trim();
                log.debug("Generated summary for context {}: {} chars", contextId, summary.length());
                return summary;
            } else {
                log.warn("Null or empty response from LLM for summary context {}", contextId);
                return null;
            }

        } catch (Exception e) {
            log.error("Exception during LLM summary generation for context {}: {}", contextId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Build the prompt for conversation summarization.
     */
    private String buildSummaryPrompt(String conversationText) {
        return String.format(
            "You are an expert conversation summarizer. Your task is to create a comprehensive summary of the following conversation. Focus on:\n\n" +
            "1. Topics discussed\n" +
            "2. Information shared by either participant\n" +
            "3. Decisions made or actions agreed upon\n" +
            "4. Questions asked and answers provided\n" +
            "5. Any specific facts, dates, relationships, or details mentioned\n\n" +
            "Keep the summary informative. Preserve important context that would help someone continue this conversation naturally.\n\n" +
            "Conversation to summarize:\n\n%s\n\n" +
            "Summary:",
            conversationText
        );
    }

    /**
     * Build the prompt for updating an existing summary.
     */
    private String buildSummaryUpdatePrompt(String existingSummary, String newMessagesText) {
        return String.format(
            "You have an existing conversation summary and some new messages. Your task is to update the summary to incorporate the new information.\n\n" +
            "EXISTING SUMMARY:\n%s\n\n" +
            "NEW MESSAGES:\n%s\n\n" +
            "Please provide an updated summary that:\n" +
            "1. Incorporates all new information from the new messages\n" +
            "2. Maintains continuity with the existing summary\n" +
            "3. Stays concise but comprehensive\n" +
            "4. Preserves important context for continuing the conversation\n\n" +
            "Updated Summary:",
            existingSummary,
            newMessagesText
        );
    }

    /**
     * Generate a fallback summary when LLM is unavailable.
     */
    private String generateFallbackSummary(List<Message> messages) {
        if (messages.isEmpty()) {
            return "No conversation history available.";
        }

        log.debug("Generating fallback summary for {} messages", messages.size());

        // Simple fallback: extract key information manually
        StringBuilder summary = new StringBuilder();
        summary.append("Conversation summary: ");

        // Count messages by role
        long userMessages = messages.stream()
            .filter(m -> m.getRole() == Message.Role.USER)
            .count();
        long assistantMessages = messages.stream()
            .filter(m -> m.getRole() == Message.Role.ASSISTANT)
            .count();

        summary.append(String.format("%d exchanges between user and assistant. ", messages.size()));

        // Extract some basic keywords from recent messages
        List<String> recentContent = messages.stream()
            .skip(Math.max(0, messages.size() - 3)) // Last 3 messages
            .map(Message::getContent)
            .map(content -> content.length() > 100 ? content.substring(0, 100) + "..." : content)
            .toList();

        if (!recentContent.isEmpty()) {
            summary.append("Recent topics: ");
            summary.append(String.join(" | ", recentContent));
        }

        return summary.toString();
    }

    /**
     * Check if the summary service is available (LLM is accessible).
     */
    public boolean isAvailable() {
        try {
            OllamaRequest testRequest = new OllamaRequest(defaultModel, "Test", false);

            webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(testRequest)
                .retrieve()
                .bodyToMono(OllamaResponse.class)
                .timeout(java.time.Duration.ofSeconds(5))
                .block();

            return true;
        } catch (Exception e) {
            log.warn("Summary service not available: {}", e.getMessage());
            return false;
        }
    }

    // Configuration getters for monitoring
    public int getMaxInputLength() {
        return maxInputLength;
    }

    public double getSummaryTemperature() {
        return summaryTemperature;
    }
}
