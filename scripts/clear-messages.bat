@echo off
echo ====================================
echo AgentTimeline - Clear Messages
echo ====================================
echo Clearing stored messages from Redis...
echo.

powershell -ExecutionPolicy Bypass -File "%~dp0clear-messages.ps1" %*

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Clear messages failed with exit code %errorlevel%
) else (
    echo.
    echo [SUCCESS] Clear messages completed
)

echo.
echo Press any key to close this window...
pause >nul
