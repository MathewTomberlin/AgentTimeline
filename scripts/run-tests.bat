@echo off
echo ====================================
echo AgentTimeline - Test Runner
echo ====================================
echo Running comprehensive API tests...
echo.

powershell -ExecutionPolicy Bypass -File "%~dp0run-tests.ps1" %*

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Test suite failed with exit code %errorlevel%
) else (
    echo.
    echo [SUCCESS] All tests completed
)

echo.
echo Press any key to close this window...
pause >nul
