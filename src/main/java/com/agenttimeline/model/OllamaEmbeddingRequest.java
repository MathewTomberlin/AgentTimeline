package com.agenttimeline.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OllamaEmbeddingRequest {
    private String model;
    private String prompt;
    private boolean stream = false;
}
