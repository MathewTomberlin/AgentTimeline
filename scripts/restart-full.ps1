param(
    [switch]$NoPause
)

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "AgentTimeline - Full Restart Script" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host "Stopping all services and restarting..." -ForegroundColor Yellow
Write-Host ""

# Function to stop processes by port
function Stop-ProcessByPort {
    param([int]$Port, [string]$ServiceName)

    Write-Host "Stopping $ServiceName (port $Port)..." -ForegroundColor Yellow

    try {
        $connections = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue | Where-Object { $_.State -eq "Listen" }

        if ($connections) {
            foreach ($connection in $connections) {
                $processId = $connection.OwningProcess
                try {
                    Stop-Process -Id $processId -Force -ErrorAction Stop
                    Write-Host "✅ Stopped $ServiceName (PID: $processId)" -ForegroundColor Green
                } catch {
                    Write-Host "⚠️  Could not stop $ServiceName (PID: $processId)" -ForegroundColor Yellow
                }
            }
        } else {
            Write-Host "ℹ️  $ServiceName was not running" -ForegroundColor Gray
        }
    } catch {
        Write-Host "⚠️  Error stopping $ServiceName" -ForegroundColor Yellow
    }
}

# Function to stop Java processes (Spring Boot)
function Stop-JavaProcesses {
    Write-Host "Stopping Java processes (Spring Boot)..." -ForegroundColor Yellow

    try {
        $javaProcesses = Get-Process -Name "java" -ErrorAction SilentlyContinue

        if ($javaProcesses) {
            foreach ($process in $javaProcesses) {
                try {
                    Stop-Process -Id $process.Id -Force -ErrorAction Stop
                    Write-Host "✅ Stopped Java process (PID: $($process.Id))" -ForegroundColor Green
                } catch {
                    Write-Host "⚠️  Could not stop Java process (PID: $($process.Id))" -ForegroundColor Yellow
                }
            }
        } else {
            Write-Host "ℹ️  No Java processes found" -ForegroundColor Gray
        }
    } catch {
        Write-Host "⚠️  Error stopping Java processes" -ForegroundColor Yellow
    }
}

# Stop all services
Write-Host "Stopping existing services..." -ForegroundColor Yellow
Write-Host ""

Stop-ProcessByPort -Port 8080 -ServiceName "Spring Boot Application"
Stop-JavaProcesses
Stop-ProcessByPort -Port 11434 -ServiceName "Ollama Server"
Stop-ProcessByPort -Port 6379 -ServiceName "Redis Server"

Write-Host ""
Write-Host "Waiting for services to fully stop..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

# Now start all services fresh
Write-Host ""
Write-Host "Starting services fresh..." -ForegroundColor Green

# Start Redis
Write-Host "[1/3] Starting Redis server..." -ForegroundColor Yellow
try {
    $redisProcess = Start-Process -FilePath ".\redis\redis-server.exe" -ArgumentList "--port 6379" -WindowStyle Hidden -PassThru
    Start-Sleep -Seconds 3
    Write-Host "✅ Redis server started" -ForegroundColor Green
} catch {
    Write-Host "❌ Failed to start Redis server" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
}

# Start Ollama
Write-Host "[2/3] Starting Ollama server..." -ForegroundColor Yellow
try {
    $ollamaProcess = Start-Process -FilePath "ollama" -ArgumentList "serve" -WindowStyle Hidden -PassThru
    Start-Sleep -Seconds 5
    Write-Host "✅ Ollama server started" -ForegroundColor Green
} catch {
    Write-Host "❌ Failed to start Ollama server" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
}

# Start Application
Write-Host "[3/3] Starting AgentTimeline application..." -ForegroundColor Yellow
try {
    $appProcess = Start-Process -FilePath "mvn" -ArgumentList "spring-boot:run" -WindowStyle Normal -PassThru
    Start-Sleep -Seconds 10
    Write-Host "✅ Spring Boot application started" -ForegroundColor Green
} catch {
    Write-Host "❌ Failed to start Spring Boot application" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
}

Write-Host ""
Write-Host "====================================" -ForegroundColor Green
Write-Host "Restart completed successfully!" -ForegroundColor Green
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

if (-not $NoPause) {
    Read-Host "Press Enter to continue"
}
