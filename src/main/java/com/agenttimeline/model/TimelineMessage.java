package com.agenttimeline.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@RedisHash("timeline_message")
public class TimelineMessage {
    @Id
    private String id;
    private String sessionId;
    private String userMessage;
    private String assistantResponse;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    private String modelUsed;
    private long responseTime; // in milliseconds
}
