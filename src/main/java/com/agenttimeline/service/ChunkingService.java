package com.agenttimeline.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ChunkingService {

    // Approximate characters per token (rough estimation)
    private static final int CHARS_PER_TOKEN = 4;
    private static final int DEFAULT_CHUNK_SIZE = 256; // tokens
    private static final int DEFAULT_OVERLAP_SIZE = 50; // tokens
    private static final int MIN_CHUNK_SIZE = 50; // tokens
    private static final int MAX_CHUNK_SIZE = 1000; // tokens

    /**
     * Split text into chunks with optional overlap
     * @param text The text to chunk
     * @param chunkSizeTokens Target chunk size in tokens
     * @param overlapTokens Overlap between chunks in tokens
     * @param useOverlap Whether to use overlapping chunks
     * @return List of text chunks
     */
    public List<String> chunkText(String text, int chunkSizeTokens, int overlapTokens, boolean useOverlap) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // Validate parameters
        int effectiveChunkSize = Math.max(MIN_CHUNK_SIZE, Math.min(MAX_CHUNK_SIZE, chunkSizeTokens));
        int effectiveOverlap = useOverlap ? Math.min(overlapTokens, effectiveChunkSize / 2) : 0;

        List<String> chunks = new ArrayList<>();
        int textLength = text.length();
        int currentPosition = 0;

        // If text fits in one chunk, don't create overlapping chunks
        int effectiveChunkChars = effectiveChunkSize * CHARS_PER_TOKEN;
        if (textLength <= effectiveChunkChars) {
            chunks.add(text.trim());
            log.info("Text fits in single chunk: {} chars (limit: {} chars)",
                textLength, effectiveChunkChars);
            return chunks;
        }

        log.debug("Chunking text: length={}, chunkSizeTokens={}, overlapTokens={}, useOverlap={}",
            textLength, chunkSizeTokens, overlapTokens, useOverlap);
        log.debug("Effective values: chunkSize={}, overlap={}, chunkChars={}",
            effectiveChunkSize, effectiveOverlap, effectiveChunkChars);

        while (currentPosition < textLength) {
            // Calculate chunk end position
            int chunkEnd = calculateChunkEnd(text, currentPosition, effectiveChunkSize);

            // Extract chunk
            String chunk = text.substring(currentPosition, Math.min(chunkEnd, textLength));
            if (!chunk.trim().isEmpty()) {
                chunks.add(chunk.trim());
                log.debug("Created chunk {}: {} chars, {} tokens",
                    chunks.size(), chunk.length(), estimateTokens(chunk));
            }

            // Move position for next chunk
            if (useOverlap && chunkEnd < textLength) {
                // Only use overlap if there's more text to process
                int overlapChars = effectiveOverlap * CHARS_PER_TOKEN;
                int nextPosition = Math.max(0, chunkEnd - overlapChars);

                // Ensure we move forward
                currentPosition = Math.max(currentPosition + 1, nextPosition);

                // Safety check: if we can't advance meaningfully, disable overlap
                if (currentPosition >= chunkEnd || currentPosition >= textLength - 10) {
                    currentPosition = chunkEnd;
                }

                log.debug("Overlap: chunkEnd={}, overlapChars={}, nextPosition={}, currentPosition={}",
                    chunkEnd, overlapChars, nextPosition, currentPosition);
            } else {
                currentPosition = chunkEnd;
            }

            // Prevent infinite loop
            if (currentPosition >= textLength) {
                break;
            }
        }

        log.info("Split text into {} chunks (total: {} chars, ~{} tokens)",
            chunks.size(), textLength, estimateTokens(text));
        return chunks;
    }

    /**
     * Split text into chunks with default settings (256 tokens, no overlap)
     */
    public List<String> chunkText(String text) {
        return chunkText(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE, false);
    }

    /**
     * Split text into overlapping chunks for better context preservation
     */
    public List<String> chunkTextWithOverlap(String text) {
        return chunkText(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE, true);
    }

    /**
     * Calculate the end position of a chunk, trying to break at word boundaries
     */
    private int calculateChunkEnd(String text, int startPosition, int chunkSizeTokens) {
        int targetEnd = startPosition + (chunkSizeTokens * CHARS_PER_TOKEN);

        // If we're at or near the end, return the text length
        if (targetEnd >= text.length()) {
            return text.length();
        }

        // Try to find a good break point near the target end
        int searchStart = Math.max(startPosition, targetEnd - 100); // Look back up to 100 chars
        int searchEnd = Math.min(text.length(), targetEnd + 100);   // Look forward up to 100 chars

        // Look for sentence endings first
        for (int i = searchStart; i < searchEnd && i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                // Check if followed by whitespace or end of text
                if (i + 1 >= text.length() || Character.isWhitespace(text.charAt(i + 1))) {
                    return i + 1;
                }
            }
        }

        // Look for word boundaries
        for (int i = searchStart; i < searchEnd && i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }

        // If no good break point found, use the target end
        return targetEnd;
    }

    /**
     * Estimate the number of tokens in a text (rough approximation)
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / (double) CHARS_PER_TOKEN);
    }

    /**
     * Validate chunk size parameters
     */
    public boolean isValidChunkSize(int chunkSizeTokens) {
        return chunkSizeTokens >= MIN_CHUNK_SIZE && chunkSizeTokens <= MAX_CHUNK_SIZE;
    }
}
