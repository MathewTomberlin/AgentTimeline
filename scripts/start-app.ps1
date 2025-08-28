param(
    [switch]$NoPause
)

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "AgentTimeline - Start Application" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Checking if application is already running..." -ForegroundColor Yellow

# Check if port 8080 is in use
$portInUse = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Where-Object { $_.State -eq "Listen" }

if ($portInUse) {
    Write-Host "Application appears to be running on port 8080" -ForegroundColor Red
    Write-Host "If you want to restart, please stop the existing process first." -ForegroundColor Red
    Write-Host ""
    if (-not $NoPause) {
        Read-Host "Press Enter to continue"
    }
    exit 1
}

Write-Host "Starting AgentTimeline application..." -ForegroundColor Green
Write-Host "Application will be available at: http://localhost:8080/api/v1" -ForegroundColor Green
Write-Host "Press Ctrl+C in the new window to stop the application" -ForegroundColor Yellow
Write-Host ""

Set-Location ..

# Start Maven in a new window
Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "mvn spring-boot:run" -WindowStyle Normal

Start-Sleep -Seconds 5

Write-Host "Application starting... Please wait a few seconds." -ForegroundColor Yellow
Write-Host "You can test the application at:" -ForegroundColor Cyan
Write-Host "  Health Check: http://localhost:8080/api/v1/timeline/health" -ForegroundColor White
Write-Host "  Chat Endpoint: http://localhost:8080/api/v1/timeline/chat" -ForegroundColor White
Write-Host ""

Set-Location scripts
Write-Host "Start script completed. Application is running in background." -ForegroundColor Green

if (-not $NoPause) {
    Read-Host "Press Enter to continue"
}
