package com.agenttimeline.service;

import com.agenttimeline.model.Message;
import com.agenttimeline.model.OllamaRequest;
import com.agenttimeline.model.OllamaResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for extracting key information from messages using LLM-powered analysis.
 *
 * This service is a core component of Phase 6: Enhanced Context Management.
 * It provides:
 * - LLM-powered key information extraction
 * - Structured output for entities, facts, and relationships
 * - Context-aware extraction based on conversation flow
 * - Async processing for performance
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeyInformationExtractor {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ollama.model:sam860/dolphin3-qwen2.5:3b}")
    private String defaultModel;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${extraction.max-concurrent-requests:5}")
    private int maxConcurrentRequests;

    @Value("${extraction.timeout-seconds:30}")
    private int extractionTimeoutSeconds;

    @Value("${extraction.enable-fallback:true}")
    private boolean enableFallback;

    // Cache for extraction results to avoid re-processing
    private final Map<String, ExtractedInformation> extractionCache = new ConcurrentHashMap<>();

    // Rate limiting for concurrent requests
    private final Set<String> activeExtractions = ConcurrentHashMap.newKeySet();

    /**
     * Extract key information from a single message.
     *
     * @param message The message to analyze
     * @param sessionId Session ID for context
     * @return ExtractedInformation containing key facts, entities, etc.
     */
    public ExtractedInformation extractInformation(Message message, String sessionId) {
        if (message == null || message.getContent() == null || message.getContent().trim().isEmpty()) {
            log.debug("Empty message provided for extraction, session: {}", sessionId);
            return new ExtractedInformation();
        }

        String cacheKey = message.getId();
        ExtractedInformation cached = extractionCache.get(cacheKey);
        if (cached != null) {
            log.debug("Using cached extraction for message {}", message.getId());
            return cached;
        }

        log.debug("Extracting information from message {} in session {}", message.getId(), sessionId);

        try {
            ExtractedInformation extracted = performExtraction(message, sessionId);

            // Cache the result
            extractionCache.put(cacheKey, extracted);

            log.debug("Successfully extracted information from message {}: entities={}, facts={}, intent={}",
                message.getId(), extracted.getEntities().size(), extracted.getKeyFacts().size(),
                extracted.getUserIntent() != null ? "present" : "none");

            return extracted;

        } catch (Exception e) {
            log.error("Error extracting information from message {}: {}", message.getId(), e.getMessage(), e);

            if (enableFallback) {
                return generateFallbackExtraction(message);
            } else {
                return new ExtractedInformation();
            }
        }
    }

    /**
     * Extract information from multiple messages asynchronously.
     *
     * @param messages List of messages to process
     * @param sessionId Session ID for context
     * @return List of extraction results
     */
    public List<ExtractedInformation> extractInformationBatch(List<Message> messages, String sessionId) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        log.info("Starting batch extraction of {} messages for session {}", messages.size(), sessionId);

        List<CompletableFuture<ExtractedInformation>> futures = messages.stream()
            .map(message -> CompletableFuture.supplyAsync(() ->
                extractInformation(message, sessionId)))
            .toList();

        // Wait for all extractions to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]));

        try {
            allFutures.get(); // Wait for completion
            List<ExtractedInformation> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

            log.info("Completed batch extraction for session {}: {} results", sessionId, results.size());
            return results;

        } catch (Exception e) {
            log.error("Error in batch extraction for session {}: {}", sessionId, e.getMessage(), e);
            return messages.stream()
                .map(msg -> enableFallback ? generateFallbackExtraction(msg) : new ExtractedInformation())
                .toList();
        }
    }

    /**
     * Perform the actual LLM-powered information extraction.
     */
    private ExtractedInformation performExtraction(Message message, String sessionId) {
        String prompt = buildExtractionPrompt(message);

        log.debug("Calling LLM for information extraction, message: {}", message.getId());

        try {
            OllamaRequest request = new OllamaRequest(defaultModel, prompt, false);

            Mono<OllamaResponse> responseMono = webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OllamaResponse.class)
                .timeout(java.time.Duration.ofSeconds(extractionTimeoutSeconds))
                .doOnNext(response -> log.debug("Received extraction response for message {}", message.getId()))
                .doOnError(error -> log.error("Error calling LLM for extraction: {}", error.getMessage()));

            OllamaResponse response = responseMono.block();

            if (response != null && response.getResponse() != null) {
                return parseExtractionResponse(response.getResponse(), message.getId());
            } else {
                log.warn("Null or empty response from LLM for message {}", message.getId());
                return enableFallback ? generateFallbackExtraction(message) : new ExtractedInformation();
            }

        } catch (Exception e) {
            log.error("Exception during LLM extraction for message {}: {}", message.getId(), e.getMessage(), e);
            return enableFallback ? generateFallbackExtraction(message) : new ExtractedInformation();
        }
    }

    /**
     * Build the prompt for information extraction.
     */
    private String buildExtractionPrompt(Message message) {
        String role = message.getRole().toString().toLowerCase();
        String content = message.getContent();

        return String.format(
            "You are an expert information extraction assistant. Analyze the following %s message and extract key information in JSON format.\n\n" +
            "MESSAGE: \"%s\"\n\n" +
            "Extract the following information and respond ONLY with a valid JSON object:\n\n" +
            "{\n" +
            "  \"entities\": [\"list of important entities like people, places, organizations, dates\"],\n" +
            "  \"keyFacts\": [\"list of important facts or information mentioned\"],\n" +
            "  \"userIntent\": \"what the user is trying to accomplish or asking about (if user message)\",\n" +
            "  \"actionItems\": [\"list of any tasks, requests, or actions mentioned\"],\n" +
            "  \"contextualInfo\": \"any relationships or context that connects this to previous conversation\",\n" +
            "  \"sentiment\": \"positive/negative/neutral tone of the message\",\n" +
            "  \"urgency\": \"low/medium/high - how urgent or time-sensitive this message is\"\n" +
            "}\n\n" +
            "Guidelines:\n" +
            "- Be specific and extract actual information mentioned\n" +
            "- For entities, include proper names, dates, locations, etc.\n" +
            "- For key facts, focus on information that would be important to remember\n" +
            "- Leave fields empty (empty arrays or null) if no relevant information exists\n" +
            "- Keep extractions concise but informative\n\n" +
            "JSON Response:",
            role, content
        );
    }

    /**
     * Parse the LLM response into structured ExtractedInformation.
     */
    private ExtractedInformation parseExtractionResponse(String response, String messageId) {
        try {
            // Clean the response (remove any extra text around JSON)
            String jsonResponse = extractJsonFromResponse(response);

            JsonNode jsonNode = objectMapper.readTree(jsonResponse);

            ExtractedInformation info = new ExtractedInformation();

            // Parse entities
            if (jsonNode.has("entities")) {
                JsonNode entitiesNode = jsonNode.get("entities");
                if (entitiesNode.isArray()) {
                    for (JsonNode entity : entitiesNode) {
                        if (entity.isTextual() && !entity.asText().trim().isEmpty()) {
                            info.addEntity(entity.asText().trim());
                        }
                    }
                }
            }

            // Parse key facts
            if (jsonNode.has("keyFacts")) {
                JsonNode factsNode = jsonNode.get("keyFacts");
                if (factsNode.isArray()) {
                    for (JsonNode fact : factsNode) {
                        if (fact.isTextual() && !fact.asText().trim().isEmpty()) {
                            info.addKeyFact(fact.asText().trim());
                        }
                    }
                }
            }

            // Parse user intent
            if (jsonNode.has("userIntent") && !jsonNode.get("userIntent").isNull()) {
                info.setUserIntent(jsonNode.get("userIntent").asText().trim());
            }

            // Parse action items
            if (jsonNode.has("actionItems")) {
                JsonNode actionsNode = jsonNode.get("actionItems");
                if (actionsNode.isArray()) {
                    for (JsonNode action : actionsNode) {
                        if (action.isTextual() && !action.asText().trim().isEmpty()) {
                            info.addActionItem(action.asText().trim());
                        }
                    }
                }
            }

            // Parse contextual info
            if (jsonNode.has("contextualInfo") && !jsonNode.get("contextualInfo").isNull()) {
                info.setContextualInfo(jsonNode.get("contextualInfo").asText().trim());
            }

            // Parse sentiment
            if (jsonNode.has("sentiment") && !jsonNode.get("sentiment").isNull()) {
                info.setSentiment(jsonNode.get("sentiment").asText().trim());
            }

            // Parse urgency
            if (jsonNode.has("urgency") && !jsonNode.get("urgency").isNull()) {
                info.setUrgency(jsonNode.get("urgency").asText().trim());
            }

            log.debug("Parsed extraction for message {}: {} entities, {} facts",
                messageId, info.getEntities().size(), info.getKeyFacts().size());

            return info;

        } catch (Exception e) {
            log.error("Error parsing extraction response for message {}: {}", messageId, e.getMessage(), e);
            return enableFallback ? generateFallbackExtractionById(messageId) : new ExtractedInformation();
        }
    }

    /**
     * Extract JSON from LLM response that might have extra text.
     */
    private String extractJsonFromResponse(String response) {
        // Look for JSON object boundaries
        int startIndex = response.indexOf('{');
        int endIndex = response.lastIndexOf('}');

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }

        // If no clear JSON boundaries, return the whole response
        return response.trim();
    }

    /**
     * Generate fallback extraction when LLM is unavailable.
     */
    private ExtractedInformation generateFallbackExtraction(Message message) {
        ExtractedInformation info = new ExtractedInformation();

        // Simple keyword-based extraction
        String content = message.getContent().toLowerCase();

        // Extract potential entities (capitalized words)
        String[] words = message.getContent().split("\\s+");
        for (String word : words) {
            if (word.length() > 1 && Character.isUpperCase(word.charAt(0))) {
                info.addEntity(word);
            }
        }

        // Add the full content as a key fact if it's reasonably short
        if (message.getContent().length() < 200) {
            info.addKeyFact(message.getContent());
        } else {
            info.addKeyFact(message.getContent().substring(0, 200) + "...");
        }

        // Set basic sentiment and urgency
        info.setSentiment("neutral");
        info.setUrgency("medium");

        log.debug("Generated fallback extraction for message {}", message.getId());
        return info;
    }

    /**
     * Generate fallback extraction by message ID when message object is not available.
     */
    private ExtractedInformation generateFallbackExtractionById(String messageId) {
        ExtractedInformation info = new ExtractedInformation();
        info.addKeyFact("Message content (extraction failed)");
        info.setSentiment("unknown");
        info.setUrgency("medium");
        return info;
    }

    /**
     * Clear the extraction cache.
     */
    public void clearCache() {
        int cacheSize = extractionCache.size();
        extractionCache.clear();
        log.info("Cleared extraction cache ({} entries)", cacheSize);
    }

    /**
     * Get cache statistics.
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", extractionCache.size());
        stats.put("activeExtractions", activeExtractions.size());
        stats.put("maxConcurrentRequests", maxConcurrentRequests);
        return stats;
    }

    /**
     * Check if the extraction service is available.
     */
    public boolean isAvailable() {
        try {
            OllamaRequest testRequest = new OllamaRequest(defaultModel, "Test extraction", false);

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
            log.warn("Extraction service not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Data class for storing extracted information.
     */
    public static class ExtractedInformation {
        private final List<String> entities = new ArrayList<>();
        private final List<String> keyFacts = new ArrayList<>();
        private final List<String> actionItems = new ArrayList<>();
        private String userIntent;
        private String contextualInfo;
        private String sentiment = "neutral";
        private String urgency = "medium";

        public void addEntity(String entity) {
            if (entity != null && !entity.trim().isEmpty()) {
                entities.add(entity.trim());
            }
        }

        public void addKeyFact(String fact) {
            if (fact != null && !fact.trim().isEmpty()) {
                keyFacts.add(fact.trim());
            }
        }

        public void addActionItem(String action) {
            if (action != null && !action.trim().isEmpty()) {
                actionItems.add(action.trim());
            }
        }

        // Getters
        public List<String> getEntities() { return new ArrayList<>(entities); }
        public List<String> getKeyFacts() { return new ArrayList<>(keyFacts); }
        public List<String> getActionItems() { return new ArrayList<>(actionItems); }
        public String getUserIntent() { return userIntent; }
        public String getContextualInfo() { return contextualInfo; }
        public String getSentiment() { return sentiment; }
        public String getUrgency() { return urgency; }

        // Setters
        public void setUserIntent(String userIntent) { this.userIntent = userIntent; }
        public void setContextualInfo(String contextualInfo) { this.contextualInfo = contextualInfo; }
        public void setSentiment(String sentiment) { this.sentiment = sentiment; }
        public void setUrgency(String urgency) { this.urgency = urgency; }

        public boolean isEmpty() {
            return entities.isEmpty() && keyFacts.isEmpty() && actionItems.isEmpty() &&
                   userIntent == null && contextualInfo == null;
        }

        @Override
        public String toString() {
            return "ExtractedInformation{" +
                "entities=" + entities.size() +
                ", keyFacts=" + keyFacts.size() +
                ", actionItems=" + actionItems.size() +
                ", hasIntent=" + (userIntent != null) +
                ", sentiment='" + sentiment + '\'' +
                ", urgency='" + urgency + '\'' +
                '}';
        }
    }
}
