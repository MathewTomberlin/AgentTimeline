@echo off
REM AgentTimeline Chat Script Launcher
REM Launches the PowerShell chat interface
REM
REM Usage:
REM   chat.bat                           - Enhanced chat (with Phase 6 memory)
REM   chat.bat -SimpleChat              - Simple chat (no memory services)
REM   chat.bat -SessionId "my-session"  - Use specific session ID
REM   chat.bat -ShowPrompt              - Show LLM prompts (enhanced mode only)
REM   chat.bat -NoPause                 - Don't pause at end

REM Check if PowerShell execution policy allows script running
powershell -Command "Get-ExecutionPolicy" | findstr /i "Restricted" >nul
if %errorlevel%==0 (
    echo Warning: PowerShell execution policy is Restricted.
    echo You may need to run: Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
    echo.
)

REM Launch PowerShell script
powershell -ExecutionPolicy Bypass -File "%~dp0chat.ps1" %*

REM Keep window open if there was an error
if %errorlevel% neq 0 (
    echo.
    echo Press any key to exit...
    pause >nul
)
