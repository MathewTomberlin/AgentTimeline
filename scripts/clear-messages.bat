@echo off
echo ====================================
echo AgentTimeline - Clear Messages
echo ====================================
echo.

:menu
echo Choose what to clear:
echo [1] Redis messages only (preserves indexes for future use)
echo [2] Redis + PostgreSQL data (complete cleanup - deletes Redis keys and PostgreSQL embedding chunks)
echo [3] Show all Redis keys (debug mode)
echo [4] Show help and usage examples
echo.

set /p choice="Enter your choice (1-4): "

if "%choice%"=="1" goto redis_only
if "%choice%"=="2" goto redis_postgres
if "%choice%"=="3" goto show_keys
if "%choice%"=="4" goto show_help
goto menu

:redis_only
echo.
echo Clearing Redis messages only...
powershell -ExecutionPolicy Bypass -File "%~dp0clear-messages.ps1" %*
goto end

:redis_postgres
echo.
echo Clearing Redis and PostgreSQL data...
powershell -ExecutionPolicy Bypass -File "%~dp0clear-messages.ps1" -ClearPostgres %*
goto end

:show_keys
echo.
echo Showing all Redis keys (debug mode)...
powershell -ExecutionPolicy Bypass -File "%~dp0clear-messages.ps1" -ShowKeys
goto end

:show_help
echo.
echo Usage examples:
echo   clear-messages.bat                          (Interactive menu)
echo   clear-messages.bat -ClearPostgres           (Complete cleanup - Redis + PostgreSQL)
echo   clear-messages.bat -Force                   (Skip confirmation)
echo   clear-messages.bat -Quiet                   (Minimal output)
echo   clear-messages.bat -ShowKeys                (Show all keys without deleting)
echo.
echo PowerShell usage:
echo   .\scripts\clear-messages.ps1 -ClearPostgres -Force    (Complete cleanup)
echo   .\scripts\clear-messages.ps1 -ShowKeys               (Debug key analysis)
echo   .\scripts\clear-messages.ps1 -Force                  (Redis only, preserve indexes)
echo.
echo Cleanup modes:
echo   Redis Only: Deletes message content, preserves indexes for future use
echo   Complete:   Deletes ALL Redis keys + PostgreSQL data (full reset)
echo.
echo Interactive menu options:
echo   [1] Redis only (preserves indexes)
echo   [2] Complete cleanup (Redis + PostgreSQL)
echo   [3] Show all keys (debug)
echo   [4] Help and usage
echo.
pause
goto menu

:end
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
