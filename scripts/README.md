# AgentTimeline Scripts

This directory contains automation scripts for building, running, and testing the AgentTimeline application.

## 📁 Directory Structure

```
scripts/
├── build.bat           # Build application (Batch)
├── build.ps1           # Build application (PowerShell)
├── start-app.bat       # Start Spring Boot app (Batch)
├── start-app.ps1       # Start Spring Boot app (PowerShell)
├── start-full.bat      # Start all services (Batch)
├── start-full.ps1      # Start all services (PowerShell)
├── restart-full.ps1    # Restart all services (PowerShell)
├── run-tests.ps1       # Run all tests (PowerShell)
├── tests/              # Individual test scripts
│   ├── test-health.ps1
│   ├── test-chat.ps1
│   ├── test-get-messages.ps1
│   └── test-get-session-messages.ps1
└── README.md           # This file
```

## 🚀 Quick Start

### Option 1: Full Stack Start (RECOMMENDED - PowerShell)
```powershell
# Start everything at once (Redis + Ollama + Spring Boot)
# PowerShell version handles background processes properly!
.\scripts\start-full.ps1

# Run comprehensive tests
.\scripts\run-tests.ps1
```

### Option 2: Full Stack Start (Batch - Alternative)
```batch
# Batch version requires keeping windows open
.\scripts\start-full-simple.bat
# NOTE: Do NOT close the opened windows!
```

⚠️ **Important**: The PowerShell scripts (.ps1) are much more reliable for managing background services. Use them whenever possible!

### Option 3: Individual Services
```powershell
# Build the application
.\scripts\build.ps1

# Start services individually
# Terminal 1: Redis
.\redis\redis-server.exe --port 6379

# Terminal 2: Ollama
ollama serve

# Terminal 3: Application
.\scripts\start-app.ps1
```

## 📋 Available Scripts

### Build Scripts

#### `build.bat` / `build.ps1`
Compiles the Spring Boot application using Maven.

```batch
# Batch
scripts\build.bat

# PowerShell
.\scripts\build.ps1
```

### Start Scripts

#### `start-app.bat` / `start-app.ps1`
Starts only the Spring Boot application. Requires Redis and Ollama to be running.

```batch
# Batch
scripts\start-app.bat

# PowerShell
.\scripts\start-app.ps1
```

### Utility Scripts

#### `clear-messages.bat` / `clear-messages.ps1`
Clears all stored messages from Redis. Useful for testing and development.

```batch
# Batch
scripts\clear-messages.bat

# PowerShell - with confirmation
.\scripts\clear-messages.ps1

# PowerShell - force delete without confirmation
.\scripts\clear-messages.ps1 -Force

# PowerShell - quiet mode
.\scripts\clear-messages.ps1 -Quiet

# PowerShell - custom Redis host/port
.\scripts\clear-messages.ps1 -RedisHost "192.168.1.100" -RedisPort 6380

# PowerShell - specify redis-cli.exe path directly
.\scripts\clear-messages.ps1 -RedisCliPath "C:\Program Files\Redis\redis-cli.exe"

# PowerShell - specify redis-cli.exe path with spaces
.\scripts\clear-messages.ps1 -RedisCliPath 'C:\Program Files (x86)\Redis\redis-cli.exe'
```

**Note**: Requires redis-cli.exe to be available. The script will automatically search for it in:
- `.\redis\redis-cli.exe` (project directory)
- System PATH
- Common installation directories

If redis-cli.exe is not found, the script will provide installation instructions.

#### `start-full.bat` / `start-full.ps1`
Starts all services: Redis, Ollama, and Spring Boot application.

```batch
# Batch
scripts\start-full.bat

# PowerShell
.\scripts\start-full.ps1
```

#### `restart-full.ps1`
Stops all running services and restarts them fresh.

```powershell
.\scripts\restart-full.ps1
```

### Test Scripts

#### `run-tests.ps1`
Runs the complete test suite and provides a summary.

```powershell
# Run all tests
.\scripts\run-tests.ps1

# Skip chat test (useful if Ollama is slow)
.\scripts\run-tests.ps1 -SkipChatTest

# Verbose output
.\scripts\run-tests.ps1 -Verbose

# No pauses between tests
.\scripts\run-tests.ps1 -NoPause
```

#### Individual Test Scripts

#### PowerShell Versions (.ps1)

##### `tests/test-health.ps1`
Tests the health endpoint to verify the application is running.

```powershell
.\scripts\tests\test-health.ps1
```

##### `tests/test-chat.ps1`
Tests the chat endpoint with AI integration.

```powershell
# Default test message
.\scripts\tests\test-chat.ps1

# Custom message and session
.\scripts\tests\test-chat.ps1 -Message "Custom test message" -SessionId "my-test-session"

# Quiet mode (for automation)
.\scripts\tests\test-chat.ps1 -Quiet
```

##### `tests/test-get-messages.ps1`
Tests retrieving all stored messages from Redis.

```powershell
.\scripts\tests\test-get-messages.ps1
```

##### `tests/test-get-session-messages.ps1`
Tests session-based message filtering.

```powershell
# Default session
.\scripts\tests\test-get-session-messages.ps1

# Specific session
.\scripts\tests\test-get-session-messages.ps1 -SessionId "my-session"
```

#### Batch Versions (.bat) - Keep Terminal Open

All test scripts also have batch wrappers that keep the terminal window open:

##### `tests/test-health.bat`
```batch
.\scripts\tests\test-health.bat
```

