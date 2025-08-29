package com.agenttimeline.controller;

import com.agenttimeline.model.Message;
import com.agenttimeline.model.MessageChunkEmbedding;
import com.agenttimeline.service.MessageChainValidator;
import com.agenttimeline.service.TimelineService;
import com.agenttimeline.service.VectorStoreService;
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
    private final VectorStoreService vectorStoreService;

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

    // ==================== VECTOR SEARCH AND EMBEDDING ENDPOINTS ====================

    /**
     * Search for similar message chunks within a session using vector similarity
     */
    @PostMapping("/search/similar")
    public ResponseEntity<List<MessageChunkEmbedding>> searchSimilarChunks(
            @RequestBody Map<String, Object> request,
            @RequestParam(value = "sessionId", defaultValue = "default") String sessionId) {

        try {
            String query = (String) request.get("query");
            Integer limit = (Integer) request.get("limit");

            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            int searchLimit = limit != null ? limit : 5;
            log.debug("Starting vector search for query '{}' in session '{}' with limit {}", query, sessionId, searchLimit);

            List<MessageChunkEmbedding> similarChunks = vectorStoreService.findSimilarChunks(
                query, sessionId, searchLimit);

            log.info("Vector search completed for session {}: found {} similar chunks", sessionId, similarChunks.size());
            log.debug("Similar chunks details: {}", similarChunks.stream()
                .map(chunk -> String.format("ID:%d, Text:'%s...'",
                    chunk.getId(),
                    chunk.getChunkText() != null ? chunk.getChunkText().substring(0, Math.min(30, chunk.getChunkText().length())) : "null"))
                .toList());
            return ResponseEntity.ok(similarChunks);

        } catch (Exception e) {
            log.error("Error performing vector similarity search", e);
            return ResponseEntity.internalServerError().build();
        }
    }



    /**
     * Search for similar message chunks across all sessions
     */
    @PostMapping("/search/similar/global")
    public ResponseEntity<List<MessageChunkEmbedding>> searchSimilarChunksGlobal(
            @RequestBody Map<String, Object> request) {

        try {
            String query = (String) request.get("query");
            Integer limit = (Integer) request.get("limit");

            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            int searchLimit = limit != null ? limit : 10;
            List<MessageChunkEmbedding> similarChunks = vectorStoreService.findSimilarChunksGlobal(
                query, searchLimit);

            log.info("Global vector search completed: found {} similar chunks", similarChunks.size());
            return ResponseEntity.ok(similarChunks);

        } catch (Exception e) {
            log.error("Error performing global vector similarity search", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Search for similar chunks within a similarity threshold
     */
    @PostMapping("/search/threshold/{sessionId}")
    public ResponseEntity<List<MessageChunkEmbedding>> searchWithinThreshold(
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request) {

        try {
            String query = (String) request.get("query");
            Double threshold = (Double) request.get("threshold");

            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            double similarityThreshold = threshold != null ? threshold : 0.7;
            List<MessageChunkEmbedding> similarChunks = vectorStoreService.findSimilarChunksWithinThreshold(
                query, sessionId, similarityThreshold);

            log.info("Threshold search completed for session {}: found {} chunks within threshold {}",
                sessionId, similarChunks.size(), similarityThreshold);
            return ResponseEntity.ok(similarChunks);

        } catch (Exception e) {
            log.error("Error performing threshold-based vector search", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get all chunks for a specific message
     */
    @GetMapping("/chunks/message/{messageId}")
    public ResponseEntity<List<MessageChunkEmbedding>> getChunksForMessage(@PathVariable String messageId) {
        try {
            List<MessageChunkEmbedding> chunks = vectorStoreService.getChunksForMessage(messageId);
            return ResponseEntity.ok(chunks);
        } catch (Exception e) {
            log.error("Error retrieving chunks for message: {}", messageId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get all chunks for a specific session
     */
    @GetMapping("/chunks/session/{sessionId}")
    public ResponseEntity<List<MessageChunkEmbedding>> getChunksForSession(@PathVariable String sessionId) {
        try {
            List<MessageChunkEmbedding> chunks = vectorStoreService.getChunksForSession(sessionId);
            return ResponseEntity.ok(chunks);
        } catch (Exception e) {
            log.error("Error retrieving chunks for session: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get vector store statistics
     */
    @GetMapping("/vector/statistics")
    public ResponseEntity<VectorStoreService.VectorStoreStatistics> getVectorStoreStatistics() {
        try {
            VectorStoreService.VectorStoreStatistics stats = vectorStoreService.getStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error retrieving vector store statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Manually trigger vector processing for a message (for testing/backfilling)
     */
    @PostMapping("/vector/process")
    public ResponseEntity<Map<String, Object>> processMessageForVector(
            @RequestBody Map<String, String> request) {

        try {
            String messageId = request.get("messageId");
            String messageText = request.get("messageText");
            String sessionId = request.get("sessionId");

            if (messageId == null || messageText == null || sessionId == null) {
                return ResponseEntity.badRequest().build();
            }

            int chunksCreated = vectorStoreService.processAndStoreMessage(messageId, messageText, sessionId);

            Map<String, Object> response = Map.of(
                "messageId", messageId,
                "chunksCreated", chunksCreated,
                "success", chunksCreated > 0
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error manually processing message for vector storage", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Debug endpoint to inspect chunks for a session
     */
    @GetMapping("/debug/chunks/{sessionId}")
    public ResponseEntity<Map<String, Object>> debugChunks(@PathVariable String sessionId) {
        try {
            List<MessageChunkEmbedding> chunks = vectorStoreService.getChunksForSession(sessionId);

            Map<String, Object> response = Map.of(
                "sessionId", sessionId,
                "totalChunks", chunks.size(),
                "chunks", chunks.stream().map(chunk -> Map.of(
                    "id", chunk.getId(),
                    "messageId", chunk.getMessageId(),
                    "chunkIndex", chunk.getChunkIndex(),
                    "chunkText", chunk.getChunkText() != null ? chunk.getChunkText().substring(0, Math.min(50, chunk.getChunkText().length())) + "..." : null,
                    "hasEmbedding", chunk.getEmbeddingVector() != null && chunk.getEmbeddingVector().length > 0,
                    "embeddingDimensions", chunk.getEmbeddingVector() != null ? chunk.getEmbeddingVector().length : 0
                )).toList()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting debug info for session: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }





    /**
     * Reprocess all messages in a session for vector storage
     */
    @PostMapping("/vector/reprocess/{sessionId}")
    public ResponseEntity<Map<String, Object>> reprocessSession(@PathVariable String sessionId) {
        try {
            List<Message> messages = timelineService.getSessionMessages(sessionId);

            // Delete all existing chunks for the session to avoid duplicates
            vectorStoreService.deleteChunksForSession(sessionId);
            long deletedChunks = 0; // Count not easily available without repository access
            log.info("Deleted existing chunks for session {} before reprocessing", sessionId);

            int totalChunks = 0;
            int processedMessages = 0;

            for (Message message : messages) {
                int chunks = vectorStoreService.processAndStoreMessage(
                    message.getId(),
                    message.getContent(),
                    sessionId
                );
                totalChunks += chunks;
                processedMessages++;
                log.info("Reprocessed message {}: {} chunks", message.getId(), chunks);
            }

            Map<String, Object> response = Map.of(
                "sessionId", sessionId,
                "processedMessages", processedMessages,
                "totalChunks", totalChunks,
                "deletedChunks", deletedChunks,
                "success", true
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error reprocessing session: {}", sessionId, e);
            return ResponseEntity.internalServerError().build();
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
            "phase", "4",
            "features", "Message chaining, Conversation reconstruction, Chain validation, Vector embeddings, Similarity search",
            "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }

    /**
     * Test embedding generation endpoint
     */
    @PostMapping("/test/embedding")
    public ResponseEntity<Map<String, Object>> testEmbedding(@RequestBody Map<String, String> request) {
        try {
            String testText = request.get("text");
            if (testText == null || testText.trim().isEmpty()) {
                testText = "This is a test message for embedding generation";
            }

            log.info("Testing embedding generation for text: '{}'", testText);

            // Test embedding generation
            double[] embedding = vectorStoreService.getEmbeddingService().generateEmbedding(testText);

            Map<String, Object> response = Map.of(
                "success", embedding.length > 0,
                "text", testText,
                "embeddingLength", embedding.length,
                "firstFewValues", embedding.length > 0 ?
                    java.util.Arrays.copyOfRange(embedding, 0, Math.min(5, embedding.length)) :
                    new double[0]
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error testing embedding generation", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "errorType", e.getClass().getSimpleName()
            ));
        }
    }

    /**
     * Test embedding generation within VectorStoreService
     */
    @PostMapping("/test/vectorstore-embedding")
    public ResponseEntity<Map<String, Object>> testVectorStoreEmbedding(@RequestBody Map<String, String> request) {
        try {
            String testText = request.get("text");
            if (testText == null || testText.trim().isEmpty()) {
                testText = "Test embedding within VectorStoreService";
            }

            log.info("Testing embedding generation within VectorStoreService for text: '{}'", testText);

            // Test embedding generation within VectorStoreService
            double[] embedding = vectorStoreService.testEmbeddingGeneration(testText);

            Map<String, Object> response = Map.of(
                "success", embedding.length > 0,
                "text", testText,
                "embeddingLength", embedding.length,
                "firstFewValues", embedding.length > 0 ?
                    java.util.Arrays.copyOfRange(embedding, 0, Math.min(5, embedding.length)) :
                    new double[0],
                "testType", "VectorStoreService direct test"
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error testing VectorStoreService embedding generation", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "errorType", e.getClass().getSimpleName(),
                "testType", "VectorStoreService direct test"
            ));
        }
    }
}
