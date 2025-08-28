package com.agenttimeline.repository;

import com.agenttimeline.model.MessageChunkEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageChunkEmbeddingRepository extends JpaRepository<MessageChunkEmbedding, Long> {

    /**
     * Find chunks by message ID
     */
    List<MessageChunkEmbedding> findByMessageId(String messageId);

    /**
     * Find chunks by session ID
     */
    List<MessageChunkEmbedding> findBySessionId(String sessionId);

    /**
     * Delete all chunks for a specific message
     */
    void deleteByMessageId(String messageId);

    /**
     * Delete all chunks for a specific session
     */
    void deleteBySessionId(String sessionId);

    /**
     * Count chunks for a specific message
     */
    long countByMessageId(String messageId);

    /**
     * Count chunks for a specific session
     */
    long countBySessionId(String sessionId);
}
