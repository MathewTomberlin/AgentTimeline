@echo off
echo ====================================
echo AgentTimeline - Start Application
echo ====================================
echo.

echo Checking if application is already running...
for /f "tokens=5" %%a in ('netstat -aon ^| find ":8080" ^| find "LISTENING"') do (
    echo Application appears to be running on port 8080 (PID: %%a)
    echo If you want to restart, please stop the existing process first.
    echo.
    pause
    exit /b 1
)

echo Starting AgentTimeline application...
echo Application will be available at: http://localhost:8080/api/v1
echo Press Ctrl+C to stop the application
echo.

cd ..
start "AgentTimeline" cmd /c "mvn spring-boot:run"

timeout /t 5 /nobreak >nul

echo Application starting... Please wait a few seconds.
echo You can test the application at:
echo   Health Check: http://localhost:8080/api/v1/timeline/health
echo   Chat Endpoint: http://localhost:8080/api/v1/timeline/chat
echo.

cd scripts
echo Start script completed. Application is running in background.
pause
