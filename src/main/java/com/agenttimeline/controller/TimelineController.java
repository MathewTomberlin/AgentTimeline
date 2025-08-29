package com.agenttimeline.controller;

import com.agenttimeline.model.Message;
import com.agenttimeline.model.MessageChunkEmbedding;
import com.agenttimeline.service.ChunkGroupManager;
import com.agenttimeline.service.ChunkingService;
import com.agenttimeline.service.ContextRetrievalService;
import com.agenttimeline.service.EnhancedOllamaService;
import com.agenttimeline.service.MessageChainValidator;
import com.agenttimeline.service.TimelineService;
import com.agenttimeline.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/timeline")
@RequiredArgsConstructor
@Slf4j
public class TimelineController {

    private final TimelineService timelineService;
    private final VectorStoreService vectorStoreService;

    // Phase 6: Enhanced Context Management Services
    private final com.agenttimeline.service.ConversationHistoryManager conversationHistoryManager;
    private final com.agenttimeline.service.ConversationSummaryService conversationSummaryService;
    private final com.agenttimeline.service.KeyInformationExtractor keyInformationExtractor;
    private final com.agenttimeline.service.ConfigurableContextRetrievalService configurableContextRetrievalService;
    private final com.agenttimeline.service.EnhancedPromptBuilder enhancedPromptBuilder;

    // Phase 5: Context-Augmented Generation Services (legacy)
    private final ContextRetrievalService contextRetrievalService;
    private final ChunkGroupManager chunkGroupManager;
    private final EnhancedOllamaService enhancedOllamaService;

