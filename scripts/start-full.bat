@echo off
echo ====================================
echo AgentTimeline - Full Start Script
echo ====================================
echo Starting PostgreSQL, Redis, Ollama, and Spring Boot Application...
echo.
echo NOTE: This batch version has limitations with keeping services running.
echo Consider using the PowerShell version instead: .\scripts\start-full.ps1
echo.

echo [1/4] Starting PostgreSQL database...
echo Starting PostgreSQL with pgvector extension...
docker run -d --name agent-pg -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=agent_timeline -p 5432:5432 ankane/pgvector >nul 2>&1
timeout /t 5 /nobreak >nul

echo Enabling pgvector extension...
docker exec agent-pg psql -U postgres -d agent_timeline -c "CREATE EXTENSION IF NOT EXISTS vector;" >nul 2>&1
timeout /t 2 /nobreak >nul

echo ✅ PostgreSQL database started and configured (container: agent-pg)
echo.

echo [2/4] Starting Redis server...
start "Redis Server" cmd /k "cd /d %~dp0\..\redis && redis-server.exe --port 6379"
timeout /t 3 /nobreak >nul
echo ✅ Redis server started (window should stay open)
echo.

echo [3/4] Starting Ollama server...
start "Ollama Server" cmd /k "ollama serve"
timeout /t 5 /nobreak >nul
echo ✅ Ollama server started (window should stay open)
echo.

echo [4/4] Starting AgentTimeline application...
start "AgentTimeline" cmd /k "cd /d %~dp0\.. && mvn spring-boot:run"
timeout /t 10 /nobreak >nul
echo ✅ Spring Boot application started (window should stay open)
echo.

echo ====================================
echo All services started successfully!
echo ====================================
echo.
echo IMPORTANT: Do NOT close the opened windows - they contain the running services!
echo.
echo Services running:
echo   • PostgreSQL:   localhost:5432 (container: agent-pg)
echo   • Redis:        localhost:6379
echo   • Ollama:       localhost:11434
echo   • Application:  localhost:8080/api/v1
echo.
echo Test endpoints:
echo   • Health:           http://localhost:8080/api/v1/timeline/health
echo   • Chat:             http://localhost:8080/api/v1/timeline/chat
echo   • All Messages:     http://localhost:8080/api/v1/timeline/messages
echo   • Vector Stats:     http://localhost:8080/api/v1/timeline/vector/statistics
echo   • Debug Chunks:     http://localhost:8080/api/v1/timeline/debug/chunks/test-session
echo.
echo Vector search test:
echo   • Run: .\scripts\test-vector-endpoints.ps1
echo.
echo Press any key to close this window...
echo (Keep the service windows open!)
pause >nul
