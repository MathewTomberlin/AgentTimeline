package com.agenttimeline.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;

@Entity
@Table(name = "message_chunk_embeddings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageChunkEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(name = "chunk_text", columnDefinition = "TEXT", nullable = false)
    private String chunkText;

    @Column(name = "embedding_vector", columnDefinition = "LONGTEXT")
    private String embeddingVectorJson;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Helper methods for embedding vector conversion
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Transient
    private double[] embeddingVector;

    public double[] getEmbeddingVector() {
        if (embeddingVector != null) {
            return embeddingVector;
        }
        if (embeddingVectorJson != null && !embeddingVectorJson.isEmpty()) {
            try {
                return objectMapper.readValue(embeddingVectorJson, double[].class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to deserialize embedding vector", e);
            }
        }
        return null;
    }

    public void setEmbeddingVector(double[] embeddingVector) {
        this.embeddingVector = embeddingVector;
        if (embeddingVector != null) {
            try {
                this.embeddingVectorJson = objectMapper.writeValueAsString(embeddingVector);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize embedding vector", e);
            }
        } else {
            this.embeddingVectorJson = null;
        }
    }

    public void setEmbeddingVectorJson(String embeddingVectorJson) {
        this.embeddingVectorJson = embeddingVectorJson;
    }
}
