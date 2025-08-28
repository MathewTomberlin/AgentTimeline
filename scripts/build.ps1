param(
    [switch]$NoPause
)

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "AgentTimeline - Maven Build Script" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Building AgentTimeline application..." -ForegroundColor Yellow
Write-Host ""

Set-Location ..
try {
    & mvn clean compile
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "✅ Build completed successfully!" -ForegroundColor Green
        Write-Host ""
    } else {
        Write-Host ""
        Write-Host "❌ Build failed! Check the output above for errors." -ForegroundColor Red
        Write-Host ""
        exit 1
    }
} catch {
    Write-Host ""
    Write-Host "❌ Build failed with exception: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    exit 1
}

Set-Location scripts
Write-Host "Build script completed."

if (-not $NoPause) {
    Read-Host "Press Enter to continue"
}
