@echo off
REM Vector Search Performance Test
REM Measures the performance of vector similarity search operations

echo === AgentTimeline Vector Search Performance Test ===
echo Measuring system performance with timing analysis
echo.

REM Check if PowerShell is available
powershell -Command "Write-Host 'PowerShell is available'" >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: PowerShell is not available on this system.
    echo Please ensure PowerShell is installed and try again.
    pause
    exit /b 1
)

echo Starting performance test...
echo This will test: Message processing, similarity search, global search, limit variations
echo.

REM Run the PowerShell performance test
powershell -ExecutionPolicy Bypass -File "%~dp0test-performance.ps1"

echo.
echo === Performance Test Complete ===
echo.
echo The performance test has measured:
echo â€¢ Message processing times
echo â€¢ Similarity search response times
echo â€¢ Global search across all sessions
echo â€¢ Performance with different result limits
echo â€¢ Overall system throughput
echo.
pause
