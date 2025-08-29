@echo off
REM Test Similarity Search Demonstration
REM This batch file runs the PowerShell script to demonstrate vector similarity search

echo === AgentTimeline Phase 4: Similarity Search Demonstration ===
echo.

REM Check if PowerShell is available
powershell -Command "Write-Host 'PowerShell is available'" >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: PowerShell is not available on this system.
    echo Please ensure PowerShell is installed and try again.
    pause
    exit /b 1
)

echo Starting similarity search demonstration...
echo.

REM Run the PowerShell script
powershell -ExecutionPolicy Bypass -File "%~dp0test-similarity-search-demo.ps1"

echo.
echo === Demonstration Complete ===
echo.
echo The demonstration showed how AgentTimeline uses vector similarity search
echo to provide context-aware responses based on your conversation history.
echo.
pause
