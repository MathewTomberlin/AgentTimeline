package com.agenttimeline.service;

import com.agenttimeline.model.Message;
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

import java.time.Duration;
import java.util.*;

/**
 * Enhanced prompt builder that intelligently combines multiple context sources.
 *
 * This service replaces the current prompt construction logic with a sophisticated
 * system that integrates:
 * - Immediate conversation history and summaries
 * - Key information extracted from messages
 * - Relevant historical chunks from vector search
 * - Current message context
 *
 * This is a core component of Phase 6: Enhanced Context Management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedPromptBuilder {

    private final WebClient webClient;

    // Configuration parameters
    @Value("${prompt.max.length:4000}")
    private int maxPromptLength;

    @Value("${prompt.enable.truncation:true}")
    private boolean enableTruncation;

    @Value("${prompt.context.priority:balanced}")
    private String contextPriority;

    @Value("${prompt.include.metadata:true}")
    private boolean includeMetadata;

    @Value("${prompt.improved.format.enabled:true}")
    private boolean improvedFormatEnabled;

    @Value("${prompt.chatml.format.enabled:true}")
    private boolean chatmlFormatEnabled;

    // Ollama configuration for LLM-based extraction
    @Value("${ollama.model:sam860/dolphin3-qwen2.5:3b}")
    private String defaultModel;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;



    /**
     * Build an enhanced prompt from multiple context sources.
     *
     * @param userMessage The current user message
     * @param conversationContext Immediate conversation context
     * @param relevantChunks Historical chunks from vector search
     * @param sessionId Session ID for logging
     * @return Enhanced prompt string
     */
    public String buildEnhancedPrompt(String userMessage,
                                    ConversationHistoryManager.ConversationContext conversationContext,
                                    List<ConfigurableContextRetrievalService.ExpandedChunkGroup> relevantChunks,
                                    String sessionId) {

        log.debug("Building enhanced prompt for session {} with context sources: conversation={}, chunks={}",
            sessionId,
            conversationContext != null ? "present" : "null",
            relevantChunks != null ? relevantChunks.size() : 0);

        PromptComponents components = new PromptComponents();

        // Add system context
        components.addSystemContext(buildSystemContext());

        // Add conversation history/summary
        if (conversationContext != null && !conversationContext.getRecentMessages().isEmpty()) {
            components.addConversationHistory(buildConversationHistorySection(conversationContext));
        }

        // Add chunk summary as assistant message
        if (relevantChunks != null && !relevantChunks.isEmpty()) {
            components.addChunkSummary(buildChunkSummarySection(relevantChunks, sessionId));
        }

        // Add current message
        components.addCurrentMessage(buildCurrentMessageSection(userMessage));

        // Build final prompt with intelligent prioritization
        String finalPrompt = buildFinalPrompt(components, sessionId);

        log.debug("Built enhanced prompt for session {}: {} characters", sessionId, finalPrompt.length());

        return finalPrompt;
    }

    /**
     * Build system context section with improved format for dolphin3 model.
     */
    private String buildSystemContext() {
        return "You are a helpful AI assistant with access to conversation history.\n" +
               "Your task is to provide accurate, contextual responses using the provided information.\n\n" +
               "INSTRUCTIONS:\n" +
               "- Use conversation history to maintain context and avoid repetition\n" +
               "- Reference specific details from previous messages when relevant\n" +
               "- Provide direct, helpful answers to user questions\n" +
               "- If information conflicts, prioritize the most recent context";
    }

    /**
     * Build conversation history section with improved format.
     */
    private String buildConversationHistorySection(ConversationHistoryManager.ConversationContext context) {
        StringBuilder section = new StringBuilder();

        if (context.hasSummary()) {
            section.append("CONVERSATION SUMMARY:\n");
            section.append(context.getSummary()).append("\n\n");
        }

        if (context.hasRecentMessages()) {
            section.append("RECENT CONVERSATION:\n");
            for (Message message : context.getRecentMessages()) {
                String role = message.getRole() == Message.Role.USER ? "User" : "Assistant";
                String content = message.getContent();
                section.append(role).append(": ").append(content).append("\n");
            }
            section.append("\n");
        }

        return section.toString();
    }

    /**
     * Build chunk summary section using LLM-based information extraction.
     */
    private String buildChunkSummarySection(List<ConfigurableContextRetrievalService.ExpandedChunkGroup> chunks, String sessionId) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }

        try {
            // Build debug section showing retrieved chunks
            StringBuilder debugSection = new StringBuilder();
            debugSection.append("DEBUG - RETRIEVED CHUNKS:\n");
            for (int i = 0; i < chunks.size(); i++) {
                ConfigurableContextRetrievalService.ExpandedChunkGroup group = chunks.get(i);
                debugSection.append(String.format("Chunk Group %d (Message: %s):\n", i + 1, group.getMessageId()));
                debugSection.append(group.getCombinedText()).append("\n\n");
            }

            // Use LLM to extract key information from all chunks
            StringBuilder allChunksText = new StringBuilder();
            for (ConfigurableContextRetrievalService.ExpandedChunkGroup group : chunks) {
                String combinedText = group.getCombinedText();
                if (combinedText != null && !combinedText.trim().isEmpty()) {
                    allChunksText.append(combinedText.trim()).append("\n");
                }
            }

            if (allChunksText.length() == 0) {
                return debugSection.toString() + "No meaningful chunk content found.";
            }

            // Extract key information using LLM
            String extractedInfo = extractKeyInformationWithLLM(allChunksText.toString().trim(), sessionId);

            if (extractedInfo != null && !extractedInfo.trim().isEmpty()) {
                log.debug("LLM extracted information for session {}: {}", sessionId, extractedInfo);
                return debugSection.toString() + "EXTRACTED SUMMARY:\n" + extractedInfo;
            }

        } catch (Exception e) {
            log.error("Error extracting information with LLM for session {}: {}", sessionId, e.getMessage(), e);
        }

        // Fallback to basic summary if LLM extraction fails
        return "Some details from previous conversations were found.";
    }

    /**
     * Extract key information from text using LLM.
     */
    private String extractKeyInformationWithLLM(String text, String sessionId) {
        try {
            String extractionPrompt = String.format(
                "You are an expert information extraction assistant. Analyze the following conversation text and create a brief, factual summary of the most important information learned about the user.\n\n" +
                "CONVERSATION TEXT:\n%s\n\n" +
                "INSTRUCTIONS:\n" +
                "- Focus on personal information (names, locations, occupations, preferences)\n" +
                "- Include specific facts, details, and context\n" +
                "- Write in a neutral, factual style\n" +
                "- Keep it concise (1-2 sentences)\n" +
                "- Use direct, clear language\n" +
                "- If no important information is found, respond with an empty string\n\n" +
                "SUMMARY:",
                text
            );

            OllamaRequest request = new OllamaRequest(defaultModel, extractionPrompt, false);

            Mono<OllamaResponse> responseMono = webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OllamaResponse.class)
                .timeout(Duration.ofSeconds(10)) // Shorter timeout for extraction
                .doOnError(error -> log.warn("LLM extraction failed for session {}: {}", sessionId, error.getMessage()));

            OllamaResponse response = responseMono.block();
            if (response != null && response.getResponse() != null) {
                String extracted = response.getResponse().trim();

                // Clean up the response
                if (extracted.toLowerCase().contains("no important information") ||
                    extracted.toLowerCase().contains("no significant information") ||
                    extracted.length() < 10) {
                    return "";
                }

                // Clean up any unwanted prefixes that might have been added
                extracted = extracted.trim();
                if (extracted.toLowerCase().startsWith("the user ")) {
                    extracted = extracted.substring(8);
                    extracted = extracted.substring(0, 1).toUpperCase() + extracted.substring(1);
                }
                if (extracted.toLowerCase().startsWith("you ")) {
                    extracted = extracted.substring(4);
                    extracted = extracted.substring(0, 1).toUpperCase() + extracted.substring(1);
                }

                return extracted;
            }

        } catch (Exception e) {
            log.error("Exception during LLM extraction for session {}: {}", sessionId, e.getMessage());
        }

        return "";
    }



    /**
     * Check if context chunk is repetitive or unhelpful.
     */
    private boolean isRepetitiveContext(String text) {
        if (text == null) return true;

        String lowerText = text.toLowerCase();
        // Filter out generic or repetitive phrases
        return lowerText.contains("what did i say") ||
               lowerText.contains("can you tell me") ||
               lowerText.length() < 10 ||
               lowerText.split("\\s+").length < 3;
    }

    /**
     * Build current message section with clear formatting.
     */
    private String buildCurrentMessageSection(String userMessage) {
        return "CURRENT USER MESSAGE:\n" + userMessage.trim();
    }

    /**
     * Build final prompt using appropriate format based on configuration.
     * Uses ChatML format for dolphin3-qwen2.5 model if enabled.
     */
    private String buildFinalPrompt(PromptComponents components, String sessionId) {
        if (chatmlFormatEnabled) {
            return buildChatMLPrompt(components, sessionId);
        } else {
            return buildLegacyPrompt(components, sessionId);
        }
    }

    /**
     * Build final prompt using ChatML format for dolphin3-qwen2.5 model.
     * All context must be structured as chat messages with proper roles.
     */
    private String buildChatMLPrompt(PromptComponents components, String sessionId) {
        StringBuilder prompt = new StringBuilder();

        // System message in ChatML format
        prompt.append("<|im_start|>system\n");
        prompt.append(buildChatMLSystemMessage()).append("\n");
        prompt.append("<|im_end|>\n");

        // Add chunk summary as assistant message (before conversation history)
        if (!components.getChunkSummary().isEmpty()) {
            prompt.append("<|im_start|>assistant\n");
            prompt.append(components.getChunkSummary()).append("\n");
            prompt.append("<|im_end|>\n");
        }

        // Add conversation history (without key information and historical context)
        if (!components.getConversationHistory().isEmpty()) {
            List<ChatMessage> historyMessages = convertConversationHistoryToMessages(components.getConversationHistory());
            for (ChatMessage msg : historyMessages) {
                prompt.append("<|im_start|>").append(msg.getRole()).append("\n");
                prompt.append(msg.getContent()).append("\n");
                prompt.append("<|im_end|>\n");
            }
        }

        // Add current user message
        prompt.append("<|im_start|>user\n");
        prompt.append(extractUserMessageContent(components.getCurrentMessage())).append("\n");
        prompt.append("<|im_end|>\n");

        // Start assistant response
        prompt.append("<|im_start|>assistant\n");

        // Final length check and truncation if needed
        String finalPrompt = prompt.toString();
        if (enableTruncation && finalPrompt.length() > maxPromptLength) {
            finalPrompt = applyChatMLTruncation(finalPrompt, sessionId);
        }

        return finalPrompt;
    }

    /**
     * Build final prompt using legacy format (for backward compatibility).
     */
    private String buildLegacyPrompt(PromptComponents components, String sessionId) {
        StringBuilder prompt = new StringBuilder();

        // System instructions first (clear and concise)
        prompt.append(components.getSystemContext()).append("\n\n");

        // Add chunk summary right after system (before conversation history)
        if (!components.getChunkSummary().isEmpty()) {
            prompt.append(components.getChunkSummary()).append("\n\n");
        }

        // Add conversation history
        if (!components.getConversationHistory().isEmpty()) {
            prompt.append(components.getConversationHistory());
        }

        // Current user message (clearly marked)
        prompt.append(components.getCurrentMessage());

        // Final length check and truncation if needed
        String finalPrompt = prompt.toString();
        if (enableTruncation && finalPrompt.length() > maxPromptLength) {
            finalPrompt = applyFinalTruncation(finalPrompt, sessionId);
        }

        return finalPrompt;
    }

    /**
     * Build system message optimized for ChatML format.
     */
    private String buildChatMLSystemMessage() {
        return "You are a helpful AI assistant with access to conversation history. " +
               "Your task is to provide accurate, contextual responses using the provided information. " +
               "Use conversation history to maintain context and avoid repetition. " +
               "Reference specific details from previous messages when relevant. " +
               "Provide direct, helpful answers to user questions. " +
               "If information conflicts, prioritize the most recent context.";
    }

    /**
     * Convert all context information into chat message format.
     */
    private List<ChatMessage> convertContextToChatMessages(PromptComponents components, String sessionId) {
        List<ChatMessage> messages = new ArrayList<>();

        // Convert conversation history to chat messages
        if (!components.getConversationHistory().isEmpty()) {
            messages.addAll(convertConversationHistoryToMessages(components.getConversationHistory()));
        }

        // Chunk summary is handled separately in buildChatMLPrompt

        return messages;
    }

    /**
     * Convert conversation history to chat message format.
     */
    private List<ChatMessage> convertConversationHistoryToMessages(String conversationHistory) {
        List<ChatMessage> messages = new ArrayList<>();

        // Parse the conversation history and convert to proper chat messages
        String[] lines = conversationHistory.split("\n");
        StringBuilder currentContent = new StringBuilder();
        String currentRole = null;

        for (String line : lines) {
            if (line.startsWith("User: ")) {
                // Save previous message if exists
                if (currentRole != null && currentContent.length() > 0) {
                    messages.add(new ChatMessage(currentRole, currentContent.toString().trim()));
                }
                // Start new user message
                currentRole = "user";
                currentContent = new StringBuilder(line.substring(6)); // Remove "User: "
            } else if (line.startsWith("Assistant: ")) {
                // Save previous message if exists
                if (currentRole != null && currentContent.length() > 0) {
                    messages.add(new ChatMessage(currentRole, currentContent.toString().trim()));
                }
                // Start new assistant message
                currentRole = "assistant";
                currentContent = new StringBuilder(line.substring(11)); // Remove "Assistant: "
            } else if (!line.trim().isEmpty() && currentRole != null) {
                // Continue current message
                currentContent.append("\n").append(line);
            }
        }

        // Add final message
        if (currentRole != null && currentContent.length() > 0) {
            messages.add(new ChatMessage(currentRole, currentContent.toString().trim()));
        }

        return messages;
    }





    /**
     * Extract user message content from current message section.
     */
    private String extractUserMessageContent(String currentMessage) {
        // Remove the "CURRENT USER MESSAGE:\n" prefix if present
        if (currentMessage.startsWith("CURRENT USER MESSAGE:\n")) {
            return currentMessage.substring("CURRENT USER MESSAGE:\n".length()).trim();
        }
        return currentMessage.trim();
    }

    /**
     * Apply truncation specifically for ChatML format.
     */
    private String applyChatMLTruncation(String prompt, String sessionId) {
        log.warn("Applying ChatML truncation to prompt for session {}: {} chars (limit: {})",
            sessionId, prompt.length(), maxPromptLength);

        // For ChatML format, we need to be more careful about truncation
        // Try to truncate from the middle, keeping system message and recent context
        String systemTag = "<|im_start|>system\n";
        String systemEndTag = "<|im_end|>\n";
        String userTag = "<|im_start|>user\n";

        int systemStart = prompt.indexOf(systemTag);
        int systemEnd = prompt.indexOf(systemEndTag, systemStart);
        int lastUserStart = prompt.lastIndexOf(userTag);

        if (systemStart >= 0 && systemEnd > 0 && lastUserStart > 0) {
            // Keep system message and final user message
            String systemPart = prompt.substring(systemStart, systemEnd + systemEndTag.length());
            String userPart = prompt.substring(lastUserStart);
            String assistantPart = "<|im_start|>assistant\n";

            // Calculate space for middle content
            int overhead = systemPart.length() + userPart.length() + assistantPart.length();
            int availableForMiddle = maxPromptLength - overhead - 100; // Buffer

            if (availableForMiddle > 0) {
                // Find middle content
                int middleStart = systemEnd + systemEndTag.length();
                int middleEnd = lastUserStart;

                if (middleEnd > middleStart) {
                    String middleContent = prompt.substring(middleStart, middleEnd);
                    if (middleContent.length() > availableForMiddle) {
                        // Truncate middle content
                        middleContent = middleContent.substring(0, availableForMiddle - 50) +
                                      "\n[...context truncated due to length...]\n";
                    }
                    return systemPart + middleContent + userPart + assistantPart;
                }
            }
        }

        // Fallback: simple truncation
        return prompt.substring(0, maxPromptLength - 50) + "\n[...truncated...]\n";
    }







    /**
     * Apply final truncation to the complete prompt if still too long.
     */
    private String applyFinalTruncation(String prompt, String sessionId) {
        log.warn("Applying final truncation to prompt for session {}: {} chars (limit: {})",
            sessionId, prompt.length(), maxPromptLength);

        // Strategy: Keep system context and current message, truncate context sections
        String systemContext = extractSystemContext(prompt);
        String currentMessage = extractCurrentMessage(prompt);
        String contextSections = extractContextSections(prompt);

        // Calculate space allocation
        int systemAndMessageLength = systemContext.length() + currentMessage.length() + 100;
        int availableForContext = maxPromptLength - systemAndMessageLength;

        // Truncate context sections
        String truncatedContext = "";
        if (availableForContext > 200) {
            truncatedContext = truncateSection(contextSections, availableForContext);
        }

        // Rebuild prompt
        String finalPrompt = systemContext + "\n\n" + truncatedContext + "\n" + currentMessage;

        log.warn("Final prompt truncated for session {}: {} -> {} chars",
            sessionId, prompt.length(), finalPrompt.length());

        return finalPrompt;
    }

    /**
     * Extract system context from prompt.
     */
    private String extractSystemContext(String prompt) {
        int firstDoubleNewline = prompt.indexOf("\n\n");
        return firstDoubleNewline > 0 ? prompt.substring(0, firstDoubleNewline) : prompt;
    }

    /**
     * Extract current message from prompt.
     */
    private String extractCurrentMessage(String prompt) {
        int currentMessageIndex = prompt.lastIndexOf("## Current Message:");
        return currentMessageIndex >= 0 ? prompt.substring(currentMessageIndex) : "";
    }

    /**
     * Extract context sections from prompt.
     */
    private String extractContextSections(String prompt) {
        int firstDoubleNewline = prompt.indexOf("\n\n");
        int currentMessageIndex = prompt.lastIndexOf("## Current Message:");

        if (firstDoubleNewline > 0 && currentMessageIndex > firstDoubleNewline) {
            return prompt.substring(firstDoubleNewline + 2, currentMessageIndex);
        }

        return "";
    }

    /**
     * Truncate a section of text to fit within the specified length.
     */
    private String truncateSection(String section, int maxLength) {
        if (section == null || section.length() <= maxLength) {
            return section;
        }

        // Find a good break point (sentence, then word boundary)
        int truncateAt = maxLength - 50; // Leave room for truncation message
        if (truncateAt < 0) truncateAt = maxLength / 2;

        // Try to break at sentence end
        int lastSentence = section.lastIndexOf(".", truncateAt);
        if (lastSentence > maxLength * 0.7) {
            truncateAt = lastSentence + 1;
        } else {
            // Try to break at word boundary
            int lastSpace = section.lastIndexOf(" ", truncateAt);
            if (lastSpace > maxLength * 0.5) {
                truncateAt = lastSpace;
            }
        }

        return section.substring(0, truncateAt) + "\n[...content truncated due to length...]\n";
    }

    /**
     * Capitalize first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Get prompt building statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("maxPromptLength", maxPromptLength);
        stats.put("enableTruncation", enableTruncation);
        stats.put("contextPriority", contextPriority);
        stats.put("includeMetadata", includeMetadata);
        stats.put("improvedFormatEnabled", improvedFormatEnabled);
        stats.put("chatmlFormatEnabled", chatmlFormatEnabled);
        return stats;
    }

    /**
     * Inner class representing a chat message for ChatML format.
     */
    private static class ChatMessage {
        private final String role;
        private final String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }

        @Override
        public String toString() {
            return "ChatMessage{" +
                "role='" + role + '\'' +
                ", content='" + content.substring(0, Math.min(50, content.length())) + "...'" +
                '}';
        }
    }

    /**
     * Inner class for managing prompt components.
     */
    private static class PromptComponents {
        private String systemContext = "";
        private String conversationHistory = "";
        private String chunkSummary = "";
        private String currentMessage = "";

        public void addSystemContext(String systemContext) {
            this.systemContext = systemContext;
        }

        public void addConversationHistory(String conversationHistory) {
            this.conversationHistory = conversationHistory;
        }

        public void addChunkSummary(String chunkSummary) {
            this.chunkSummary = chunkSummary;
        }

        public void addCurrentMessage(String currentMessage) {
            this.currentMessage = currentMessage;
        }

        // Getters
        public String getSystemContext() { return systemContext; }
        public String getConversationHistory() { return conversationHistory; }
        public String getChunkSummary() { return chunkSummary; }
        public String getCurrentMessage() { return currentMessage; }

        // Checkers
        public boolean hasConversationHistory() {
            return conversationHistory != null && !conversationHistory.trim().isEmpty();
        }

        public boolean hasChunkSummary() {
            return chunkSummary != null && !chunkSummary.trim().isEmpty();
        }
    }


}

