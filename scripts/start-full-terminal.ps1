param(
    [switch]$NoLogo,
    [switch]$NoWait,
    [switch]$SkipChecks,
    [switch]$Force
)

# Terminal-optimized full start script for PowerShell execution
# Starts Redis, Ollama, and Spring Boot application

$ErrorActionPreference = "Stop"

if (-not $NoLogo) {
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host "AgentTimeline - Terminal Full Start" -ForegroundColor Cyan
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host ""
}

$projectDir = Split-Path -Parent $PSScriptRoot
$originalLocation = Get-Location

$services = @()

try {
    Set-Location $projectDir

    if (-not $SkipChecks) {
        Write-Host "Performing pre-flight checks..." -ForegroundColor Yellow
        Write-Host ""

        # Check for existing services
        $redisRunning = Get-NetTCPConnection -LocalPort 6379 -ErrorAction SilentlyContinue | Where-Object { $_.State -eq "Listen" }
        $ollamaRunning = Get-NetTCPConnection -LocalPort 11434 -ErrorAction SilentlyContinue | Where-Object { $_.State -eq "Listen" }
        $appRunning = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Where-Object { $_.State -eq "Listen" }

        if ($redisRunning -or $ollamaRunning -or $appRunning) {
            Write-Host "Found running services:" -ForegroundColor Yellow
            if ($redisRunning) { Write-Host "  • Redis (port 6379)" -ForegroundColor White }
            if ($ollamaRunning) { Write-Host "  • Ollama (port 11434)" -ForegroundColor White }
            if ($appRunning) { Write-Host "  • Application (port 8080)" -ForegroundColor White }

            if (-not $Force) {
                $continue = Read-Host "`nContinue anyway? (y/N)"
                if ($continue -notmatch "^[Yy]") {
                    Write-Host "[CANCELLED] Operation cancelled by user" -ForegroundColor Yellow
                    exit 0
                }
            }
        }
    }

    Write-Host ""
    Write-Host "Starting all services..." -ForegroundColor Green
    Write-Host ""

    # Start Redis
    Write-Host "[1/3] Starting Redis server..." -ForegroundColor Cyan
    try {
        $redisProcess = Start-Process -FilePath ".\redis\redis-server.exe" -ArgumentList "--port", "6379" -NoNewWindow -PassThru
        $services += @{
            Name = "Redis"
            Process = $redisProcess
            Port = 6379
        }
        Write-Host "[SUCCESS] Redis started (PID: $($redisProcess.Id))" -ForegroundColor Green
    } catch {
        Write-Host "[ERROR] Failed to start Redis: $($_.Exception.Message)" -ForegroundColor Red
        exit 1
    }

    # Start Ollama
    Write-Host "[2/3] Starting Ollama server..." -ForegroundColor Cyan
    try {
        $ollamaProcess = Start-Process -FilePath "ollama" -ArgumentList "serve" -NoNewWindow -PassThru
        $services += @{
            Name = "Ollama"
            Process = $ollamaProcess
            Port = 11434
        }
        Write-Host "[SUCCESS] Ollama started (PID: $($ollamaProcess.Id))" -ForegroundColor Green
    } catch {
        Write-Host "[ERROR] Failed to start Ollama: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "Make sure Ollama is installed and in your PATH" -ForegroundColor Yellow
        exit 1
    }

    # Start Application
    Write-Host "[3/3] Starting AgentTimeline application..." -ForegroundColor Cyan
    try {
        $appProcess = Start-Process -FilePath "mvn" -ArgumentList "spring-boot:run" -NoNewWindow -PassThru
        $services += @{
            Name = "Application"
            Process = $appProcess
            Port = 8080
        }
        Write-Host "[SUCCESS] Application started (PID: $($appProcess.Id))" -ForegroundColor Green
    } catch {
        Write-Host "[ERROR] Failed to start application: $($_.Exception.Message)" -ForegroundColor Red
        exit 1
    }

    # Wait for services to start up
    Write-Host ""
    Write-Host "Waiting for services to initialize..." -ForegroundColor Yellow
    Start-Sleep -Seconds 15

    # Check service status
    Write-Host ""
    Write-Host "Checking service status..." -ForegroundColor Cyan

    foreach ($service in $services) {
        try {
            $connection = Get-NetTCPConnection -LocalPort $service.Port -ErrorAction SilentlyContinue | Where-Object { $_.State -eq "Listen" }
            if ($connection) {
                Write-Host "[OK] $($service.Name) listening on port $($service.Port)" -ForegroundColor Green
            } else {
                Write-Host "[WARNING] $($service.Name) not yet listening on port $($service.Port)" -ForegroundColor Yellow
            }
        } catch {
            Write-Host "[ERROR] Could not check $($service.Name) status" -ForegroundColor Red
        }
    }

    # Test application health
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/timeline/health" -Method GET -TimeoutSec 10
        if ($response.StatusCode -eq 200) {
            Write-Host "[OK] Application health check passed" -ForegroundColor Green
        }
    } catch {
        Write-Host "[WARNING] Application health check failed - may still be starting" -ForegroundColor Yellow
    }

    Write-Host ""
    Write-Host "====================================" -ForegroundColor Green
    Write-Host "[SUCCESS] All services started!" -ForegroundColor Green
    Write-Host "====================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Services running:" -ForegroundColor Cyan
    Write-Host "  • Redis:        localhost:6379" -ForegroundColor White
    Write-Host "  • Ollama:       localhost:11434" -ForegroundColor White
    Write-Host "  • Application:  localhost:8080/api/v1" -ForegroundColor White
    Write-Host ""
    Write-Host "Test endpoints:" -ForegroundColor Cyan
    Write-Host "  • Health:       http://localhost:8080/api/v1/timeline/health" -ForegroundColor White
    Write-Host "  • Chat:         http://localhost:8080/api/v1/timeline/chat" -ForegroundColor White
    Write-Host "  • All Messages: http://localhost:8080/api/v1/timeline/messages" -ForegroundColor White
    Write-Host ""
    Write-Host "Management:" -ForegroundColor Cyan
    Write-Host "  • Run tests:    .\scripts\run-tests-terminal.ps1" -ForegroundColor White
    Write-Host "  • Clear data:   .\scripts\clear-messages-terminal.ps1" -ForegroundColor White
    Write-Host "  • Stop all:     .\scripts\restart-full-terminal.ps1" -ForegroundColor White

    if (-not $NoWait) {
        Write-Host ""
        Read-Host "Press Enter to continue (services will continue running)"
    }

} catch {
    Write-Host ""
    Write-Host "[ERROR] Failed to start services: $($_.Exception.Message)" -ForegroundColor Red

    # Cleanup on failure
    Write-Host "Cleaning up..." -ForegroundColor Yellow
    foreach ($service in $services) {
        try {
            Stop-Process -Id $service.Process.Id -Force -ErrorAction SilentlyContinue
            Write-Host "Stopped $($service.Name)" -ForegroundColor Gray
        } catch {
            # Ignore cleanup errors
        }
    }
    exit 1
} finally {
    Set-Location $originalLocation
}
