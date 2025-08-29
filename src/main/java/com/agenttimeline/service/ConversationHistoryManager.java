package com.agenttimeline.service;

import com.agenttimeline.model.Message;
import com.agenttimeline.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages conversation history with rolling windows and intelligent summarization.
 *
 * This service is a core component of Phase 6: Enhanced Context Management.
 * It provides:
 * - Rolling conversation windows of configurable size
 * - Automatic summarization when windows exceed limits
 * - Memory-efficient storage of conversation state
 * - Integration with key information extraction
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationHistoryManager {

    private final MessageRepository messageRepository;
    private final ConversationSummaryService summaryService;

    // Configuration parameters
    @Value("${conversation.history.window.size:10}")
    private int maxWindowSize;

    @Value("${conversation.history.max-summary-length:1000}")
    private int maxSummaryLength;

    @Value("${conversation.history.retention.max-age-hours:24}")
    private int maxRetentionHours;

    @Value("${conversation.history.cleanup-interval-minutes:60}")
    private int cleanupIntervalMinutes;

    // In-memory storage for conversation windows
    private final Map<String, ConversationWindow> conversationWindows = new ConcurrentHashMap<>();

    // Last cleanup timestamp
    private volatile LocalDateTime lastCleanup = LocalDateTime.now();

    /**
     * Add a message to the conversation window for a session.
     *
     * @param sessionId The session ID
     * @param message The message to add
     */
    public void addMessage(String sessionId, Message message) {
        log.debug("Adding message {} to conversation window for session {}", message.getId(), sessionId);

        ConversationWindow window = conversationWindows.computeIfAbsent(sessionId, k -> new ConversationWindow());

        synchronized (window) {
            window.addMessage(message);

            // Check if we need to summarize
            if (window.getMessages().size() > maxWindowSize) {
                summarizeWindow(sessionId, window);
            }

            // Periodic cleanup
            performPeriodicCleanup();
        }

        log.debug("Added message to window. Session {} now has {} messages and summary length: {}",
            sessionId, window.getMessages().size(),
            window.getSummary() != null ? window.getSummary().length() : 0);
    }

    /**
     * Get the current conversation context for a session.
     * Returns either the rolling window or the summary if available.
     *
     * @param sessionId The session ID
     * @return ConversationContext containing messages and/or summary
     */
    public ConversationContext getConversationContext(String sessionId) {
        ConversationWindow window = conversationWindows.get(sessionId);
        if (window == null) {
            log.debug("No conversation window found for session {}", sessionId);
            return new ConversationContext(List.of(), null);
        }

        synchronized (window) {
            List<Message> messages = new ArrayList<>(window.getMessages());
            String summary = window.getSummary();

            log.debug("Retrieved conversation context for session {}: {} messages, summary length: {}",
                sessionId, messages.size(), summary != null ? summary.length() : 0);

            return new ConversationContext(messages, summary);
        }
    }

    /**
     * Get the most recent N messages from the conversation window.
     *
     * @param sessionId The session ID
     * @param count Number of recent messages to retrieve
     * @return List of recent messages (most recent first)
     */
    public List<Message> getRecentMessages(String sessionId, int count) {
        ConversationWindow window = conversationWindows.get(sessionId);
        if (window == null) {
            return List.of();
        }

        synchronized (window) {
            List<Message> messages = window.getMessages();
            int startIndex = Math.max(0, messages.size() - count);
            return messages.subList(startIndex, messages.size());
        }
    }

    /**
     * Summarize the conversation window when it exceeds the maximum size.
     */
    private void summarizeWindow(String sessionId, ConversationWindow window) {
        try {
            log.info("Summarizing conversation window for session {} ({} messages)",
                sessionId, window.getMessages().size());

            // Get all messages for summarization
            List<Message> messages = window.getMessages();

            // Generate summary using the summary service
            String newSummary = summaryService.generateSummary(messages, sessionId);

            // Update window with summary
            window.setSummary(newSummary);

            // Keep only the most recent messages to maintain some immediate context
            int keepCount = Math.max(3, maxWindowSize / 2); // Keep at least 3 or half the window size
            window.keepRecentMessages(keepCount);

            log.info("Summarized window for session {}. New summary length: {}, kept {} messages",
                sessionId, newSummary.length(), window.getMessages().size());

        } catch (Exception e) {
            log.error("Error summarizing conversation window for session {}: {}", sessionId, e.getMessage(), e);
            // On error, just keep the most recent messages without summarization
            window.keepRecentMessages(maxWindowSize);
        }
    }

    /**
     * Perform periodic cleanup of old conversation windows.
     */
    private void performPeriodicCleanup() {
        LocalDateTime now = LocalDateTime.now();
        if (lastCleanup.plusMinutes(cleanupIntervalMinutes).isAfter(now)) {
            return; // Not time for cleanup yet
        }

        log.debug("Performing periodic cleanup of conversation windows");

        LocalDateTime cutoffTime = now.minusHours(maxRetentionHours);
        List<String> sessionsToRemove = new ArrayList<>();

        for (Map.Entry<String, ConversationWindow> entry : conversationWindows.entrySet()) {
            ConversationWindow window = entry.getValue();
            synchronized (window) {
                if (window.getLastActivity().isBefore(cutoffTime)) {
                    sessionsToRemove.add(entry.getKey());
                }
            }
        }

        for (String sessionId : sessionsToRemove) {
            conversationWindows.remove(sessionId);
            log.debug("Removed old conversation window for session {}", sessionId);
        }

        lastCleanup = now;

        if (!sessionsToRemove.isEmpty()) {
            log.info("Cleaned up {} old conversation windows", sessionsToRemove.size());
        }
    }

    /**
     * Clear conversation history for a session.
     *
     * @param sessionId The session ID
     */
    public void clearHistory(String sessionId) {
        conversationWindows.remove(sessionId);
        log.info("Cleared conversation history for session {}", sessionId);
    }

    /**
     * Clear all conversation history for all sessions.
     *
     * @return Number of sessions cleared
     */
    public int clearAllHistory() {
        int sessionsCleared = conversationWindows.size();
        conversationWindows.clear();
        log.info("Cleared conversation history for all {} sessions", sessionsCleared);
        return sessionsCleared;
    }

    /**
     * Get statistics about conversation windows.
     *
     * @return Map containing statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSessions", conversationWindows.size());
        stats.put("maxWindowSize", maxWindowSize);
        stats.put("maxSummaryLength", maxSummaryLength);
        stats.put("maxRetentionHours", maxRetentionHours);

        int totalMessages = 0;
        int totalSummaries = 0;
        int maxMessagesInWindow = 0;

        for (ConversationWindow window : conversationWindows.values()) {
            synchronized (window) {
                totalMessages += window.getMessages().size();
                if (window.getSummary() != null) {
                    totalSummaries++;
                }
                maxMessagesInWindow = Math.max(maxMessagesInWindow, window.getMessages().size());
            }
        }

        stats.put("totalMessages", totalMessages);
        stats.put("totalSummaries", totalSummaries);
        stats.put("maxMessagesInWindow", maxMessagesInWindow);
        stats.put("averageMessagesPerWindow", conversationWindows.isEmpty() ? 0 :
            (double) totalMessages / conversationWindows.size());

        return stats;
    }

    /**
     * Inner class representing a conversation window with messages and optional summary.
     */
    private static class ConversationWindow {
        private final List<Message> messages = new ArrayList<>();
        private String summary;
        private LocalDateTime lastActivity = LocalDateTime.now();

        public synchronized void addMessage(Message message) {
            messages.add(message);
            lastActivity = LocalDateTime.now();
        }

        public synchronized List<Message> getMessages() {
            return new ArrayList<>(messages);
        }

        public synchronized String getSummary() {
            return summary;
        }

        public synchronized void setSummary(String summary) {
            this.summary = summary;
            lastActivity = LocalDateTime.now();
        }

        public synchronized LocalDateTime getLastActivity() {
            return lastActivity;
        }

        /**
         * Keep only the most recent N messages in the window.
         */
        public synchronized void keepRecentMessages(int count) {
            if (messages.size() > count) {
                // Keep the most recent messages
                int startIndex = messages.size() - count;
                List<Message> recentMessages = new ArrayList<>(messages.subList(startIndex, messages.size()));
                messages.clear();
                messages.addAll(recentMessages);
                lastActivity = LocalDateTime.now();
            }
        }
    }

    /**
     * Data class representing conversation context.
     */
    public static class ConversationContext {
        private final List<Message> recentMessages;
        private final String summary;

        public ConversationContext(List<Message> recentMessages, String summary) {
            this.recentMessages = recentMessages != null ? new ArrayList<>(recentMessages) : List.of();
            this.summary = summary;
        }

        public List<Message> getRecentMessages() {
            return new ArrayList<>(recentMessages);
        }

        public String getSummary() {
            return summary;
        }

        public boolean hasSummary() {
            return summary != null && !summary.trim().isEmpty();
        }

        public boolean hasRecentMessages() {
            return !recentMessages.isEmpty();
        }

        @Override
        public String toString() {
            return "ConversationContext{" +
                "recentMessages=" + recentMessages.size() +
                ", hasSummary=" + hasSummary() +
                '}';
        }
    }
}
