package com.agenttimeline.service;

import com.agenttimeline.model.Message;
import com.agenttimeline.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Utility class for validating message chains and handling chain integrity
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessageChainValidator {

    private final MessageRepository messageRepository;

    /**
     * Validate the integrity of a message chain for a given session
     */
    public ChainValidationResult validateChain(String sessionId) {
        try {
            List<Message> sessionMessages = messageRepository.findBySessionId(sessionId);
            return validateMessageChain(sessionMessages, sessionId);
        } catch (Exception e) {
            log.error("Error validating message chain for session: {}", sessionId, e);
            return ChainValidationResult.builder()
                .valid(false)
                .errorMessage("Error during chain validation: " + e.getMessage())
                .build();
        }
    }

    /**
     * Validate message chain integrity
     */
    public ChainValidationResult validateMessageChain(List<Message> messages, String sessionId) {
        ChainValidationResult.ChainValidationResultBuilder resultBuilder = ChainValidationResult.builder()
            .valid(true)
            .sessionId(sessionId);

        Set<String> messageIds = new HashSet<>();
        Map<String, Message> messageMap = new HashMap<>();
        List<String> orphanMessages = new ArrayList<>();
        List<String> brokenReferences = new ArrayList<>();

        // Build message map and collect IDs
        for (Message message : messages) {
            if (message.getId() != null) {
                messageIds.add(message.getId());
                messageMap.put(message.getId(), message);
            }
        }

        // Check for orphan messages and broken parent references
        for (Message message : messages) {
            if (message.hasParent()) {
                String parentId = message.getParentMessageId();
                if (!messageIds.contains(parentId)) {
                    brokenReferences.add(message.getId());
                    log.warn("Message {} in session {} has broken parent reference: {}",
                        message.getId(), sessionId, parentId);
                }
            }
        }

        // Find root messages (messages without parents)
        List<Message> rootMessages = messages.stream()
            .filter(Message::isFirstMessage)
            .toList();

        // Check for multiple root messages (should only be one per conversation thread)
        if (rootMessages.size() > 1) {
            resultBuilder.warning("Multiple root messages found in session " + sessionId +
                " (" + rootMessages.size() + " root messages)");
        }

        // Validate chain connectivity
        Set<String> reachableMessages = new HashSet<>();
        for (Message rootMessage : rootMessages) {
            traverseAndValidateChain(rootMessage, messageMap, reachableMessages, sessionId);
        }

        // Find unreachable messages
        for (String messageId : messageIds) {
            if (!reachableMessages.contains(messageId)) {
                orphanMessages.add(messageId);
            }
        }

        if (!brokenReferences.isEmpty() || !orphanMessages.isEmpty()) {
            resultBuilder.valid(false);

            List<String> issues = new ArrayList<>();
            if (!brokenReferences.isEmpty()) {
                issues.add(brokenReferences.size() + " broken parent references");
            }
            if (!orphanMessages.isEmpty()) {
                issues.add(orphanMessages.size() + " orphaned messages");
            }

            resultBuilder.errorMessage("Chain validation failed: " + String.join(", ", issues));
            resultBuilder.brokenReferences(brokenReferences);
            resultBuilder.orphanMessages(orphanMessages);
        }

        resultBuilder.totalMessages(messages.size());
        resultBuilder.rootMessages(rootMessages.size());

        ChainValidationResult result = resultBuilder.build();
        log.info("Chain validation for session {}: {}", sessionId, result.isValid() ? "VALID" : "INVALID");
        return result;
    }

    /**
     * Traverse message chain and validate connectivity
     */
    private void traverseAndValidateChain(Message currentMessage, Map<String, Message> messageMap,
                                        Set<String> visited, String sessionId) {
        if (currentMessage == null || visited.contains(currentMessage.getId())) {
            return;
        }

        visited.add(currentMessage.getId());

        // Find child messages
        List<Message> childMessages = messageMap.values().stream()
            .filter(msg -> currentMessage.getId().equals(msg.getParentMessageId()))
            .toList();

        // Recursively validate child messages
        for (Message childMessage : childMessages) {
            traverseAndValidateChain(childMessage, messageMap, visited, sessionId);
        }
    }

    /**
     * Repair broken chains by attempting to fix parent references
     */
    public ChainRepairResult repairChain(String sessionId) {
        ChainValidationResult validation = validateChain(sessionId);
        ChainRepairResult.ChainRepairResultBuilder repairResult = ChainRepairResult.builder()
            .sessionId(sessionId)
            .originalValidation(validation);

        if (validation.isValid()) {
            repairResult.success(true);
            repairResult.message("Chain is already valid, no repair needed");
            return repairResult.build();
        }

        try {
            List<String> repairs = new ArrayList<>();
            List<Message> sessionMessages = messageRepository.findBySessionId(sessionId);

            // Attempt to repair broken parent references
            for (String brokenMessageId : validation.getBrokenReferences()) {
                Message brokenMessage = messageRepository.findById(brokenMessageId).orElse(null);
                if (brokenMessage != null) {
                    // Find the most recent message before this one as a potential parent
                    Optional<Message> potentialParent = sessionMessages.stream()
                        .filter(msg -> !msg.getId().equals(brokenMessageId))
                        .filter(msg -> msg.getTimestamp().isBefore(brokenMessage.getTimestamp()))
                        .max(Comparator.comparing(Message::getTimestamp));

                    if (potentialParent.isPresent()) {
                        brokenMessage.setParentMessageId(potentialParent.get().getId());
                        messageRepository.save(brokenMessage);
                        repairs.add("Fixed parent reference for message " + brokenMessageId +
                                  " -> " + potentialParent.get().getId());
                    }
                }
            }

            // Re-validate after repairs
            ChainValidationResult postRepairValidation = validateChain(sessionId);
            repairResult.success(postRepairValidation.isValid());
            repairResult.repairsPerformed(repairs);
            repairResult.finalValidation(postRepairValidation);

            if (postRepairValidation.isValid()) {
                repairResult.message("Chain repair successful");
            } else {
                repairResult.message("Chain repair partially successful, some issues remain");
            }

        } catch (Exception e) {
            log.error("Error during chain repair for session: {}", sessionId, e);
            repairResult.success(false);
            repairResult.message("Chain repair failed: " + e.getMessage());
        }

        return repairResult.build();
    }

    /**
     * Result class for chain validation
     */
    @lombok.Data
    @lombok.Builder
    public static class ChainValidationResult {
        private boolean valid;
        private String sessionId;
        private String errorMessage;
        private String warning;
        private List<String> brokenReferences;
        private List<String> orphanMessages;
        private int totalMessages;
        private int rootMessages;
    }

    /**
     * Result class for chain repair operations
     */
    @lombok.Data
    @lombok.Builder
    public static class ChainRepairResult {
        private boolean success;
        private String sessionId;
        private String message;
        private ChainValidationResult originalValidation;
        private ChainValidationResult finalValidation;
        private List<String> repairsPerformed;
    }
}
