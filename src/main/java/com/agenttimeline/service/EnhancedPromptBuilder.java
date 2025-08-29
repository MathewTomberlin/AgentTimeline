package com.agenttimeline.service;

import com.agenttimeline.model.Message;
import com.agenttimeline.model.MessageChunkEmbedding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    // Configuration parameters
    @Value("${prompt.max.length:4000}")
    private int maxPromptLength;

    @Value("${prompt.enable.truncation:true}")
    private boolean enableTruncation;

    @Value("${prompt.context.priority:balanced}")
    private String contextPriority;

    @Value("${prompt.include.metadata:true}")
    private boolean includeMetadata;

    // Context source weights for prioritization
    private static final double CONVERSATION_HISTORY_WEIGHT = 0.4;
    private static final double KEY_INFORMATION_WEIGHT = 0.3;
    private static final double HISTORICAL_CHUNKS_WEIGHT = 0.2;
    private static final double CURRENT_MESSAGE_WEIGHT = 0.1;

    /**
     * Build an enhanced prompt from multiple context sources.
     *
     * @param userMessage The current user message
     * @param conversationContext Immediate conversation context
     * @param keyInformation Extracted key information
     * @param relevantChunks Historical chunks from vector search
     * @param sessionId Session ID for logging
     * @return Enhanced prompt string
     */
    public String buildEnhancedPrompt(String userMessage,
                                    ConversationHistoryManager.ConversationContext conversationContext,
                                    KeyInformationExtractor.ExtractedInformation keyInformation,
                                    List<ConfigurableContextRetrievalService.ExpandedChunkGroup> relevantChunks,
                                    String sessionId) {

        log.debug("Building enhanced prompt for session {} with context sources: conversation={}, keyInfo={}, chunks={}",
            sessionId,
            conversationContext != null ? "present" : "null",
            keyInformation != null ? "present" : "null",
            relevantChunks != null ? relevantChunks.size() : 0);

        PromptComponents components = new PromptComponents();

        // Add system context
        components.addSystemContext(buildSystemContext());

        // Add conversation history/summary
        if (conversationContext != null && !conversationContext.getRecentMessages().isEmpty()) {
            components.addConversationHistory(buildConversationHistorySection(conversationContext));
        }

        // Add key information
        if (keyInformation != null && !keyInformation.isEmpty()) {
            components.addKeyInformation(buildKeyInformationSection(keyInformation));
        }

        // Add historical context
        if (relevantChunks != null && !relevantChunks.isEmpty()) {
            components.addHistoricalContext(buildHistoricalContextSection(relevantChunks));
        }

        // Add current message
        components.addCurrentMessage(buildCurrentMessageSection(userMessage));

        // Build final prompt with intelligent prioritization
        String finalPrompt = buildFinalPrompt(components, sessionId);

        log.debug("Built enhanced prompt for session {}: {} characters", sessionId, finalPrompt.length());

        return finalPrompt;
    }

    /**
     * Build system context section.
     */
    private String buildSystemContext() {
        return "You are an intelligent AI assistant engaged in a conversation. " +
               "Use the provided context to give accurate, helpful, and contextually appropriate responses. " +
               "Reference previous conversation elements when relevant, but don't force connections that aren't natural.";
    }

    /**
     * Build conversation history section.
     */
    private String buildConversationHistorySection(ConversationHistoryManager.ConversationContext context) {
        StringBuilder section = new StringBuilder();
        section.append("## Recent Conversation:\n");

        // Add summary if available
        if (context.hasSummary()) {
            section.append("**Summary:** ").append(context.getSummary()).append("\n\n");
        }

        // Add recent messages
        if (context.hasRecentMessages()) {
            section.append("**Recent Messages:**\n");
            for (Message message : context.getRecentMessages()) {
                String role = message.getRole().toString().toLowerCase();
                String content = message.getContent();
                section.append("- ").append(capitalize(role)).append(": ").append(content).append("\n");
            }
            section.append("\n");
        }

        return section.toString();
    }

    /**
     * Build key information section.
     */
    private String buildKeyInformationSection(KeyInformationExtractor.ExtractedInformation info) {
        StringBuilder section = new StringBuilder();
        section.append("## Key Information:\n");

        // Add entities
        if (!info.getEntities().isEmpty()) {
            section.append("**Important Entities:** ");
            section.append(String.join(", ", info.getEntities()));
            section.append("\n");
        }

        // Add key facts
        if (!info.getKeyFacts().isEmpty()) {
            section.append("**Key Facts:**\n");
            for (String fact : info.getKeyFacts()) {
                section.append("- ").append(fact).append("\n");
            }
            section.append("\n");
        }

        // Add user intent (if applicable)
        if (info.getUserIntent() != null) {
            section.append("**User Intent:** ").append(info.getUserIntent()).append("\n\n");
        }

        // Add action items
        if (!info.getActionItems().isEmpty()) {
            section.append("**Action Items:**\n");
            for (String action : info.getActionItems()) {
                section.append("- ").append(action).append("\n");
            }
            section.append("\n");
        }

        // Add contextual information
        if (info.getContextualInfo() != null) {
            section.append("**Context:** ").append(info.getContextualInfo()).append("\n\n");
        }

        return section.toString();
    }

    /**
     * Build historical context section from retrieved chunks.
     */
    private String buildHistoricalContextSection(List<ConfigurableContextRetrievalService.ExpandedChunkGroup> chunks) {
        StringBuilder section = new StringBuilder();
        section.append("## Relevant Historical Context:\n");

        for (ConfigurableContextRetrievalService.ExpandedChunkGroup group : chunks) {
            section.append("**Context from previous conversation:**\n");
            section.append("\"").append(group.getCombinedText()).append("\"\n\n");
        }

        return section.toString();
    }

    /**
     * Build current message section.
     */
    private String buildCurrentMessageSection(String userMessage) {
        return "## Current Message:\n" + userMessage;
    }

    /**
     * Build final prompt with intelligent prioritization and length management.
     */
    private String buildFinalPrompt(PromptComponents components, String sessionId) {
        StringBuilder prompt = new StringBuilder();

        // Add system context (always included)
        prompt.append(components.getSystemContext()).append("\n\n");

        // Prioritize and add context sections based on strategy
        List<ContextSection> prioritizedSections = prioritizeContextSections(components);

        // Calculate available space for context
        int systemLength = components.getSystemContext().length();
        int currentMessageLength = components.getCurrentMessage().length();
        int overhead = systemLength + currentMessageLength + 500; // Buffer for formatting
        int availableContextSpace = maxPromptLength - overhead;

        // Add context sections with intelligent truncation
        String contextContent = buildContextContent(prioritizedSections, availableContextSpace, sessionId);
        if (!contextContent.isEmpty()) {
            prompt.append(contextContent).append("\n");
        }

        // Add current message
        prompt.append(components.getCurrentMessage());

        // Final length check and truncation if needed
        String finalPrompt = prompt.toString();
        if (enableTruncation && finalPrompt.length() > maxPromptLength) {
            finalPrompt = applyFinalTruncation(finalPrompt, sessionId);
        }

        return finalPrompt;
    }

    /**
     * Prioritize context sections based on strategy and content quality.
     */
    private List<ContextSection> prioritizeContextSections(PromptComponents components) {
        List<ContextSection> sections = new ArrayList<>();

        // Add sections in priority order
        if (components.hasConversationHistory()) {
            sections.add(new ContextSection("conversation",
                components.getConversationHistory(),
                CONVERSATION_HISTORY_WEIGHT));
        }

        if (components.hasKeyInformation()) {
            sections.add(new ContextSection("keyInfo",
                components.getKeyInformation(),
                KEY_INFORMATION_WEIGHT));
        }

        if (components.hasHistoricalContext()) {
            sections.add(new ContextSection("historical",
                components.getHistoricalContext(),
                HISTORICAL_CHUNKS_WEIGHT));
        }

        // Sort by priority (higher weight first)
        sections.sort((a, b) -> Double.compare(b.getPriority(), a.getPriority()));

        return sections;
    }

    /**
     * Build context content with intelligent space allocation.
     */
    private String buildContextContent(List<ContextSection> sections, int availableSpace, String sessionId) {
        StringBuilder context = new StringBuilder();
        int remainingSpace = availableSpace;

        for (ContextSection section : sections) {
            String sectionContent = section.getContent();

            // Check if we have space for this section
            if (sectionContent.length() <= remainingSpace) {
                // Add full section
                context.append(sectionContent);
                remainingSpace -= sectionContent.length();
                log.debug("Added full {} section ({} chars) for session {}",
                    section.getType(), sectionContent.length(), sessionId);
            } else if (remainingSpace > 100) { // Only add if we have meaningful space
                // Add truncated section
                String truncatedSection = truncateSection(sectionContent, remainingSpace);
                context.append(truncatedSection);
                log.debug("Added truncated {} section ({} -> {} chars) for session {}",
                    section.getType(), sectionContent.length(), truncatedSection.length(), sessionId);
                break; // No more space for additional sections
            } else {
                log.debug("Skipping {} section due to insufficient space ({} chars available) for session {}",
                    section.getType(), remainingSpace, sessionId);
                break;
            }
        }

        return context.toString();
    }

    /**
     * Truncate a section intelligently.
     */
    private String truncateSection(String content, int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }

        // Try to truncate at a natural break point
        int truncationPoint = findNaturalBreakPoint(content, maxLength - 50); // Leave space for "..."

        String truncated = content.substring(0, truncationPoint);
        return truncated + "\n[...content truncated due to length...]\n";
    }

    /**
     * Find a natural break point in the content.
     */
    private int findNaturalBreakPoint(String content, int targetLength) {
        if (targetLength >= content.length()) {
            return content.length();
        }

        // Look for section breaks first
        for (int i = targetLength; i > targetLength - 200 && i > 0; i--) {
            if (i < content.length() - 2 && content.substring(i, i + 2).equals("\n\n")) {
                return i;
            }
        }

        // Look for sentence endings
        for (int i = targetLength; i > targetLength - 100 && i > 0; i--) {
            char c = content.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                return i + 1;
            }
        }

        // Look for word boundaries
        for (int i = targetLength; i > targetLength - 50 && i > 0; i--) {
            if (Character.isWhitespace(content.charAt(i))) {
                return i;
            }
        }

        // Fallback to character-based truncation
        return targetLength;
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
        return stats;
    }

    /**
     * Inner class for managing prompt components.
     */
    private static class PromptComponents {
        private String systemContext = "";
        private String conversationHistory = "";
        private String keyInformation = "";
        private String historicalContext = "";
        private String currentMessage = "";

        public void addSystemContext(String systemContext) {
            this.systemContext = systemContext;
        }

        public void addConversationHistory(String conversationHistory) {
            this.conversationHistory = conversationHistory;
        }

        public void addKeyInformation(String keyInformation) {
            this.keyInformation = keyInformation;
        }

        public void addHistoricalContext(String historicalContext) {
            this.historicalContext = historicalContext;
        }

        public void addCurrentMessage(String currentMessage) {
            this.currentMessage = currentMessage;
        }

        // Getters
        public String getSystemContext() { return systemContext; }
        public String getConversationHistory() { return conversationHistory; }
        public String getKeyInformation() { return keyInformation; }
        public String getHistoricalContext() { return historicalContext; }
        public String getCurrentMessage() { return currentMessage; }

        // Checkers
        public boolean hasConversationHistory() {
            return conversationHistory != null && !conversationHistory.trim().isEmpty();
        }

        public boolean hasKeyInformation() {
            return keyInformation != null && !keyInformation.trim().isEmpty();
        }

        public boolean hasHistoricalContext() {
            return historicalContext != null && !historicalContext.trim().isEmpty();
        }
    }

    /**
     * Inner class for context sections with priority.
     */
    private static class ContextSection {
        private final String type;
        private final String content;
        private final double priority;

        public ContextSection(String type, String content, double priority) {
            this.type = type;
            this.content = content;
            this.priority = priority;
        }

        public String getType() { return type; }
        public String getContent() { return content; }
        public double getPriority() { return priority; }
    }
}
