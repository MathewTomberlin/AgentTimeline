@echo off
echo ====================================
echo AgentTimeline - Clear Messages
echo ====================================
echo.

:menu
echo Choose what to clear:
echo [1] Redis messages + Phase 6 memory (recommended - clears conversation history)
echo [2] Redis + PostgreSQL data (complete cleanup - deletes Redis keys and PostgreSQL embedding chunks)
echo [3] Redis + Phase 6 memory caches (clears application memory)
echo [4] Complete cleanup (Redis + PostgreSQL + Phase 6 memory)
echo [5] Show all Redis keys (debug mode)
echo [6] Show help and usage examples
echo.

set /p choice="Enter your choice (1-6): "

if "%choice%"=="1" goto redis_only
if "%choice%"=="2" goto redis_postgres
if "%choice%"=="3" goto redis_phase6
if "%choice%"=="4" goto complete_cleanup
if "%choice%"=="5" goto show_keys
if "%choice%"=="6" goto show_help
goto menu

:redis_only
echo.
echo Clearing Redis messages + Phase 6 memory caches...
powershell -ExecutionPolicy Bypass -File "%~dp0clear-messages.ps1" -ClearPhase6 %*
goto end

:redis_postgres
echo.
echo Clearing Redis and PostgreSQL data...
powershell -ExecutionPolicy Bypass -File "%~dp0clear-messages.ps1" -ClearPostgres %*
goto end

:redis_phase6
echo.
echo Clearing Redis and Phase 6 memory caches...
powershell -ExecutionPolicy Bypass -File "%~dp0clear-messages.ps1" -ClearPhase6 %*
goto end

:complete_cleanup
echo.
echo Complete cleanup: Redis + PostgreSQL + Phase 6 memory...
powershell -ExecutionPolicy Bypass -File "%~dp0clear-messages.ps1" -ClearAll %*
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
echo   clear-messages.bat -ClearPostgres           (Redis + PostgreSQL cleanup)
echo   clear-messages.bat -ClearPhase6             (Redis + Phase 6 memory cleanup)
echo   clear-messages.bat -ClearAll                (Complete cleanup)
echo   clear-messages.bat -Force                   (Skip confirmation)
echo   clear-messages.bat -Quiet                   (Minimal output)
echo   clear-messages.bat -ShowKeys                (Show all keys without deleting)
echo.
echo PowerShell usage:
echo   .\scripts\clear-messages.ps1 -ClearPostgres -Force    (Redis + PostgreSQL)
echo   .\scripts\clear-messages.ps1 -ClearPhase6 -Force      (Redis + Phase 6 memory)
echo   .\scripts\clear-messages.ps1 -ClearAll -Force         (Complete cleanup)
echo   .\scripts\clear-messages.ps1 -ShowKeys               (Debug key analysis)
echo   .\scripts\clear-messages.ps1 -Force                  (Redis only)
echo.
echo Cleanup modes:
echo   Redis Only:     Deletes message content + Phase 6 memory (recommended default)
echo   Redis + PG:     Deletes Redis keys + PostgreSQL embedding data
echo   Redis + Phase6: Deletes Redis keys + clears application memory caches
echo   Complete:       Deletes Redis + PostgreSQL + Phase 6 memory (full reset)
echo.
echo Phase 6 Memory Caches:
echo   - Conversation history windows (rolling conversation memory)
echo   - Key information extraction cache (LLM analysis results)
echo   - Context retrieval metrics (performance statistics)
echo.
echo Interactive menu options:
echo   [1] Redis + Phase 6 memory (recommended - clears conversation history)
echo   [2] Redis + PostgreSQL
echo   [3] Redis + Phase 6 memory
echo   [4] Complete cleanup (all data)
echo   [5] Show all keys (debug)
echo   [6] Help and usage
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