    /**
     * Chat endpoint with message chaining
     * Stores user message and assistant response separately with proper parent-child relationships
     * Optionally includes the enhanced prompt sent to LLM for debugging
     */
    @PostMapping("/chat")
    public Mono<ResponseEntity<?>> chat(
            @RequestBody Map<String, String> request,
            @RequestParam(defaultValue = "default") String sessionId,
            @RequestParam(defaultValue = "false") boolean includePrompt) {

        String userMessage = request.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        log.info("Processing chat request for session: {} with message: '{}', includePrompt: {}", sessionId, userMessage, includePrompt);

        return timelineService.processUserMessage(userMessage, sessionId)
                .flatMap(message -> {
                    if (includePrompt) {
                        // For debugging: also return the prompt that was sent to LLM
                        return timelineService.getLastEnhancedPrompt(sessionId)
                                .map(prompt -> {
                                    Map<String, Object> debugResponse = new HashMap<>();
                                    debugResponse.put("message", message);
                                    debugResponse.put("enhancedPrompt", prompt);
                                    debugResponse.put("promptLength", prompt.length());
                                    debugResponse.put("wordCount", prompt.split("\\s+").length);
                                    return ResponseEntity.ok((Object)debugResponse);
                                })
                                .defaultIfEmpty(ResponseEntity.ok((Object)message));
                    } else {
                        return Mono.just(ResponseEntity.ok(message));
                    }
                })
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
            "phase", "5",
            "features", "Message chaining, Conversation reconstruction, Chain validation, Vector embeddings, Similarity search, Context-augmented generation",
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

    /**
     * DEBUG ENDPOINT: Inspect Phase 5 context retrieval pipeline
     * This endpoint allows detailed inspection of the context retrieval process
     */
    @GetMapping("/debug/context/{sessionId}")
    public ResponseEntity<Map<String, Object>> debugContextRetrieval(
            @PathVariable String sessionId,
            @RequestParam String userMessage) {
        try {
            log.info("Debug context retrieval for session {} with message: '{}'", sessionId, userMessage);

            // Step 1: Get expanded groups using ContextRetrievalService
            List<ContextRetrievalService.ExpandedChunkGroup> expandedGroups =
                contextRetrievalService.retrieveContext(userMessage, sessionId, null);

            // Step 2: Merge overlapping groups using ChunkGroupManager
            List<ChunkGroupManager.ContextChunkGroup> contextGroups =
                chunkGroupManager.mergeOverlappingGroups(expandedGroups);

            // Step 3: Construct enhanced prompt using EnhancedOllamaService
            String enhancedPrompt = enhancedOllamaService.constructEnhancedPrompt(userMessage, contextGroups);

            // Build comprehensive debug response
            Map<String, Object> debugInfo = new HashMap<>();
            debugInfo.put("sessionId", sessionId);
            debugInfo.put("userMessage", userMessage);
            debugInfo.put("timestamp", java.time.LocalDateTime.now().toString());

            // Expanded groups details
            List<Map<String, Object>> expandedGroupsInfo = expandedGroups.stream()
                .map(group -> {
                    Map<String, Object> groupInfo = new HashMap<>();
                    groupInfo.put("messageId", group.getMessageId());
                    groupInfo.put("chunkCount", group.getChunkCount());
                    groupInfo.put("combinedText", group.getCombinedText());

                    // Individual chunk details
                    List<Map<String, Object>> chunksInfo = group.getChunks().stream()
                        .map(chunk -> {
                            Map<String, Object> chunkInfo = new HashMap<>();
                            chunkInfo.put("id", chunk.getId());
                            chunkInfo.put("index", chunk.getChunkIndex());
                            chunkInfo.put("text", chunk.getChunkText());
                            chunkInfo.put("textLength", chunk.getChunkText() != null ? chunk.getChunkText().length() : 0);
                            chunkInfo.put("hasEmbedding", chunk.getEmbeddingVector() != null);
                            return chunkInfo;
                        })
                        .toList();
                    groupInfo.put("chunks", chunksInfo);

                    return groupInfo;
                })
                .toList();
            debugInfo.put("expandedGroups", expandedGroupsInfo);

            // Merged groups details
            List<Map<String, Object>> mergedGroupsInfo = contextGroups.stream()
                .map(group -> {
                    Map<String, Object> groupInfo = new HashMap<>();
                    groupInfo.put("messageId", group.getMessageId());
                    groupInfo.put("totalChunks", group.getTotalChunks());
                    groupInfo.put("combinedText", group.getCombinedText());
                    groupInfo.put("earliestTimestamp", group.getEarliestTimestamp());
                    groupInfo.put("latestTimestamp", group.getLatestTimestamp());
                    return groupInfo;
                })
                .toList();
            debugInfo.put("mergedGroups", mergedGroupsInfo);

            // Enhanced prompt details
            debugInfo.put("enhancedPrompt", enhancedPrompt);
            debugInfo.put("promptLength", enhancedPrompt.length());
            debugInfo.put("wordCount", enhancedPrompt.split("\\s+").length);

            // Configuration info
            debugInfo.put("configuration", Map.of(
                "chunksBefore", contextRetrievalService.getChunksBefore(),
                "chunksAfter", contextRetrievalService.getChunksAfter(),
                "maxSimilarChunks", contextRetrievalService.getMaxSimilarChunks(),
                "maxContextGroups", enhancedOllamaService.getMaxContextGroups(),
                "maxTotalChunks", enhancedOllamaService.getMaxTotalChunks(),
                "maxPromptLength", enhancedOllamaService.getMaxPromptLength(),
                "truncationEnabled", enhancedOllamaService.isEnableTruncation()
            ));

            log.info("Debug context retrieval completed for session {}: {} expanded groups, {} merged groups, {} char prompt",
                sessionId, expandedGroups.size(), contextGroups.size(), enhancedPrompt.length());

            return ResponseEntity.ok(debugInfo);

        } catch (Exception e) {
            log.error("Error in debug context retrieval for session {}: {}", sessionId, e.getMessage(), e);
            Map<String, Object> errorInfo = new HashMap<>();
            errorInfo.put("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
            errorInfo.put("errorType", e.getClass().getSimpleName());
            errorInfo.put("sessionId", sessionId);
            errorInfo.put("userMessage", userMessage);
            errorInfo.put("timestamp", java.time.LocalDateTime.now().toString());
            return ResponseEntity.internalServerError().body(errorInfo);
        }
    }

    /**
     * DEBUG ENDPOINT: Test chunking service directly
     */
    @PostMapping("/debug/chunking")
    public ResponseEntity<Map<String, Object>> debugChunking(@RequestBody Map<String, String> request) {
        try {
            String text = request.get("text");
            if (text == null || text.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Text parameter is required"));
            }

            log.info("Debug chunking test for text: '{}'", text.substring(0, Math.min(50, text.length())));

            // Get the chunking service from vector store service
            ChunkingService chunkingService = vectorStoreService.getChunkingService();

            List<String> chunksNoOverlap = chunkingService.chunkText(text);
            List<String> chunksWithOverlap = chunkingService.chunkTextWithOverlap(text);

            Map<String, Object> chunkingInfo = Map.of(
                "originalText", text,
                "originalLength", text.length(),
                "estimatedTokens", chunkingService.estimateTokens(text),
                "chunksNoOverlap", Map.of(
                    "count", chunksNoOverlap.size(),
                    "chunks", chunksNoOverlap.stream()
                        .map(chunk -> Map.of(
                            "text", chunk,
                            "length", chunk.length(),
                            "estimatedTokens", chunkingService.estimateTokens(chunk)
                        ))
                        .toList()
                ),
                "chunksWithOverlap", Map.of(
                    "count", chunksWithOverlap.size(),
                    "chunks", chunksWithOverlap.stream()
                        .map(chunk -> Map.of(
                            "text", chunk,
                            "length", chunk.length(),
                            "estimatedTokens", chunkingService.estimateTokens(chunk)
                        ))
                        .toList()
                )
            );

            log.info("Chunking debug completed: {} chunks without overlap, {} with overlap",
                chunksNoOverlap.size(), chunksWithOverlap.size());

            return ResponseEntity.ok(chunkingInfo);

        } catch (Exception e) {
            log.error("Error in chunking debug: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage(),
                "errorType", e.getClass().getSimpleName()
            ));
        }
    }

    // =====================================
    // Phase 6: Enhanced Context Management
    // =====================================

    /**
     * Get Phase 6 system statistics and status.
     */
    @GetMapping("/phase6/stats")
    public ResponseEntity<Map<String, Object>> getPhase6Statistics() {
        try {
            Map<String, Object> stats = timelineService.getPhase6Statistics();
            log.info("Retrieved Phase 6 statistics");
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error retrieving Phase 6 statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage(),
                "errorType", e.getClass().getSimpleName()
            ));
        }
    }

    /**
     * Get conversation context for a session (Phase 6).
     */
    @GetMapping("/phase6/context/{sessionId}")
    public ResponseEntity<Map<String, Object>> getConversationContext(@PathVariable String sessionId) {
        try {
            var context = conversationHistoryManager.getConversationContext(sessionId);

            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("hasRecentMessages", context.hasRecentMessages());
            response.put("hasSummary", context.hasSummary());
            response.put("recentMessageCount", context.getRecentMessages().size());
            response.put("summaryLength", context.getSummary() != null ? context.getSummary().length() : 0);

            if (context.hasRecentMessages()) {
                response.put("recentMessages", context.getRecentMessages().stream()
                    .map(msg -> Map.of(
                        "id", msg.getId(),
                        "role", msg.getRole().toString(),
                        "content", msg.getContent(),
                        "timestamp", msg.getTimestamp()
                    ))
                    .toList());
            }

            if (context.hasSummary()) {
                response.put("summary", context.getSummary());
            }

            log.info("Retrieved conversation context for session {}: {} messages, {} chars summary",
                sessionId, context.getRecentMessages().size(),
                context.getSummary() != null ? context.getSummary().length() : 0);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error retrieving conversation context for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage(),
                "errorType", e.getClass().getSimpleName()
            ));
        }
    }

    /**
     * Clear conversation history for a session (Phase 6).
     */
    @DeleteMapping("/phase6/history/{sessionId}")
    public ResponseEntity<Map<String, Object>> clearConversationHistory(@PathVariable String sessionId) {
        try {
            conversationHistoryManager.clearHistory(sessionId);

            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("action", "history_cleared");
            response.put("timestamp", java.time.LocalDateTime.now());

            log.info("Cleared conversation history for session {}", sessionId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error clearing conversation history for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage(),
                "errorType", e.getClass().getSimpleName()
            ));
        }
    }

    /**
     * Extract key information from a message (Phase 6).
     */
    @PostMapping("/phase6/extract")
    public ResponseEntity<Map<String, Object>> extractKeyInformation(
            @RequestBody Map<String, String> request,
            @RequestParam(defaultValue = "default") String sessionId) {

        String messageText = request.get("message");
        if (messageText == null || messageText.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message text is required"));
        }

        try {
            // Create a temporary message for extraction
            Message tempMessage = new Message();
            tempMessage.setId("temp-" + System.currentTimeMillis());
            tempMessage.setContent(messageText);
            tempMessage.setRole(Message.Role.USER);

            var extractedInfo = keyInformationExtractor.extractInformation(tempMessage, sessionId);

            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("originalMessage", messageText);
            response.put("extraction", Map.of(
                "entities", extractedInfo.getEntities(),
                "keyFacts", extractedInfo.getKeyFacts(),
                "actionItems", extractedInfo.getActionItems(),
                "userIntent", extractedInfo.getUserIntent(),
                "contextualInfo", extractedInfo.getContextualInfo(),
                "sentiment", extractedInfo.getSentiment(),
                "urgency", extractedInfo.getUrgency(),
                "isEmpty", extractedInfo.isEmpty()
            ));

            log.info("Extracted key information from message in session {}: {} entities, {} facts",
                sessionId, extractedInfo.getEntities().size(), extractedInfo.getKeyFacts().size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error extracting key information: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage(),
                "errorType", e.getClass().getSimpleName()
            ));
        }
    }

    /**
     * Get configurable context retrieval statistics (Phase 6).
     */
    @GetMapping("/phase6/retrieval/stats")
    public ResponseEntity<Map<String, Object>> getContextRetrievalStatistics() {
        try {
            Map<String, Object> stats = configurableContextRetrievalService.getStatistics();
            log.info("Retrieved context retrieval statistics");
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error retrieving context retrieval statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage(),
                "errorType", e.getClass().getSimpleName()
            ));
        }
    }

    /**
     * Get conversation history manager statistics (Phase 6).
     */
    @GetMapping("/phase6/history/stats")
    public ResponseEntity<Map<String, Object>> getConversationHistoryStatistics() {
        try {
            Map<String, Object> stats = conversationHistoryManager.getStatistics();
            log.info("Retrieved conversation history statistics");
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error retrieving conversation history statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage(),
                "errorType", e.getClass().getSimpleName()
            ));
        }
    }

    /**
     * Test Phase 6 context retrieval with custom configuration.
     */
    @PostMapping("/phase6/test/retrieval")
    public ResponseEntity<Map<String, Object>> testContextRetrieval(
            @RequestBody Map<String, Object> request,
            @RequestParam(defaultValue = "default") String sessionId) {

        String userMessage = (String) request.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User message is required"));
        }

        try {
            // Parse optional configuration parameters
            var config = com.agenttimeline.service.ConfigurableContextRetrievalService.RetrievalConfig.builder();

            if (request.containsKey("chunksBefore")) {
                config.chunksBefore(((Number) request.get("chunksBefore")).intValue());
            }
            if (request.containsKey("chunksAfter")) {
                config.chunksAfter(((Number) request.get("chunksAfter")).intValue());
            }
            if (request.containsKey("maxSimilarChunks")) {
                config.maxSimilarChunks(((Number) request.get("maxSimilarChunks")).intValue());
            }
            if (request.containsKey("similarityThreshold")) {
                config.similarityThreshold(((Number) request.get("similarityThreshold")).doubleValue());
            }
            if (request.containsKey("strategy")) {
                String strategyStr = (String) request.get("strategy");
                var strategy = switch (strategyStr.toLowerCase()) {
                    case "fixed" -> com.agenttimeline.service.ConfigurableContextRetrievalService.RetrievalStrategy.FIXED;
                    case "adaptive" -> com.agenttimeline.service.ConfigurableContextRetrievalService.RetrievalStrategy.ADAPTIVE;
                    case "intelligent" -> com.agenttimeline.service.ConfigurableContextRetrievalService.RetrievalStrategy.INTELLIGENT;
                    default -> com.agenttimeline.service.ConfigurableContextRetrievalService.RetrievalStrategy.ADAPTIVE;
                };
                config.strategy(strategy);
            }

            var retrievalConfig = config.build();
            var results = configurableContextRetrievalService.retrieveContext(userMessage, sessionId, null, retrievalConfig);

            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("userMessage", userMessage);
            response.put("config", Map.of(
                "strategy", retrievalConfig.getStrategy(),
                "chunksBefore", retrievalConfig.getChunksBefore(),
                "chunksAfter", retrievalConfig.getChunksAfter(),
                "maxSimilarChunks", retrievalConfig.getMaxSimilarChunks(),
                "similarityThreshold", retrievalConfig.getSimilarityThreshold()
            ));
            response.put("results", results.stream()
                .map(group -> Map.of(
                    "messageId", group.getMessageId(),
                    "chunkCount", group.getChunkCount(),
                    "combinedText", group.getCombinedText().substring(0, Math.min(200, group.getCombinedText().length()))
                ))
                .toList());

            log.info("Tested context retrieval for session {}: {} groups found", sessionId, results.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error testing context retrieval: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", e.getMessage(),
                "errorType", e.getClass().getSimpleName()
            ));
        }
    }
}
