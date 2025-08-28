package com.agenttimeline.controller;

import com.agenttimeline.model.TimelineMessage;
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

    @PostMapping("/chat")
    public Mono<ResponseEntity<TimelineMessage>> chat(
            @RequestBody Map<String, String> request,
            @RequestParam(defaultValue = "default") String sessionId) {

        String userMessage = request.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        log.info("Processing chat request for session: {}", sessionId);

        return timelineService.processUserMessage(userMessage, sessionId)
                .map(message -> ResponseEntity.ok(message))
                .doOnError(error -> log.error("Error processing chat request", error))
                .onErrorResume(error -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<TimelineMessage>> getSessionMessages(@PathVariable String sessionId) {
        try {
            List<TimelineMessage> messages = timelineService.getSessionMessages(sessionId);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error retrieving session messages", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/messages")
    public ResponseEntity<List<TimelineMessage>> getAllMessages() {
        try {
            List<TimelineMessage> messages = timelineService.getAllMessages();
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error retrieving all messages", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "AgentTimeline API",
            "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }
}
