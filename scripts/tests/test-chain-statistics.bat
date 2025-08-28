@echo off
REM AgentTimeline - Message Chain Statistics Test (Phase 2)
REM This batch file runs the PowerShell script for testing chain statistics

echo ====================================
echo AgentTimeline - Message Chain Statistics Test (Phase 2)
echo ====================================
echo.

powershell.exe -ExecutionPolicy Bypass -File "%~dp0test-chain-statistics.ps1" %*

echo.
echo Test completed.
pause
