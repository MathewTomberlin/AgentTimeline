package com.agenttimeline.service;

import com.agenttimeline.model.OllamaRequest;
import com.agenttimeline.model.OllamaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class OllamaService {

    private final WebClient webClient;

    @Value("${ollama.model:llama2}")
    private String defaultModel;

    public Mono<OllamaResponse> generateResponse(String prompt) {
        OllamaRequest request = new OllamaRequest(defaultModel, prompt, false);

        return webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OllamaResponse.class)
                .doOnNext(response -> log.debug("Ollama response received: {}", response))
                .doOnError(error -> log.error("Error calling Ollama API", error));
    }
}
