package com.agenttimeline.service;

import com.agenttimeline.model.Message;
import com.agenttimeline.model.OllamaResponse;
import com.agenttimeline.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimelineService {

    private final OllamaService ollamaService;
    private final MessageRepository messageRepository;
    private final MessageChainValidator chainValidator;

    /**
     * Process user message with message chaining
     * 1. Save user message with parent reference
     * 2. Get AI response
     * 3. Save assistant message with user message as parent
     * 4. Return the assistant message
     */
    public Mono<Message> processUserMessage(String userMessage, String sessionId) {
        // Capture user message timestamp immediately when it arrives
        LocalDateTime userMessageTimestamp = LocalDateTime.now();

        // Find the last message in the session to use as parent for the new user message
        String parentMessageId = findLastMessageIdInSession(sessionId);

        // Create and save user message with timestamp from when it arrived
        Message userMessageEntity = createUserMessage(userMessage, sessionId, parentMessageId, userMessageTimestamp);
        Message savedUserMessage = messageRepository.save(userMessageEntity);
        log.info("Saved user message with ID: {} at timestamp: {} (nano: {})",
            savedUserMessage.getId(), userMessageEntity.getTimestamp(), userMessageEntity.getTimestamp().toLocalTime().toNanoOfDay());

        return ollamaService.generateResponse(userMessage)
                .map(ollamaResponse -> {
                    // Capture assistant message timestamp when response arrives
                    LocalDateTime assistantMessageTimestamp = LocalDateTime.now();
                    long responseTime = java.time.Duration.between(userMessageTimestamp, assistantMessageTimestamp).toMillis();

                    // Create assistant message with timestamp from when response arrived
                    Message assistantMessage = createAssistantMessage(
                        ollamaResponse.getResponse(),
                        sessionId,
                        savedUserMessage.getId(),
                        ollamaResponse.getModel(),
                        responseTime,
                        assistantMessageTimestamp
                    );

                    Message savedAssistantMessage = messageRepository.save(assistantMessage);
                    log.info("Saved assistant message with ID: {} at timestamp: {} (nano: {}) (parent: {}, response time: {}ms)",
                        savedAssistantMessage.getId(), assistantMessage.getTimestamp(),
                        assistantMessage.getTimestamp().toLocalTime().toNanoOfDay(), savedUserMessage.getId(), responseTime);

                    return savedAssistantMessage;
                });
    }

    /**
     * Get conversation history by reconstructing message chain with validation
     */
    public List<Message> getConversationHistory(String sessionId) {
        try {
            // First, validate the message chain
            MessageChainValidator.ChainValidationResult validation = chainValidator.validateChain(sessionId);

            if (!validation.isValid()) {
                log.warn("Message chain validation failed for session {}: {}", sessionId, validation.getErrorMessage());

                // Attempt automatic repair for common issues
                if (!validation.getBrokenReferences().isEmpty()) {
                    log.info("Attempting automatic chain repair for session: {}", sessionId);
                    MessageChainValidator.ChainRepairResult repair = chainValidator.repairChain(sessionId);
                    if (repair.isSuccess()) {
                        log.info("Chain repair successful for session: {}", sessionId);
                    } else {
                        log.warn("Chain repair failed for session: {}", sessionId);
                    }
                }
            }

            List<Message> conversation = new ArrayList<>();
            Set<String> processedMessageIds = new HashSet<>();

            // Find all messages for the session
            List<Message> sessionMessages = messageRepository.findBySessionId(sessionId);

            // Find messages with no parent (starting points)
            List<Message> rootMessages = sessionMessages.stream()
                .filter(Message::isFirstMessage)
                .sorted(Comparator.comparing(Message::getTimestamp))
                .collect(ArrayList::new, (list, msg) -> list.add(msg), ArrayList::addAll);

            // Reconstruct conversation by following message chains
            if (rootMessages.isEmpty()) {
                log.warn("No root messages found in session {}", sessionId);
                return conversation;
            }

            if (rootMessages.size() == 1) {
                // Normal case: single root message
                reconstructConversationChain(rootMessages.get(0), sessionMessages, conversation, processedMessageIds);
            } else if (rootMessages.size() > 1) {
                log.warn("Multiple root messages found in session {}: {}. Attempting to reconstruct with primary root.",
                    sessionId, rootMessages.size());

                // Sort root messages and use the oldest one as the primary root
                rootMessages.sort(Comparator.comparing(Message::getTimestamp));
                Message primaryRoot = rootMessages.get(0);

                log.info("Using message {} as primary root for session {}", primaryRoot.getId(), sessionId);
                reconstructConversationChain(primaryRoot, sessionMessages, conversation, processedMessageIds);

                // Process any remaining unprocessed messages that might be disconnected
                for (Message message : sessionMessages) {
                    if (!processedMessageIds.contains(message.getId())) {
                        log.warn("Found unprocessed message {} in session {} - this indicates a chain break",
                            message.getId(), sessionId);
                        // Still add it to maintain data completeness, but log the issue
                        conversation.add(message);
                        processedMessageIds.add(message.getId());
                    }
                }
            }

            log.info("Reconstructed conversation for session {}: {} messages", sessionId, conversation.size());
            return conversation;
        } catch (Exception e) {
            log.error("Error reconstructing conversation for session: {}", sessionId, e);
            // Fallback to timestamp-based sorting if chain reconstruction fails
            log.info("Falling back to timestamp-based sorting for session: {}", sessionId);
            return getSessionMessages(sessionId);
        }
    }

    /**
     * Get all messages for a session (legacy method for backward compatibility)
     */
    public List<Message> getSessionMessages(String sessionId) {
        List<Message> messages = messageRepository.findBySessionId(sessionId);
        log.debug("Raw messages for session {}: {}", sessionId,
            messages.stream()
                .map(msg -> String.format("%s:%s@%s", msg.getRole(), msg.getId().substring(0, 8), msg.getTimestamp()))
                .toList());

        List<Message> sortedMessages = messages.stream()
            .sorted(Comparator.comparing(Message::getTimestamp).reversed())
            .collect(ArrayList::new, (list, msg) -> list.add(msg), ArrayList::addAll);

        log.debug("Timestamp-sorted messages for session {}: {}", sessionId,
            sortedMessages.stream()
                .map(msg -> String.format("%s:%s@%s", msg.getRole(), msg.getId().substring(0, 8), msg.getTimestamp()))
                .toList());

        return sortedMessages;
    }

    /**
     * Get all messages across all sessions
     */
    public List<Message> getAllMessages() {
        List<Message> allMessages = new ArrayList<>();
        messageRepository.findAll().forEach(allMessages::add);

        return allMessages.stream()
            .sorted(Comparator.comparing(Message::getTimestamp).reversed())
            .collect(ArrayList::new, (list, msg) -> list.add(msg), ArrayList::addAll);
    }

    // Private helper methods

    private String findLastMessageIdInSession(String sessionId) {
        List<Message> sessionMessages = messageRepository.findBySessionId(sessionId);
        if (sessionMessages.isEmpty()) {
            return null;
        }

        // Find the most recent message by timestamp
        // Use a more robust comparison to handle potential timestamp precision issues
        return sessionMessages.stream()
            .max((msg1, msg2) -> {
                int timestampCompare = msg1.getTimestamp().compareTo(msg2.getTimestamp());
                if (timestampCompare != 0) {
                    return timestampCompare;
                }
                // If timestamps are identical, prefer messages with parents (more recent in chain)
                boolean msg1HasParent = msg1.getParentMessageId() != null;
                boolean msg2HasParent = msg2.getParentMessageId() != null;
                if (msg1HasParent && !msg2HasParent) {
                    return 1; // msg1 is more recent
                } else if (!msg1HasParent && msg2HasParent) {
                    return -1; // msg2 is more recent
                }
                // If both have same parent status, use ID as tiebreaker
                return msg1.getId().compareTo(msg2.getId());
            })
            .map(Message::getId)
            .orElse(null);
    }

    /**
     * Find the most recent processed message that could be a parent for chain repair
     */
    private String findMostRecentProcessedMessage(List<Message> processedMessages, LocalDateTime beforeTimestamp) {
        if (processedMessages.isEmpty()) {
            return null;
        }

        // Find messages processed before the given timestamp
        return processedMessages.stream()
            .filter(msg -> msg.getTimestamp().isBefore(beforeTimestamp) ||
                          msg.getTimestamp().equals(beforeTimestamp))
            .max(Comparator.comparing(Message::getTimestamp))
            .map(Message::getId)
            .orElse(null);
    }

    private Message createUserMessage(String content, String sessionId, String parentMessageId, LocalDateTime timestamp) {
        Message message = new Message();
        message.setId(UUID.randomUUID().toString());
        message.setSessionId(sessionId);
        message.setRole(Message.Role.USER);
        message.setContent(content);
        message.setTimestamp(timestamp);
        message.setParentMessageId(parentMessageId);

        // Add metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("messageType", "user_input");
        message.setMetadata(metadata);

        return message;
    }

    private Message createAssistantMessage(String content, String sessionId, String parentMessageId,
                                         String modelUsed, long responseTime, LocalDateTime timestamp) {
        Message message = new Message();
        message.setId(UUID.randomUUID().toString());
        message.setSessionId(sessionId);
        message.setRole(Message.Role.ASSISTANT);
        message.setContent(content);
        message.setTimestamp(timestamp);
        message.setParentMessageId(parentMessageId);

        // Add metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("messageType", "assistant_response");
        metadata.put("model", modelUsed);
        metadata.put("responseTimeMs", responseTime);
        message.setMetadata(metadata);

        return message;
    }

    private void reconstructConversationChain(Message currentMessage, List<Message> allSessionMessages,
                                           List<Message> conversation, Set<String> processedMessageIds) {
        if (currentMessage == null || processedMessageIds.contains(currentMessage.getId())) {
            return;
        }

        // Add current message to conversation
        conversation.add(currentMessage);
        processedMessageIds.add(currentMessage.getId());

        // Find child messages (messages that have this message as parent)
        List<Message> childMessages = allSessionMessages.stream()
            .filter(msg -> currentMessage.getId().equals(msg.getParentMessageId()))
            .sorted(Comparator.comparing(Message::getTimestamp))
            .collect(ArrayList::new, (list, msg) -> list.add(msg), ArrayList::addAll);

        // Recursively add child messages
        for (Message childMessage : childMessages) {
            reconstructConversationChain(childMessage, allSessionMessages, conversation, processedMessageIds);
        }
    }

    /**
     * Validate message chain for a session
     */
    public MessageChainValidator.ChainValidationResult validateMessageChain(String sessionId) {
        return chainValidator.validateChain(sessionId);
    }

    /**
     * Repair broken message chains for a session
     */
    public MessageChainValidator.ChainRepairResult repairMessageChain(String sessionId) {
        return chainValidator.repairChain(sessionId);
    }

    /**
     * TEST METHOD: Create broken chains for testing repair functionality
     * This method is for testing purposes only
     */
    public String createBrokenChainForTesting(String sessionId, String breakType) {
        try {
            switch (breakType.toLowerCase()) {
                case "orphaned":
                    return createOrphanedMessageScenario(sessionId);
                case "broken-reference":
                    return createBrokenReferenceScenario(sessionId);
                case "multiple-roots":
                    return createMultipleRootsScenario(sessionId);
                default:
                    return createOrphanedMessageScenario(sessionId);
            }
        } catch (Exception e) {
            log.error("Error creating broken chain for testing: {}", e.getMessage());
            throw new RuntimeException("Failed to create broken chain: " + e.getMessage());
        }
    }

    /**
     * Create orphaned messages (messages not reachable from any root)
     */
    private String createOrphanedMessageScenario(String sessionId) {
        // First create a normal conversation
        Message rootMessage = createUserMessage("Root message for orphaned test", sessionId, null, LocalDateTime.now().minusMinutes(5));
        messageRepository.save(rootMessage);

        Message childMessage = createAssistantMessage("Response to root", sessionId, rootMessage.getId(), "test-model", 100, LocalDateTime.now().minusMinutes(4));
        messageRepository.save(childMessage);

        // Create an orphaned message (no parent, not connected to the main chain)
        Message orphanedMessage = createUserMessage("This is an orphaned message", sessionId, null, LocalDateTime.now().minusMinutes(2));
        messageRepository.save(orphanedMessage);

        // Create another orphaned message with a fake parent ID
        Message anotherOrphaned = createAssistantMessage("Another orphaned message", sessionId, "non-existent-parent-id", "test-model", 50, LocalDateTime.now().minusMinutes(1));
        messageRepository.save(anotherOrphaned);

        return "Created orphaned message scenario: 2 valid messages + 2 orphaned messages";
    }

    /**
     * Create broken parent reference scenario
     */
    private String createBrokenReferenceScenario(String sessionId) {
        // Create a normal message first
        Message validMessage = createUserMessage("Valid message", sessionId, null, LocalDateTime.now().minusMinutes(3));
        messageRepository.save(validMessage);

        // Create a message with a broken parent reference (references non-existent message)
        Message brokenRefMessage = createAssistantMessage("Message with broken parent", sessionId, "fake-parent-id-12345", "test-model", 75, LocalDateTime.now().minusMinutes(2));
        messageRepository.save(brokenRefMessage);

        // Create another broken reference
        Message anotherBroken = createUserMessage("Another broken reference", sessionId, "non-existent-parent-67890", LocalDateTime.now().minusMinutes(1));
        messageRepository.save(anotherBroken);

        return "Created broken reference scenario: 1 valid message + 2 messages with broken parent references";
    }

    /**
     * Create multiple root messages scenario
     */
    private String createMultipleRootsScenario(String sessionId) {
        // Create multiple root messages (messages without parents)
        Message root1 = createUserMessage("First conversation root", sessionId, null, LocalDateTime.now().minusMinutes(4));
        messageRepository.save(root1);

        Message root2 = createUserMessage("Second conversation root", sessionId, null, LocalDateTime.now().minusMinutes(3));
        messageRepository.save(root2);

        Message root3 = createAssistantMessage("Third conversation root", sessionId, null, "test-model", 80, LocalDateTime.now().minusMinutes(2));
        messageRepository.save(root3);

        // Add some messages to one of the roots to create a partial chain
        Message childOfRoot1 = createAssistantMessage("Response to first root", sessionId, root1.getId(), "test-model", 90, LocalDateTime.now().minusMinutes(1));
        messageRepository.save(childOfRoot1);

        return "Created multiple roots scenario: 3 root messages + 1 connected message (2 orphaned roots)";
    }

    /**
     * Get chain validation statistics across all sessions
     */
    public Map<String, Object> getChainValidationStatistics() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // Get all sessions by finding unique sessionIds from messages
            Set<String> sessionIds = new HashSet<>();
            messageRepository.findAll().forEach(message -> {
                if (message != null && message.getSessionId() != null) {
                    sessionIds.add(message.getSessionId());
                }
            });

            int totalSessions = sessionIds.size();
            int validChains = 0;
            int invalidChains = 0;
            int totalMessages = 0;
            List<Map<String, Object>> sessionStats = new ArrayList<>();

            for (String sessionId : sessionIds) {
                try {
                    MessageChainValidator.ChainValidationResult validation = chainValidator.validateChain(sessionId);
                    List<Message> sessionMessages = messageRepository.findBySessionId(sessionId);

                    // Filter out null messages
                    sessionMessages = sessionMessages.stream()
                        .filter(message -> message != null)
                        .collect(ArrayList::new, (list, msg) -> list.add(msg), ArrayList::addAll);

                    totalMessages += sessionMessages.size();

                    Map<String, Object> sessionStat = new HashMap<>();
                    sessionStat.put("sessionId", sessionId);
                    sessionStat.put("valid", validation.isValid());
                    sessionStat.put("messageCount", sessionMessages.size());
                    sessionStat.put("brokenReferences", validation.getBrokenReferences() != null ? validation.getBrokenReferences().size() : 0);
                    sessionStat.put("orphanMessages", validation.getOrphanMessages() != null ? validation.getOrphanMessages().size() : 0);

                    sessionStats.add(sessionStat);

                    if (validation.isValid()) {
                        validChains++;
                    } else {
                        invalidChains++;
                    }
                } catch (Exception e) {
                    log.warn("Error processing session {}: {}", sessionId, e.getMessage());
                    // Continue with other sessions even if one fails
                }
            }

            stats.put("totalSessions", totalSessions);
            stats.put("validChains", validChains);
            stats.put("invalidChains", invalidChains);
            stats.put("totalMessages", totalMessages);
            stats.put("validationRate", totalSessions > 0 ? (double) validChains / totalSessions * 100 : 0);
            stats.put("sessionStats", sessionStats);

            return stats;
        } catch (Exception e) {
            log.error("Error generating chain validation statistics", e);
            Map<String, Object> errorStats = new HashMap<>();
            errorStats.put("error", "Failed to generate statistics: " + e.getMessage());
            return errorStats;
        }
    }
}
