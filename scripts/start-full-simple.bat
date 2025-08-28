@echo off
echo ====================================
echo AgentTimeline - Simple Full Start
echo ====================================
echo This script opens each service in its own window.
echo DO NOT CLOSE the opened windows while using the application!
echo.
echo Press any key to continue...
pause >nul
echo.

echo [1/3] Starting Redis server...
start "Redis Server" cmd /k "cd /d %~dp0\..\redis && echo Redis Server - Close this window to stop Redis && redis-server.exe --port 6379"
echo ✅ Redis server window opened
echo.

echo [2/3] Starting Ollama server...
start "Ollama Server" cmd /k "echo Ollama Server - Close this window to stop Ollama && ollama serve"
echo ✅ Ollama server window opened
echo.

echo [3/3] Starting AgentTimeline application...
start "AgentTimeline" cmd /k "cd /d %~dp0\.. && echo AgentTimeline Application - Close this window to stop the app && mvn spring-boot:run"
echo ✅ Application window opened
echo.

echo ====================================
echo All services started!
echo ====================================
echo.
echo Services are running in separate windows:
echo   • Redis:        localhost:6379  (Redis Server window)
echo   • Ollama:       localhost:11434 (Ollama Server window)
echo   • Application:  localhost:8080  (AgentTimeline window)
echo.
echo Test endpoints:
echo   • Health:       http://localhost:8080/api/v1/timeline/health
echo   • Chat:         http://localhost:8080/api/v1/timeline/chat
echo   • All Messages: http://localhost:8080/api/v1/timeline/messages
echo.
echo To stop services: Close their respective windows
echo.
echo Press any key to close this launcher window...
pause >nul
