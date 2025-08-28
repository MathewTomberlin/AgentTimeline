package com.agenttimeline.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingServiceTest {

    // Note: These tests are simplified since WebClient mocking is complex
    // In a real scenario, you'd use @SpringBootTest with test containers or wiremock

    @Test
    void testCosineSimilarityCalculation() {
        // Test the similarity calculation logic without WebClient
        EmbeddingService service = new EmbeddingService(null);

        double[] a = {1.0, 2.0, 3.0};
        double[] b = {1.0, 2.0, 3.0};
        double similarity = service.cosineSimilarity(a, b);

        assertEquals(1.0, similarity, 0.001);
    }

    @Test
    void testCosineSimilarityDifferentVectors() {
        EmbeddingService service = new EmbeddingService(null);

        double[] a = {1.0, 0.0, 0.0};
        double[] b = {0.0, 1.0, 0.0};
        double similarity = service.cosineSimilarity(a, b);

        assertEquals(0.0, similarity, 0.001);
    }

    @Test
    void testCosineSimilarityPartialSimilarity() {
        EmbeddingService service = new EmbeddingService(null);

        double[] a = {1.0, 2.0, 0.0};
        double[] b = {1.0, 0.0, 2.0};
        double similarity = service.cosineSimilarity(a, b);

        // Should be less than 1.0 but greater than 0.0
        assertTrue(similarity > 0.0 && similarity < 1.0);
    }

    @Test
    void testCosineSimilarityZeroVector() {
        EmbeddingService service = new EmbeddingService(null);

        double[] a = {0.0, 0.0, 0.0};
        double[] b = {1.0, 2.0, 3.0};
        double similarity = service.cosineSimilarity(a, b);

        assertEquals(0.0, similarity, 0.001);
    }
}
