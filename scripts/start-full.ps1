param(
    [switch]$NoPause
)

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "AgentTimeline - Full Start Script" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host "Starting PostgreSQL, Redis, Ollama, and Spring Boot Application..." -ForegroundColor Yellow
Write-Host ""

# Function to check if port is in use
function Test-Port {
    param([int]$Port)
    $connection = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
    return $null -ne $connection
}

# Global step counter
$script:currentStep = 1

# Function to start service and wait
function Start-Service-WithWait {
    param(
        [string]$ServiceName,
        [string]$Command,
        [string]$WorkingDirectory = $null,
        [int]$WaitSeconds = 3
    )

    $script:currentStep++
    Write-Host "[$($script:currentStep)/4] Starting $ServiceName..." -ForegroundColor Yellow

    try {
        if ($WorkingDirectory) {
            $startInfo = New-Object System.Diagnostics.ProcessStartInfo
            $startInfo.FileName = "cmd.exe"
            $startInfo.Arguments = "/c $Command"
            $startInfo.WorkingDirectory = $WorkingDirectory
            $startInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Minimized
            $process = [System.Diagnostics.Process]::Start($startInfo)
        } else {
            $process = Start-Process -FilePath "cmd.exe" -ArgumentList "/c", $Command -WindowStyle Hidden -PassThru
        }

        Start-Sleep -Seconds $WaitSeconds
        Write-Host "✅ $ServiceName started" -ForegroundColor Green
    } catch {
        Write-Host "❌ Failed to start $ServiceName" -ForegroundColor Red
        Write-Host $_.Exception.Message -ForegroundColor Red
    }
}

# Check if services are already running
Write-Host "Checking for existing services..." -ForegroundColor Yellow

$postgresRunning = Test-Port -Port 5432
$redisRunning = Test-Port -Port 6379
$ollamaRunning = Test-Port -Port 11434
$appRunning = Test-Port -Port 8080

if ($postgresRunning) {
    Write-Host "⚠️  PostgreSQL appears to be running on port 5432" -ForegroundColor Yellow
}
if ($redisRunning) {
    Write-Host "⚠️  Redis appears to be running on port 6379" -ForegroundColor Yellow
}
if ($ollamaRunning) {
    Write-Host "⚠️  Ollama appears to be running on port 11434" -ForegroundColor Yellow
}
if ($appRunning) {
    Write-Host "⚠️  Application appears to be running on port 8080" -ForegroundColor Yellow
}

if ($postgresRunning -or $redisRunning -or $ollamaRunning -or $appRunning) {
    Write-Host ""
    $continue = Read-Host "Some services may already be running. Continue anyway? (y/N)"
    if ($continue -notmatch "^[Yy]") {
        Write-Host "Operation cancelled." -ForegroundColor Yellow
        if (-not $NoPause) {
            Read-Host "Press Enter to continue"
        }
        exit 0
    }
}

Write-Host ""

# Start PostgreSQL
Write-Host "[1/4] Starting PostgreSQL database..." -ForegroundColor Yellow
try {
    docker run -d --name agent-pg -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=agent_timeline -p 5432:5432 ankane/pgvector | Out-Null
    Start-Sleep -Seconds 5

    # Enable pgvector extension
    docker exec agent-pg psql -U postgres -d agent_timeline -c "CREATE EXTENSION IF NOT EXISTS vector;" | Out-Null
    Start-Sleep -Seconds 2

    Write-Host "✅ PostgreSQL database started and configured (container: agent-pg)" -ForegroundColor Green
} catch {
    Write-Host "❌ Failed to start PostgreSQL" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
}

# Start Redis
Start-Service-WithWait -ServiceName "Redis server" -Command "redis-server.exe --port 6379" -WorkingDirectory "redis" -WaitSeconds 3

# Start Ollama
Start-Service-WithWait -ServiceName "Ollama server" -Command "ollama serve" -WaitSeconds 5

# Start Application
Start-Service-WithWait -ServiceName "AgentTimeline application" -Command "mvn spring-boot:run" -WaitSeconds 10

Write-Host ""
Write-Host "====================================" -ForegroundColor Green
Write-Host "All services started successfully!" -ForegroundColor Green
Write-Host "====================================" -ForegroundColor Green
Write-Host ""
Write-Host "Services running:" -ForegroundColor Cyan
Write-Host "  • PostgreSQL:   localhost:5432 (container: agent-pg)" -ForegroundColor White
Write-Host "  • Redis:        localhost:6379" -ForegroundColor White
Write-Host "  • Ollama:       localhost:11434" -ForegroundColor White
Write-Host "  • Application:  localhost:8080/api/v1" -ForegroundColor White
Write-Host ""
Write-Host "Test endpoints:" -ForegroundColor Cyan
Write-Host "  • Health:           http://localhost:8080/api/v1/timeline/health" -ForegroundColor White
Write-Host "  • Chat:             http://localhost:8080/api/v1/timeline/chat" -ForegroundColor White
Write-Host "  • All Messages:     http://localhost:8080/api/v1/timeline/messages" -ForegroundColor White
Write-Host "  • Vector Stats:     http://localhost:8080/api/v1/timeline/vector/statistics" -ForegroundColor White
Write-Host "  • Debug Chunks:     http://localhost:8080/api/v1/timeline/debug/chunks/test-session" -ForegroundColor White
Write-Host ""
Write-Host "Vector search test:" -ForegroundColor Cyan
Write-Host "  • Run: .\scripts\test-vector-endpoints.ps1" -ForegroundColor White
Write-Host ""

if (-not $NoPause) {
    Read-Host "Press Enter to continue (services will continue running)"
}
