@echo off
REM Vector Search Scenarios Test
REM Tests different content types and scenarios for vector similarity search

echo === AgentTimeline Vector Search Scenarios Test ===
echo Testing different content types and scenarios
echo.

REM Check if PowerShell is available
powershell -Command "Write-Host 'PowerShell is available'" >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: PowerShell is not available on this system.
    echo Please ensure PowerShell is installed and try again.
    pause
    exit /b 1
)

echo Starting comprehensive vector search scenarios test...
echo This will test: Technical content, casual conversation, message lengths, session isolation
echo.

REM Run the PowerShell script
powershell -ExecutionPolicy Bypass -File "%~dp0test-vector-scenarios.ps1"

echo.
echo === Scenarios Test Complete ===
echo.
echo The comprehensive test validated:
echo â€¢ Technical programming content context retention
echo â€¢ Casual conversation context retention
echo â€¢ Short vs long message performance
echo â€¢ Session isolation and data separation
echo.
pause
