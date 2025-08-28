package com.agenttimeline.service;

import com.agenttimeline.model.OllamaResponse;
import com.agenttimeline.model.TimelineMessage;
import com.agenttimeline.repository.TimelineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimelineService {

    private final OllamaService ollamaService;
    private final TimelineRepository timelineRepository;

    public Mono<TimelineMessage> processUserMessage(String userMessage, String sessionId) {
        String messageId = UUID.randomUUID().toString();
        LocalDateTime startTime = LocalDateTime.now();

        return ollamaService.generateResponse(userMessage)
                .map(ollamaResponse -> {
                    LocalDateTime endTime = LocalDateTime.now();
                    long responseTime = java.time.Duration.between(startTime, endTime).toMillis();

                    TimelineMessage timelineMessage = new TimelineMessage();
                    timelineMessage.setId(messageId);
                    timelineMessage.setSessionId(sessionId);
                    timelineMessage.setUserMessage(userMessage);
                    timelineMessage.setAssistantResponse(ollamaResponse.getResponse());
                    timelineMessage.setTimestamp(startTime);
                    timelineMessage.setModelUsed(ollamaResponse.getModel());
                    timelineMessage.setResponseTime(responseTime);

                    return timelineMessage;
                })
                .doOnNext(message -> {
                    timelineRepository.save(message);
                    log.info("Saved timeline message with ID: {}", message.getId());
                });
    }

    public List<TimelineMessage> getSessionMessages(String sessionId) {
        // Get all messages and filter by session ID since Spring Data Redis
        // doesn't support query methods like JPA
        List<TimelineMessage> allMessages = new ArrayList<>();
        timelineRepository.findAll().forEach(allMessages::add);

        return allMessages.stream()
                .filter(message -> sessionId.equals(message.getSessionId()))
                .sorted(Comparator.comparing(TimelineMessage::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    public List<TimelineMessage> getAllMessages() {
        List<TimelineMessage> allMessages = new ArrayList<>();
        timelineRepository.findAll().forEach(allMessages::add);

        return allMessages.stream()
                .sorted(Comparator.comparing(TimelineMessage::getTimestamp).reversed())
                .collect(Collectors.toList());
    }
}
