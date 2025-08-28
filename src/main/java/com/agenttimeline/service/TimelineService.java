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
        LocalDateTime startTime = LocalDateTime.now();

        // Find the last message in the session to use as parent for the new user message
        String parentMessageId = findLastMessageIdInSession(sessionId);

        // Create and save user message
        Message userMessageEntity = createUserMessage(userMessage, sessionId, parentMessageId);
        Message savedUserMessage = messageRepository.save(userMessageEntity);
        log.info("Saved user message with ID: {}", savedUserMessage.getId());

        return ollamaService.generateResponse(userMessage)
                .map(ollamaResponse -> {
                    LocalDateTime endTime = LocalDateTime.now();
                    long responseTime = java.time.Duration.between(startTime, endTime).toMillis();

                    // Create assistant message with user message as parent
                    Message assistantMessage = createAssistantMessage(
                        ollamaResponse.getResponse(),
                        sessionId,
                        savedUserMessage.getId(),
                        ollamaResponse.getModel(),
                        responseTime
                    );

                    Message savedAssistantMessage = messageRepository.save(assistantMessage);
                    log.info("Saved assistant message with ID: {} (parent: {})",
                        savedAssistantMessage.getId(), savedUserMessage.getId());

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
            for (Message rootMessage : rootMessages) {
                reconstructConversationChain(rootMessage, sessionMessages, conversation, processedMessageIds);
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
        return messageRepository.findBySessionId(sessionId).stream()
            .sorted(Comparator.comparing(Message::getTimestamp).reversed())
            .collect(ArrayList::new, (list, msg) -> list.add(msg), ArrayList::addAll);
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
        return sessionMessages.stream()
            .max(Comparator.comparing(Message::getTimestamp))
            .map(Message::getId)
            .orElse(null);
    }

    private Message createUserMessage(String content, String sessionId, String parentMessageId) {
        Message message = new Message();
        message.setId(UUID.randomUUID().toString());
        message.setSessionId(sessionId);
        message.setRole(Message.Role.USER);
        message.setContent(content);
        message.setTimestamp(LocalDateTime.now());
        message.setParentMessageId(parentMessageId);

        // Add metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("messageType", "user_input");
        message.setMetadata(metadata);

        return message;
    }

    private Message createAssistantMessage(String content, String sessionId, String parentMessageId,
                                         String modelUsed, long responseTime) {
        Message message = new Message();
        message.setId(UUID.randomUUID().toString());
        message.setSessionId(sessionId);
        message.setRole(Message.Role.ASSISTANT);
        message.setContent(content);
        message.setTimestamp(LocalDateTime.now());
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
     * Get chain validation statistics across all sessions
     */
    public Map<String, Object> getChainValidationStatistics() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // Get all sessions by finding unique sessionIds from messages
            Set<String> sessionIds = new HashSet<>();
            messageRepository.findAll().forEach(message -> {
                if (message.getSessionId() != null) {
                    sessionIds.add(message.getSessionId());
                }
            });

            int totalSessions = sessionIds.size();
            int validChains = 0;
            int invalidChains = 0;
            int totalMessages = 0;
            List<Map<String, Object>> sessionStats = new ArrayList<>();

            for (String sessionId : sessionIds) {
                MessageChainValidator.ChainValidationResult validation = chainValidator.validateChain(sessionId);
                List<Message> sessionMessages = messageRepository.findBySessionId(sessionId);

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
