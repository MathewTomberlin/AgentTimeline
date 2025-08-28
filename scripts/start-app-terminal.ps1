param(
    [switch]$NoLogo,
    [switch]$NoWait,
    [switch]$Background
)

# Terminal-optimized start script for PowerShell execution
# Designed for interactive terminal use with real-time feedback

$ErrorActionPreference = "Stop"

if (-not $NoLogo) {
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host "AgentTimeline - Terminal Start" -ForegroundColor Cyan
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host ""
}

$projectDir = Split-Path -Parent $PSScriptRoot
$originalLocation = Get-Location

try {
    Set-Location $projectDir

    # Check if application is already running
    $portInUse = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Where-Object { $_.State -eq "Listen" }

    if ($portInUse) {
        Write-Host "[WARNING] Application appears to be running on port 8080" -ForegroundColor Yellow
        $continue = Read-Host "Continue anyway? (y/N)"
        if ($continue -notmatch "^[Yy]") {
            Write-Host "[CANCELLED] Operation cancelled by user" -ForegroundColor Yellow
            exit 0
        }
    }

    # Check prerequisites
    Write-Host "Checking prerequisites..." -ForegroundColor Yellow

    # Check Redis
    $redisRunning = Get-NetTCPConnection -LocalPort 6379 -ErrorAction SilentlyContinue | Where-Object { $_.State -eq "Listen" }
    if (-not $redisRunning) {
        Write-Host "[WARNING] Redis server not detected on port 6379" -ForegroundColor Yellow
        Write-Host "Make sure Redis is running: .\redis\redis-server.exe --port 6379" -ForegroundColor White
    } else {
        Write-Host "[OK] Redis server is running" -ForegroundColor Green
    }

    # Check Ollama
    $ollamaRunning = Get-NetTCPConnection -LocalPort 11434 -ErrorAction SilentlyContinue | Where-Object { $_.State -eq "Listen" }
    if (-not $ollamaRunning) {
        Write-Host "[WARNING] Ollama server not detected on port 11434" -ForegroundColor Yellow
        Write-Host "Make sure Ollama is running: ollama serve" -ForegroundColor White
    } else {
        Write-Host "[OK] Ollama server is running" -ForegroundColor Green
    }

    Write-Host ""
    Write-Host "Starting AgentTimeline application..." -ForegroundColor Green
    Write-Host "Application will be available at: http://localhost:8080/api/v1" -ForegroundColor White
    Write-Host ""

    if ($Background) {
        # Start in background
        $appProcess = Start-Process -FilePath "mvn" -ArgumentList "spring-boot:run" -NoNewWindow -PassThru
        Write-Host "[SUCCESS] Application started in background (PID: $($appProcess.Id))" -ForegroundColor Green

        # Wait a bit for startup
        Start-Sleep -Seconds 10

        # Test the application
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/timeline/health" -Method GET -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                Write-Host "[SUCCESS] Application is responding correctly!" -ForegroundColor Green
            }
        } catch {
            Write-Host "[WARNING] Application may still be starting up..." -ForegroundColor Yellow
        }

        if (-not $NoWait) {
            Write-Host ""
            Read-Host "Press Enter to continue (application will continue running)"
        }
    } else {
        # Start in foreground (interactive mode)
        Write-Host "Starting application in foreground mode..." -ForegroundColor Cyan
        Write-Host "Press Ctrl+C to stop the application" -ForegroundColor Yellow
        Write-Host ""

        & mvn spring-boot:run
    }

} catch {
    Write-Host ""
    Write-Host "[ERROR] Failed to start application: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    Write-Host "Troubleshooting:" -ForegroundColor Yellow
    Write-Host "  • Check Java installation: java -version" -ForegroundColor White
    Write-Host "  • Check Maven installation: mvn -version" -ForegroundColor White
    Write-Host "  • Verify ports 6379 (Redis) and 11434 (Ollama) are available" -ForegroundColor White
    Write-Host "  • Try building first: .\scripts\build-terminal.ps1" -ForegroundColor White
    exit 1
} finally {
    Set-Location $originalLocation
}
