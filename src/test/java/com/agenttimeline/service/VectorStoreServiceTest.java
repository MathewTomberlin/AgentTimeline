package com.agenttimeline.service;

import com.agenttimeline.model.MessageChunkEmbedding;
import com.agenttimeline.repository.MessageChunkEmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VectorStoreServiceTest {

    @Mock
    private ChunkingService chunkingService;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private MessageChunkEmbeddingRepository repository;

    private VectorStoreService vectorStoreService;

    @BeforeEach
    void setUp() {
        vectorStoreService = new VectorStoreService(chunkingService, embeddingService, repository);
    }

    @Test
    void testProcessAndStoreMessage_Success() {
        // Setup mock data
        String messageId = "msg-123";
        String messageText = "This is a test message for chunking and embedding.";
        String sessionId = "session-456";

        List<String> mockChunks = List.of("This is a test", "message for chunking", "and embedding.");
        List<double[]> mockEmbeddings = List.of(
            new double[]{0.1, 0.2, 0.3},
            new double[]{0.4, 0.5, 0.6},
            new double[]{0.7, 0.8, 0.9}
        );

        when(chunkingService.chunkTextWithOverlap(messageText)).thenReturn(mockChunks);
        when(embeddingService.generateEmbeddings(mockChunks)).thenReturn(mockEmbeddings);
        when(repository.saveAll(anyList())).thenReturn(mockChunks.stream()
            .map(chunk -> MessageChunkEmbedding.builder().build())
            .toList());

        int result = vectorStoreService.processAndStoreMessage(messageId, messageText, sessionId);

        assertEquals(3, result);
        verify(chunkingService).chunkTextWithOverlap(messageText);
        verify(embeddingService).generateEmbeddings(mockChunks);
        verify(repository).saveAll(argThat(chunks -> {
            if (chunks instanceof List) {
                return ((List<?>) chunks).size() == 3;
            }
            return false;
        }));
    }

    @Test
    void testProcessAndStoreMessage_EmptyChunks() {
        String messageId = "msg-123";
        String messageText = "Short message";
        String sessionId = "session-456";

        when(chunkingService.chunkTextWithOverlap(messageText)).thenReturn(List.of());

        int result = vectorStoreService.processAndStoreMessage(messageId, messageText, sessionId);

        assertEquals(0, result);
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void testFindSimilarChunks_SessionSpecific() {
        String query = "test query";
        String sessionId = "session-456";
        int limit = 5;

        double[] queryEmbedding = {0.1, 0.2, 0.3};
        List<MessageChunkEmbedding> sessionChunks = List.of(
            MessageChunkEmbedding.builder()
                .chunkText("similar chunk 1")
                .embeddingVector(new double[]{0.1, 0.2, 0.3})
                .build(),
            MessageChunkEmbedding.builder()
                .chunkText("similar chunk 2")
                .embeddingVector(new double[]{0.15, 0.25, 0.35})
                .build(),
            MessageChunkEmbedding.builder()
                .chunkText("different chunk")
                .embeddingVector(new double[]{0.8, 0.9, 1.0})
                .build()
        );

        when(embeddingService.generateQueryEmbedding(query)).thenReturn(queryEmbedding);
        when(repository.findBySessionId(sessionId)).thenReturn(sessionChunks);

        List<MessageChunkEmbedding> results = vectorStoreService.findSimilarChunks(query, sessionId, limit);

        assertNotNull(results);
        assertEquals(3, results.size()); // Should return all chunks sorted by similarity
        verify(embeddingService).generateQueryEmbedding(query);
        verify(repository).findBySessionId(sessionId);
    }

    @Test
    void testFindSimilarChunks_Global() {
        String query = "test query";
        int limit = 10;

        double[] queryEmbedding = {0.1, 0.2, 0.3};
        List<MessageChunkEmbedding> allChunks = List.of(
            MessageChunkEmbedding.builder()
                .chunkText("global chunk 1")
                .embeddingVector(new double[]{0.1, 0.2, 0.3})
                .build(),
            MessageChunkEmbedding.builder()
                .chunkText("global chunk 2")
                .embeddingVector(new double[]{0.15, 0.25, 0.35})
                .build(),
            MessageChunkEmbedding.builder()
                .chunkText("different chunk")
                .embeddingVector(new double[]{0.8, 0.9, 1.0})
                .build()
        );

        when(embeddingService.generateQueryEmbedding(query)).thenReturn(queryEmbedding);
        when(repository.findAll()).thenReturn(allChunks);

        List<MessageChunkEmbedding> results = vectorStoreService.findSimilarChunksGlobal(query, limit);

        assertNotNull(results);
        assertEquals(3, results.size()); // Should return all 3 chunks (limit is 10, more than available)
        verify(embeddingService).generateQueryEmbedding(query);
        verify(repository).findAll();
    }

    @Test
    void testFindSimilarChunks_EmptyQueryEmbedding() {
        String query = "";
        String sessionId = "session-456";

        when(embeddingService.generateQueryEmbedding(query)).thenReturn(new double[0]);

        List<MessageChunkEmbedding> results = vectorStoreService.findSimilarChunks(query, sessionId, 5);

        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(repository, never()).findBySessionId(anyString());
    }

    @Test
    void testFindSimilarChunksWithinThreshold() {
        String query = "test query";
        String sessionId = "session-456";
        double threshold = 0.7;

        double[] queryEmbedding = {0.1, 0.2, 0.3};
        List<MessageChunkEmbedding> sessionChunks = List.of(
            MessageChunkEmbedding.builder()
                .chunkText("threshold chunk")
                .embeddingVector(new double[]{0.1, 0.2, 0.3}) // Very similar to query
                .build(),
            MessageChunkEmbedding.builder()
                .chunkText("different chunk")
                .embeddingVector(new double[]{0.8, 0.9, 1.0}) // Not similar enough
                .build()
        );

        when(embeddingService.generateQueryEmbedding(query)).thenReturn(queryEmbedding);
        when(repository.findBySessionId(sessionId)).thenReturn(sessionChunks);

        List<MessageChunkEmbedding> results = vectorStoreService.findSimilarChunksWithinThreshold(query, sessionId, threshold);

        assertNotNull(results);
        assertEquals(2, results.size()); // Both similar chunks should pass the 0.7 threshold
        verify(repository).findBySessionId(sessionId);
    }

    @Test
    void testGetChunksForMessage() {
        String messageId = "msg-123";
        List<MessageChunkEmbedding> mockChunks = List.of(
            MessageChunkEmbedding.builder().chunkText("chunk 1").build(),
            MessageChunkEmbedding.builder().chunkText("chunk 2").build()
        );

        when(repository.findByMessageId(messageId)).thenReturn(mockChunks);

        List<MessageChunkEmbedding> results = vectorStoreService.getChunksForMessage(messageId);

        assertNotNull(results);
        assertEquals(2, results.size());
        verify(repository).findByMessageId(messageId);
    }

    @Test
    void testGetChunksForSession() {
        String sessionId = "session-456";
        List<MessageChunkEmbedding> mockChunks = List.of(
            MessageChunkEmbedding.builder().chunkText("session chunk 1").build(),
            MessageChunkEmbedding.builder().chunkText("session chunk 2").build()
        );

        when(repository.findBySessionId(sessionId)).thenReturn(mockChunks);

        List<MessageChunkEmbedding> results = vectorStoreService.getChunksForSession(sessionId);

        assertNotNull(results);
        assertEquals(2, results.size());
        verify(repository).findBySessionId(sessionId);
    }

    @Test
    void testDeleteChunksForMessage() {
        String messageId = "msg-123";

        when(repository.countByMessageId(messageId)).thenReturn(3L);

        vectorStoreService.deleteChunksForMessage(messageId);

        verify(repository).countByMessageId(messageId);
        verify(repository).deleteByMessageId(messageId);
    }

    @Test
    void testDeleteChunksForSession() {
        String sessionId = "session-456";

        when(repository.countBySessionId(sessionId)).thenReturn(5L);

        vectorStoreService.deleteChunksForSession(sessionId);

        verify(repository).countBySessionId(sessionId);
        verify(repository).deleteBySessionId(sessionId);
    }

    @Test
    void testGetStatistics() {
        List<MessageChunkEmbedding> mockChunks = List.of(
            MessageChunkEmbedding.builder().messageId("msg1").sessionId("session1").build(),
            MessageChunkEmbedding.builder().messageId("msg2").sessionId("session1").build(),
            MessageChunkEmbedding.builder().messageId("msg3").sessionId("session2").build()
        );

        when(repository.findAll()).thenReturn(mockChunks);
        when(repository.count()).thenReturn(3L);

        VectorStoreService.VectorStoreStatistics stats = vectorStoreService.getStatistics();

        assertNotNull(stats);
        assertEquals(3, stats.totalChunks());
        assertEquals(3, stats.uniqueMessages()); // 3 unique message IDs
        assertEquals(2, stats.uniqueSessions()); // 2 unique session IDs
    }
}
