@echo off
powershell -ExecutionPolicy Bypass -File "%~dp0test-get-messages.ps1" %*

echo.
echo Press any key to close this window...
pause >nul
