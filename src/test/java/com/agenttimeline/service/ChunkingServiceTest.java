package com.agenttimeline.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ChunkingServiceTest {

    private ChunkingService chunkingService;

    @BeforeEach
    void setUp() {
        chunkingService = new ChunkingService();
    }

    @Test
    void testChunkText_EmptyText() {
        List<String> chunks = chunkingService.chunkText("");
        assertTrue(chunks.isEmpty());

        chunks = chunkingService.chunkText(null);
        assertTrue(chunks.isEmpty());
    }

    @Test
    void testChunkText_ShortText() {
        String text = "This is a short message.";
        List<String> chunks = chunkingService.chunkText(text);

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0));
    }

    @Test
    void testChunkText_LongText() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longText.append("This is a test sentence that should help with chunking. ");
        }
        String text = longText.toString();

        List<String> chunks = chunkingService.chunkText(text);

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() > 0));
        assertTrue(chunks.stream().allMatch(chunk -> !chunk.endsWith(" "))); // No trailing spaces
    }

    @Test
    void testChunkTextWithOverlap() {
        String text = "This is the first sentence. This is the second sentence. This is the third sentence.";
        List<String> chunks = chunkingService.chunkTextWithOverlap(text);

        assertTrue(chunks.size() >= 1);
        // With overlap, we should have some chunks that contain parts of multiple sentences
        boolean hasOverlappingContent = chunks.stream()
            .anyMatch(chunk -> chunk.contains("first") && chunk.contains("second"));

        if (chunks.size() > 1) {
            assertTrue(hasOverlappingContent, "Expected overlapping chunks but found none");
        }
    }

    @Test
    void testEstimateTokens() {
        String text = "This is a test message with some words.";
        int tokens = chunkingService.estimateTokens(text);

        assertTrue(tokens > 0);
        assertEquals(10, tokens); // "This is a test message with some words." has ~41 chars, ~10 tokens
    }

    @Test
    void testIsValidChunkSize() {
        assertTrue(chunkingService.isValidChunkSize(100));
        assertTrue(chunkingService.isValidChunkSize(256));
        assertTrue(chunkingService.isValidChunkSize(1000));

        assertFalse(chunkingService.isValidChunkSize(10)); // Too small
        assertFalse(chunkingService.isValidChunkSize(2000)); // Too large
    }

    @Test
    void testChunkingPreservesContent() {
        String originalText = "The quick brown fox jumps over the lazy dog. This is a test of the chunking service.";
        List<String> chunks = chunkingService.chunkText(originalText);

        String combinedText = String.join("", chunks);

        // All original words should be present (ignoring case and some punctuation differences)
        assertTrue(combinedText.toLowerCase().contains("quick"));
        assertTrue(combinedText.toLowerCase().contains("brown"));
        assertTrue(combinedText.toLowerCase().contains("fox"));
        assertTrue(combinedText.toLowerCase().contains("chunking"));
    }
}
