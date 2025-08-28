# AgentTimeline Implementation Guide

## Phase 1: Core Infrastructure Setup

### 1. Install and Setup Prerequisites

#### Required Software
- **Java Development Kit (JDK) 17 or higher**
  ```bash
  # Download and install from Oracle or OpenJDK
  # Verify installation
  java -version
  javac -version
  ```

- **Apache Maven 3.6+**
  ```bash
  # Download from https://maven.apache.org/download.cgi
  # Or use package manager (Ubuntu/Debian)
  sudo apt-get install maven

  # Verify installation
  mvn -version
  ```

- **Ollama AI Model Server**
  ```bash
  # Install Ollama
  curl -fsSL https://ollama.ai/install.sh | sh

  # Pull required model (e.g., llama2)
  ollama pull llama2

  # Start Ollama service
  ollama serve
  ```

- **Redis Database**
  ```bash
  # Install Redis (Ubuntu/Debian)
  sudo apt-get install redis-server

  # Start Redis service
  sudo systemctl start redis-server
  sudo systemctl enable redis-server

  # Verify installation
  redis-cli ping
  ```

- **Git**
  ```bash
  # Install Git
  sudo apt-get install git

  # Configure Git
  git config --global user.name "Your Name"
  git config --global user.email "your.email@example.com"
  ```

#### Development Environment
- **IntelliJ IDEA** or **Eclipse IDE** with Spring Boot support
- **Postman** or similar API testing tool
- **Redis Desktop Manager** (optional, for Redis visualization)

### 2. Spring Boot Project Setup

#### Create New Spring Boot Project

```bash
# Create project directory
mkdir agent-timeline
cd agent-timeline

# Initialize Git repository
git init
git checkout -b develop

# Create Maven project structure
mvn archetype:generate \
  -DgroupId=com.agenttimeline \
  -DartifactId=agent-timeline \
  -DarchetypeArtifactId=maven-archetype-quickstart \
  -DinteractiveMode=false
```

#### Update pom.xml Dependencies

```xml:pom.xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.agenttimeline</groupId>
    <artifactId>agent-timeline</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>Agent Timeline</name>
    <description>AI-powered timeline management system</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- HTTP Client for Ollama -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- JSON Processing -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <!-- Development Tools -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-devtools</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

#### Application Configuration

Create `src/main/resources/application.yml`:

```yaml:src/main/resources/application.yml
spring:
  application:
    name: agent-timeline

  # Redis Configuration
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0

  # Web Configuration
  web:
    cors:
      allowed-origins: http://localhost:3000, http://localhost:8080
      allowed-methods: GET, POST, PUT, DELETE, OPTIONS
      allowed-headers: "*"

# Ollama Configuration
ollama:
  base-url: http://localhost:11434
  model: llama2
  timeout: 30000

# Logging Configuration
logging:
  level:
    com.agenttimeline: DEBUG
    org.springframework.data.redis: DEBUG
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

server:
  port: 8080
  servlet:
    context-path: /api/v1
```

#### Project Structure Setup

```bash
# Create main package structure
mkdir -p src/main/java/com/agenttimeline/{controller,service,model,config,repository}
mkdir -p src/main/resources
mkdir -p src/test/java/com/agenttimeline
```

### 3. TimelineController: Ollama Integration with Redis Storage

#### Create Data Models

```java:src/main/java/com/agenttimeline/model/TimelineMessage.java
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
```

```java:src/main/java/com/agenttimeline/model/OllamaRequest.java
package com.agenttimeline.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OllamaRequest {
    private String model;
    private String prompt;
    private boolean stream = false;
}
```

```java:src/main/java/com/agenttimeline/model/OllamaResponse.java
package com.agenttimeline.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OllamaResponse {
    private String response;
    private boolean done;
    private String model;
    private long total_duration;
    private long load_duration;
    private int prompt_eval_count;
    private long prompt_eval_duration;
    private int eval_count;
    private long eval_duration;
}
```

#### Create Repository Layer

```java:src/main/java/com/agenttimeline/repository/TimelineRepository.java
package com.agenttimeline.repository;

import com.agenttimeline.model.TimelineMessage;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimelineRepository extends CrudRepository<TimelineMessage, String> {
    List<TimelineMessage> findBySessionIdOrderByTimestampDesc(String sessionId);
    List<TimelineMessage> findAllByOrderByTimestampDesc();
}
```

#### Create Service Layer

```java:src/main/java/com/agenttimeline/service/OllamaService.java
package com.agenttimeline.service;

import com.agenttimeline.model.OllamaRequest;
import com.agenttimeline.model.OllamaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class OllamaService {

    private final WebClient webClient;

    @Value("${ollama.model:llama2}")
    private String defaultModel;

    public Mono<OllamaResponse> generateResponse(String prompt) {
        OllamaRequest request = new OllamaRequest(defaultModel, prompt, false);

        return webClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OllamaResponse.class)
                .doOnNext(response -> log.debug("Ollama response received: {}", response))
                .doOnError(error -> log.error("Error calling Ollama API", error));
    }
}
```

```java:src/main/java/com/agenttimeline/service/TimelineService.java
package com.agenttimeline.service;

