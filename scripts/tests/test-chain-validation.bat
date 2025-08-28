@echo off
REM AgentTimeline - Message Chain Validation Test (Phase 2)
REM This batch file runs the PowerShell script for testing chain validation

echo ====================================
echo AgentTimeline - Message Chain Validation Test (Phase 2)
echo ====================================
echo.

powershell.exe -ExecutionPolicy Bypass -File "%~dp0test-chain-validation.ps1" %*

echo.
echo Test completed.
pause
