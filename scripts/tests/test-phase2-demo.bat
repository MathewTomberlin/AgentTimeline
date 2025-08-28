@echo off
REM AgentTimeline - Phase 2 Complete Demo - Conversation Reconstruction Showcase
REM This batch file runs the comprehensive Phase 2 demonstration

echo ====================================
echo AgentTimeline - Phase 2 Complete Demo
echo ====================================
echo.

powershell.exe -ExecutionPolicy Bypass -File "%~dp0test-phase2-demo.ps1" %*

echo.
echo Demo completed.
pause