##### `tests/test-chat.bat`
```batch
# Default test
.\scripts\tests\test-chat.bat

# With parameters
.\scripts\tests\test-chat.bat -Message "Custom test message" -SessionId "my-session"
```

##### `tests/test-get-messages.bat`
```batch
.\scripts\tests\test-get-messages.bat
```

##### `tests/test-get-session-messages.bat`
```batch
.\scripts\tests\test-get-session-messages.bat
```

##### `run-tests.bat` - Run All Tests
```batch
# Run complete test suite
.\scripts\run-tests.bat

# With options
.\scripts\run-tests.bat -SkipChatTest -NoPause
```

**Note**: Batch versions (.bat) keep the terminal window open after completion, while PowerShell versions (.ps1) close automatically unless you use `-NoPause`.

## 🔧 Script Parameters

### Common Parameters

- `-NoPause`: Don't wait for user input at the end
- `-Quiet`: Reduce output (for automation scripts)
- `-Verbose`: Show detailed output

### Test-Specific Parameters

- `-SkipChatTest`: Skip the chat test (useful if Ollama is slow)
- `-SessionId`: Specify session ID for testing
- `-Message`: Custom message for chat tests

## 📊 Test Results

The test runner provides:

- ✅ **Individual test status** (Passed/Failed/Skipped)
- ⏱️ **Execution time** for each test
- 📈 **Success rate** percentage
- 🎯 **Detailed summary** of all tests

Example output:
```
====================================
Test Results Summary
====================================

Health Endpoint: Passed (0.25s)
Chat Endpoint: Passed (3.45s)
Get All Messages: Passed (0.12s)
Get Session Messages: Passed (0.08s)

Total Tests: 4
Passed: 4
Failed: 0
Success Rate: 100.0%

🎉 All tests passed!
```

## 🛠️ Troubleshooting

### Application Won't Start
- Ensure Java 17+ is installed
- Check if ports 6379 (Redis) and 11434 (Ollama) are available
- Use `restart-full.ps1` to clean restart all services

### Tests Are Failing
- Verify all services are running: `.\scripts\start-full.ps1`
- Check Redis connection: `.\redis\redis-cli.exe ping`
- Verify Ollama is accessible: `ollama list`

### Port Conflicts
- Stop conflicting services or use different ports
- Check what's using ports: `Get-NetTCPConnection -LocalPort 8080`

### Permission Issues
- Run PowerShell as Administrator
- Check execution policy: `Get-ExecutionPolicy`

### Batch Scripts Close Windows Immediately
- **Problem**: `start-full.bat` closes service windows
- **Solution 1 (Recommended)**: Use PowerShell scripts instead:
  ```powershell
  .\scripts\start-full.ps1  # Much more reliable!
  ```
- **Solution 2**: Use the simple batch script:
  ```batch
  .\scripts\start-full-simple.bat
  ```
  Then **DO NOT close** the opened windows!

- **Solution 3**: Start services manually in separate terminals:
  ```batch
  # Terminal 1: Redis
  cd redis
  redis-server.exe --port 6379

  # Terminal 2: Ollama
  ollama serve

  # Terminal 3: Application
  mvn spring-boot:run
  ```

### redis-cli.exe Not Found
- **Problem**: Clear messages script can't find redis-cli.exe
- **Solution 1**: Install Redis in project directory:
  ```batch
  # Download Redis
  curl -o redis.zip "https://github.com/microsoftarchive/redis/releases/download/win-3.2.100/Redis-x64-3.2.100.zip"

  # Extract to project
  New-Item -ItemType Directory -Path "redis" -Force
  Expand-Archive -Path "redis.zip" -DestinationPath "redis"
  ```
- **Solution 2**: Add Redis to system PATH
- **Solution 3**: Use system-installed Redis and ensure redis-cli.exe is in PATH

### Double-Clicking Batch Files
- **Problem**: Scripts work from command line but fail when double-clicked
- **Cause**: Working directory differs when double-clicking vs. command line
- **Solution**: Always run batch scripts from command line:
  ```batch
  cd C:\path\to\AgentTimeline
  .\scripts\clear-messages.bat
  ```
  Or use PowerShell scripts instead:
  ```powershell
  .\scripts\clear-messages.ps1
  ```

## 🎯 Development Workflow

### Daily Development
```powershell
# 1. Start all services
.\scripts\start-full.ps1

# 2. Make code changes...

# 3. Build and restart
.\scripts\build.ps1
.\scripts\restart-full.ps1

# 4. Run tests
.\scripts\run-tests.ps1
```

### Testing Only
```powershell
# Quick test without rebuilding
.\scripts\run-tests.ps1

# Test specific endpoint
.\scripts\tests\test-chat.ps1 -Message "Hello from test!"
```

### Production Deployment
```powershell
# Build for production
.\scripts\build.ps1

# Start services
.\scripts\start-full.ps1
```

## 📝 Notes

- **PowerShell scripts** (.ps1) are more feature-rich and recommended
- **Batch scripts** (.bat) are simpler and work in basic Windows environments
- All scripts are designed to work from the project root directory
- Test scripts can be run individually for focused testing
- Use `-NoPause` for automation and CI/CD integration

## 🔍 Service Ports

- **Spring Boot Application**: http://localhost:8080/api/v1
- **Redis**: localhost:6379
- **Ollama**: localhost:11434

## 📞 Support

If you encounter issues:

1. Check the troubleshooting section above
2. Verify all prerequisites are installed (Java, Maven, Ollama, Redis)
3. Run `.\scripts\restart-full.ps1` to clean restart
4. Check application logs for detailed error messages

---

**Happy coding! 🚀**
