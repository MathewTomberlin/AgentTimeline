@echo off
echo ====================================
echo AgentTimeline - Full Start Script
echo ====================================
echo Starting Redis, Ollama, and Spring Boot Application...
echo.
echo NOTE: This batch version has limitations with keeping services running.
echo Consider using the PowerShell version instead: .\scripts\start-full.ps1
echo.

echo [1/3] Starting Redis server...
start "Redis Server" cmd /k "cd /d %~dp0\..\redis && redis-server.exe --port 6379"
timeout /t 3 /nobreak >nul
echo ✅ Redis server started (window should stay open)
echo.

echo [2/3] Starting Ollama server...
start "Ollama Server" cmd /k "ollama serve"
timeout /t 5 /nobreak >nul
echo ✅ Ollama server started (window should stay open)
echo.

echo [3/3] Starting AgentTimeline application...
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
echo   • Redis:        localhost:6379
echo   • Ollama:       localhost:11434
echo   • Application:  localhost:8080/api/v1
echo.
echo Test endpoints:
echo   • Health:       http://localhost:8080/api/v1/timeline/health
echo   • Chat:         http://localhost:8080/api/v1/timeline/chat
echo   • All Messages: http://localhost:8080/api/v1/timeline/messages
echo.
echo Press any key to close this window...
echo (Keep the service windows open!)
pause >nul
