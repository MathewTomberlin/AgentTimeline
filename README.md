# AgentTimeline

AI-powered timeline management system that integrates with Ollama for AI responses and stores conversation history in Redis.

## Prerequisites

1. **Java 17+** - Download from [Oracle](https://www.oracle.com/java/technologies/downloads/) or use OpenJDK
2. **Maven 3.6+** - Download from [Apache Maven](https://maven.apache.org/download.cgi)
3. **Ollama** - Install from [ollama.ai](https://ollama.ai) and pull a model:
   ```bash
   ollama pull llama2
   ```
4. **Redis** - Install Redis server (see installation instructions below)

## Redis Installation (Windows)

Since the download was interrupted, you can manually download and install Redis:

1. **Download Redis for Windows**:
   - Go to: https://github.com/microsoftarchive/redis/releases/download/win-3.2.100/Redis-x64-3.2.100.zip
   - Or use a more recent version from: https://redis.io/download

2. **Extract and Setup**:
   ```bash
   # Extract the zip file to a folder (e.g., C:\Redis)
   # Open Command Prompt as Administrator
   cd C:\Redis
   redis-server.exe --service-install
   redis-server.exe --service-start
   ```

3. **Alternative - Using Chocolatey** (if you have it installed):
   ```bash
   choco install redis-64
   ```

4. **Verify Installation**:
   ```bash
   redis-cli ping
   # Should return: PONG
   ```

## Quick Start

1. **Clone/Build the project**:
   ```bash
   cd agent-timeline
   mvn clean install
   ```

2. **Start Ollama** (in a separate terminal):
   ```bash
   ollama serve
   ```

3. **Start Redis** (if not running as service):
   ```bash
   redis-server
   ```

4. **Run the Application**:
   ```bash
   mvn spring-boot:run
   ```

5. **Test the API**:
   ```bash
   # Health check
   curl http://localhost:8080/api/v1/timeline/health

   # Send a chat message
   curl -X POST http://localhost:8080/api/v1/timeline/chat \
        -H "Content-Type: application/json" \
        -d '{"message": "Hello, how are you?"}'

   # Get all messages
   curl http://localhost:8080/api/v1/timeline/messages

   # Get session messages
   curl http://localhost:8080/api/v1/timeline/session/default
   ```

## API Endpoints

- `POST /timeline/chat` - Send a message and get AI response
- `GET /timeline/messages` - Get all timeline messages
- `GET /timeline/session/{sessionId}` - Get messages for specific session
- `GET /timeline/health` - Health check endpoint

## Configuration

The application uses `application.yml` for configuration:

- **Server**: Runs on port 8080 with context path `/api/v1`
- **Redis**: Connects to localhost:6379
- **Ollama**: Connects to localhost:11434 using llama2 model
- **CORS**: Allows requests from localhost:3000 and localhost:8080

## Architecture

- **TimelineController**: REST API endpoints
- **TimelineService**: Business logic orchestration
- **OllamaService**: AI model integration
- **TimelineRepository**: Redis data persistence
- **TimelineMessage**: Data model for chat messages

## Development

```bash
# Run with hot reload
mvn spring-boot:run

# Run tests
mvn test

# Build for production
mvn clean package
```

### Utility Scripts

The project includes several utility scripts in the `scripts/` directory:

#### Clear Messages
Clear stored conversation messages from Redis (useful for testing):

```bash
# PowerShell (recommended)
.\scripts\clear-messages.ps1

# Batch
scripts\clear-messages.bat
```

#### Test Scripts
Run comprehensive API tests:

```bash
# Run all tests
.\scripts\run-tests.ps1

# Test individual endpoints
.\scripts\tests\test-health.ps1
.\scripts\tests\test-chat.ps1
.\scripts\tests\test-get-messages.ps1
```

#### Start Scripts
Launch all services at once:

```bash
# PowerShell (recommended)
.\scripts\start-full.ps1

# Batch (keep windows open!)
scripts\start-full-simple.bat
```

## Next Steps

This is Phase 1 implementation with basic functionality. Future phases may include:
- User authentication
- WebSocket support for real-time chat
- Message search and filtering
- Multiple AI model support
- Data analytics and insights
