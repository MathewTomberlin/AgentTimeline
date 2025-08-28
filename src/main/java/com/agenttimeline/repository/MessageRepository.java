package com.agenttimeline.repository;

import com.agenttimeline.model.Message;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends CrudRepository<Message, String> {

    // Find all messages for a session
    List<Message> findBySessionId(String sessionId);

    // Find messages by session and role
    List<Message> findBySessionIdAndRole(String sessionId, Message.Role role);

    // Find messages by parent message ID (for chain traversal)
    List<Message> findByParentMessageId(String parentMessageId);

    // Find first message in a session (messages with no parent)
    List<Message> findBySessionIdAndParentMessageIdIsNull(String sessionId);

    // Find a specific message by ID
    Optional<Message> findById(String id);

    // Find all messages (for conversation reconstruction)
    List<Message> findAll();
}
