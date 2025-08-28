@echo off
echo ====================================
echo AgentTimeline - Maven Build Script
echo ====================================
echo.

echo Building AgentTimeline application...
echo.

cd ..
mvn clean compile

if %errorlevel% equ 0 (
    echo.
    echo ✅ Build completed successfully!
    echo.
) else (
    echo.
    echo ❌ Build failed! Check the output above for errors.
    echo.
    exit /b 1
)

cd scripts
echo Build script completed.
pause
