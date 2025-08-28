package com.agenttimeline.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OllamaEmbeddingResponse {
    private String model;
    private List<Double> embedding;
    private boolean done;
}
