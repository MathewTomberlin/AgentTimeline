@echo off
powershell -ExecutionPolicy Bypass -File "%~dp0test-health.ps1" %*

echo.
echo Press any key to close this window...
pause >nul
