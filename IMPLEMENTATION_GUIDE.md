# AgentTimeline Implementation Guide

## Phase 1: Core Infrastructure Setup - COMPLETED ✅

Phase 1 has been successfully implemented with the following core components:

- **Spring Boot Application** with Maven dependencies
- **Redis Integration** for data persistence
- **Ollama AI Service** integration for AI responses
- **REST API** with chat, session management, and health check endpoints
- **Data Models** for timeline messages and Ollama communication

### Phase 1 Completed Checklist

- [x] Prerequisites installed (Java, Maven, Ollama, Redis, Git)
- [x] Spring Boot project created with proper dependencies
- [x] Configuration files created (application.yml)
- [x] Data models implemented (TimelineMessage, OllamaRequest, OllamaResponse)
- [x] Repository layer configured (TimelineRepository)
- [x] Service layer implemented (OllamaService, TimelineService)
- [x] Configuration classes created (WebClientConfig, RedisConfig)
- [x] TimelineController implemented with all endpoints
- [x] Application runs successfully
- [x] Basic functionality tested (health check, chat endpoint)
- [x] Data persistence verified in Redis
- [x] IMPLEMENTATION_STATUS.md updated

## Phase 2: Enhanced Message Storage and Retrieval

### Overview
Implement separate storage and retrieval of user messages and assistant responses using a unified message model with role-based differentiation. This will provide better data organization and enable more flexible conversation management through message chaining.

### Requirements

#### 1. Update Data Model
- Create a new `Message` entity to replace the current combined `TimelineMessage`
- Include fields: `id`, `sessionId`, `role` (USER/ASSISTANT), `content`, `timestamp`, `metadata`
- **Add message chaining fields**: `parentMessageId` (references the previous message in conversation)
- The `role` field will distinguish between user messages and assistant responses
- **Message Chain Logic**: Each message (except the first) should reference its parent message, creating a linked list structure for efficient conversation reconstruction

#### 2. Database Schema Updates
- Modify Redis storage structure to accommodate separate messages
- Update repository methods to handle role-based queries
- Ensure backward compatibility with existing data if needed

#### 3. Service Layer Updates
- Update `TimelineService` to save user and assistant messages separately
- **Implement message chaining logic**: When saving a new message, set its `parentMessageId` to reference the previous message in the conversation
- Implement methods to retrieve conversation threads by session using message chain traversal
- Add functionality to reconstruct full conversations by following the message chain (no timestamp sorting needed)
- **Conversation Flow**: User message → Assistant response → User message (with parentMessageId pointing to assistant response) → Assistant response (with parentMessageId pointing to previous user message)

#### 4. API Updates
- Modify chat endpoint to store user message first, then assistant response with proper message chaining
- **Message Chain Creation**: When processing a chat request, store user message, get assistant response, then store assistant message with `parentMessageId` pointing to the user message
- Add endpoint to retrieve conversation history by session using message chain traversal (efficient reconstruction without sorting)
- Ensure all existing endpoints remain functional during transition
- **Chain Validation**: Add logic to verify message chain integrity and handle broken chains gracefully

### Implementation Steps

1. **Create New Message Model**
   - Design `Message` class with role enumeration (USER/ASSISTANT)
   - Include fields: `id`, `sessionId`, `role`, `content`, `timestamp`, `parentMessageId`, `metadata`
   - **Message Chain Structure**: `parentMessageId` should be null for the first message in each conversation
   - Include metadata for model information, response times, etc.

2. **Update Repository Layer**
   - Create `MessageRepository` with role-based query methods
   - Add method to find messages by session and reconstruct conversation chain
   - **Chain Traversal Method**: Implement `findConversationChain(sessionId)` that follows `parentMessageId` references
   - Implement session-based message retrieval with chain ordering

3. **Update Service Layer**
   - Modify `TimelineService` to handle separate message storage with chaining
   - **Message Chain Logic**: When saving messages, set `parentMessageId` to maintain conversation flow
   - Add conversation reconstruction logic that follows message chains instead of sorting by timestamp
   - **Chain Management**: Track the last message ID in each session for efficient chaining

4. **Update Controller**
   - Modify chat endpoint to store user message first, then assistant response with proper chaining
   - **Chain Creation Flow**: Store user message → Get AI response → Store assistant message with `parentMessageId` pointing to user message
   - Add conversation history endpoint that uses message chain traversal for efficient reconstruction
   - **Chain Validation**: Add error handling for broken message chains

5. **Testing and Validation**
   - Test message storage and retrieval with proper chaining
   - Verify conversation reconstruction follows the correct message chain
   - Test edge cases: broken chains, missing parent messages, concurrent conversations
   - Ensure data integrity and proper chain maintenance during high-load scenarios

### Phase 2 Checklist

- [ ] New `Message` model created with role field and `parentMessageId` for chaining
- [ ] `MessageRepository` implemented with role-based queries and chain traversal methods
- [ ] `TimelineService` updated for separate message storage with message chaining logic
- [ ] Chat endpoint modified to store user/assistant messages separately with proper chaining
- [ ] Conversation history endpoint added using message chain traversal
- [ ] Message chain validation and error handling implemented
- [ ] Conversation reconstruction tested (follows message chains correctly)
- [ ] Data migration strategy implemented (if needed)
- [ ] Documentation updated with message chaining details

---

**Next**: Begin implementation of Phase 2 by creating the new Message model and updating the storage architecture.


