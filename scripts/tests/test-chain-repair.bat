@echo off
REM AgentTimeline - Message Chain Repair Test (Phase 2)
REM This batch file runs the PowerShell script for testing chain repair

echo ====================================
echo AgentTimeline - Message Chain Repair Test (Phase 2)
echo ====================================
echo.

powershell.exe -ExecutionPolicy Bypass -File "%~dp0test-chain-repair.ps1" %*

echo.
echo Test completed.
pause
