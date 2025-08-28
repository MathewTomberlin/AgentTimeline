package com.agenttimeline.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@RedisHash("message")
public class Message {
    @Id
    private String id;
    @Indexed
    private String sessionId;
    private Role role; // USER or ASSISTANT
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    @Indexed
    private String parentMessageId; // References the previous message in conversation chain
    private Map<String, Object> metadata; // For model info, response times, etc.

    public enum Role {
        USER,
        ASSISTANT
    }

    // Helper methods for message chain management
    public boolean isFirstMessage() {
        return parentMessageId == null;
    }

    public boolean hasParent() {
        return parentMessageId != null;
    }
}
