@echo off
REM AgentTimeline - Conversation Reconstruction Test (Phase 2)
REM This batch file runs the PowerShell script for testing conversation reconstruction

echo ====================================
echo AgentTimeline - Conversation Reconstruction Test (Phase 2)
echo ====================================
echo.

powershell.exe -ExecutionPolicy Bypass -File "%~dp0test-conversation-reconstruction.ps1" %*

echo.
echo Test completed.
pause