import com.agenttimeline.model.OllamaResponse;
import com.agenttimeline.model.TimelineMessage;
import com.agenttimeline.repository.TimelineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimelineService {

    private final OllamaService ollamaService;
    private final TimelineRepository timelineRepository;

    public Mono<TimelineMessage> processUserMessage(String userMessage, String sessionId) {
        String messageId = UUID.randomUUID().toString();
        LocalDateTime startTime = LocalDateTime.now();

        return ollamaService.generateResponse(userMessage)
                .map(ollamaResponse -> {
                    LocalDateTime endTime = LocalDateTime.now();
                    long responseTime = java.time.Duration.between(startTime, endTime).toMillis();

                    TimelineMessage timelineMessage = new TimelineMessage();
                    timelineMessage.setId(messageId);
                    timelineMessage.setSessionId(sessionId);
                    timelineMessage.setUserMessage(userMessage);
                    timelineMessage.setAssistantResponse(ollamaResponse.getResponse());
                    timelineMessage.setTimestamp(startTime);
                    timelineMessage.setModelUsed(ollamaResponse.getModel());
                    timelineMessage.setResponseTime(responseTime);

                    return timelineMessage;
                })
                .doOnNext(message -> {
                    timelineRepository.save(message);
                    log.info("Saved timeline message with ID: {}", message.getId());
                });
    }

    public List<TimelineMessage> getSessionMessages(String sessionId) {
        return timelineRepository.findBySessionIdOrderByTimestampDesc(sessionId);
    }

    public List<TimelineMessage> getAllMessages() {
        return timelineRepository.findAllByOrderByTimestampDesc();
    }
}
```

#### Create Configuration Classes

```java:src/main/java/com/agenttimeline/config/WebClientConfig.java
package com.agenttimeline.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
@Slf4j
public class WebClientConfig {

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.timeout:30000}")
    private int timeoutMillis;

    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(timeoutMillis));

        return WebClient.builder()
                .baseUrl(ollamaBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }
}
```

```java:src/main/java/com/agenttimeline/config/RedisConfig.java
package com.agenttimeline.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Use JSON serializer for values
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }
}
```

#### Create TimelineController

```java:src/main/java/com/agenttimeline/controller/TimelineController.java
package com.agenttimeline.controller;

import com.agenttimeline.model.TimelineMessage;
import com.agenttimeline.service.TimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/timeline")
@RequiredArgsConstructor
@Slf4j
public class TimelineController {

    private final TimelineService timelineService;

    @PostMapping("/chat")
    public Mono<ResponseEntity<TimelineMessage>> chat(
            @RequestBody Map<String, String> request,
            @RequestParam(defaultValue = "default") String sessionId) {

        String userMessage = request.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        log.info("Processing chat request for session: {}", sessionId);

        return timelineService.processUserMessage(userMessage, sessionId)
                .map(message -> ResponseEntity.ok(message))
                .doOnError(error -> log.error("Error processing chat request", error))
                .onErrorResume(error -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<TimelineMessage>> getSessionMessages(@PathVariable String sessionId) {
        try {
            List<TimelineMessage> messages = timelineService.getSessionMessages(sessionId);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error retrieving session messages", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/messages")
    public ResponseEntity<List<TimelineMessage>> getAllMessages() {
        try {
            List<TimelineMessage> messages = timelineService.getAllMessages();
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.error("Error retrieving all messages", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "AgentTimeline API",
            "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }
}
```

#### Create Main Application Class

```java:src/main/java/com/agenttimeline/AgentTimelineApplication.java
package com.agenttimeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AgentTimelineApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentTimelineApplication.class, args);
    }
}
```

## Next Steps for Phase 1

1. **Test the Application**
   ```bash
   # Run the application
   mvn spring-boot:run

   # Test health endpoint
   curl http://localhost:8080/api/v1/timeline/health

   # Test chat endpoint
   curl -X POST http://localhost:8080/api/v1/timeline/chat \
        -H "Content-Type: application/json" \
        -d '{"message": "Hello, how are you?"}'
   ```

2. **Verify Redis Storage**
   ```bash
   # Connect to Redis
   redis-cli

   # Check stored data
   KEYS *
   HGETALL "timeline_message:<message_id>"
   ```

3. **Update IMPLEMENTATION_STATUS.md**
   - Document Phase 1 completion
   - Note any issues encountered
   - Plan Phase 2 objectives

## Phase 1 Checklist

- [ ] Prerequisites installed (Java, Maven, Ollama, Redis, Git)
- [ ] Spring Boot project created with proper dependencies
- [ ] Configuration files created (application.yml)
- [ ] Data models implemented (TimelineMessage, OllamaRequest, OllamaResponse)
- [ ] Repository layer configured (TimelineRepository)
- [ ] Service layer implemented (OllamaService, TimelineService)
- [ ] Configuration classes created (WebClientConfig, RedisConfig)
- [ ] TimelineController implemented with all endpoints
- [ ] Application runs successfully
- [ ] Basic functionality tested (health check, chat endpoint)
- [ ] Data persistence verified in Redis
- [ ] IMPLEMENTATION_STATUS.md updated

---

**Note**: This completes Phase 1 of the AgentTimeline implementation. The core infrastructure is now in place with Ollama AI integration and Redis storage capabilities.
