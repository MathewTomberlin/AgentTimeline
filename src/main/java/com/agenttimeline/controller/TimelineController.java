package com.agenttimeline.controller;

import com.agenttimeline.model.Message;
import com.agenttimeline.service.MessageChainValidator;
import com.agenttimeline.service.TimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/timeline")
@RequiredArgsConstructor
@Slf4j
public class TimelineController {

    private final TimelineService timelineService;

    /**
     * Chat endpoint with message chaining
     * Stores user message and assistant response separately with proper parent-child relationships
     */
    @PostMapping("/chat")
    public Mono<ResponseEntity<Message>> chat(
            @RequestBody Map<String, String> request,
            @RequestParam(defaultValue = "default") String sessionId) {

        String userMessage = request.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        log.info("Processing chat request for session: {} with message chaining", sessionId);

        return timelineService.processUserMessage(userMessage, sessionId)
                .map(message -> ResponseEntity.ok(message))
                .doOnError(error -> log.error("Error processing chat request", error))
                .onErrorResume(error -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    /**
     * Get conversation history by reconstructing message chains
     * This endpoint uses the new message chaining logic for efficient conversation reconstruction
     */
    @GetMapping("/conversation/{sessionId}")
    public ResponseEntity<List<Message>> getConversationHistory(@PathVariable String sessionId) {
        try {
            List<Message> conversation = timelineService.getConversationHistory(sessionId);
            log.info("Retrieved conversation history for session: {} with {} messages",
                sessionId, conversation.size());
            return ResponseEntity.ok(conversation);
        } catch (Exception e) {
            log.error("Error retrieving conversation history for session: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get all messages for a session (sorted by timestamp, for backward compatibility)
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<Message>> getSessionMessages(@PathVariable String sessionId) {
        try {
            List<Message> messages = timelineService.getSessionMessages(sessionId);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error retrieving session messages", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get all messages across all sessions
     */
    @GetMapping("/messages")
    public ResponseEntity<List<Message>> getAllMessages() {
        try {
            List<Message> messages = timelineService.getAllMessages();
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error retrieving all messages", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Validate message chain for a session
     */
    @GetMapping("/chain/validate/{sessionId}")
    public ResponseEntity<MessageChainValidator.ChainValidationResult> validateChain(@PathVariable String sessionId) {
        try {
            MessageChainValidator.ChainValidationResult result = timelineService.validateMessageChain(sessionId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error validating chain for session: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Repair broken message chains for a session
     */
    @PostMapping("/chain/repair/{sessionId}")
    public ResponseEntity<MessageChainValidator.ChainRepairResult> repairChain(@PathVariable String sessionId) {
        try {
            MessageChainValidator.ChainRepairResult result = timelineService.repairMessageChain(sessionId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error repairing chain for session: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get chain validation statistics across all sessions
     */
    @GetMapping("/chain/statistics")
    public ResponseEntity<Map<String, Object>> getChainStatistics() {
        try {
            Map<String, Object> stats = timelineService.getChainValidationStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error retrieving chain statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * TEST ENDPOINT: Create broken chains for testing repair functionality
     * This endpoint is for testing purposes only and creates intentionally broken message chains
     */
    @PostMapping("/test/create-broken-chain")
    public ResponseEntity<String> createBrokenChain(
            @RequestParam(defaultValue = "test-broken-chain") String sessionId,
            @RequestParam(defaultValue = "orphaned") String breakType) {

        try {
            String result = timelineService.createBrokenChainForTesting(sessionId, breakType);
            return ResponseEntity.ok("Broken chain created: " + result);
        } catch (Exception e) {
            log.error("Error creating broken chain for testing", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "AgentTimeline API",
            "phase", "2",
            "features", "Message chaining, Conversation reconstruction, Chain validation",
            "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }
}
