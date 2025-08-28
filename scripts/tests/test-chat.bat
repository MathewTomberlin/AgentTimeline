@echo off
powershell -ExecutionPolicy Bypass -File "%~dp0test-chat.ps1" %*

echo.
echo Press any key to close this window...
pause >nul
