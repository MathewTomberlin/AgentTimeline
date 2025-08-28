param(
    [switch]$Quiet,
    [switch]$NoPause,
    [string]$SessionId = "test-session",
    [string]$Message = "Hello from automated test!"
)

$testName = "Chat Endpoint Test"
$endpoint = "http://localhost:8080/api/v1/timeline/chat"

if (-not $Quiet) {
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host "AgentTimeline - $testName" -ForegroundColor Cyan
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Testing: $endpoint" -ForegroundColor Yellow
    Write-Host "Session: $SessionId" -ForegroundColor Yellow
    Write-Host "Message: $Message" -ForegroundColor Yellow
    Write-Host ""
}

$body = @{
    message = $Message
} | ConvertTo-Json

try {
    $response = Invoke-WebRequest -Uri "$endpoint$(if ($SessionId) { "?sessionId=$SessionId" })" -Method POST -ContentType "application/json" -Body $body -TimeoutSec 30

    if ($response.StatusCode -eq 200) {
        $content = $response.Content | ConvertFrom-Json

        if (-not $Quiet) {
            Write-Host "[PASSED] Test completed successfully" -ForegroundColor Green
            Write-Host "🤖 AI Response Details:" -ForegroundColor Green
            Write-Host "  ID: $($content.id)" -ForegroundColor White
            Write-Host "  Session: $($content.sessionId)" -ForegroundColor White
            Write-Host "  Role: $($content.role)" -ForegroundColor White
            Write-Host "  Content: $($content.content)" -ForegroundColor White
            Write-Host "  Parent Message: $($content.parentMessageId)" -ForegroundColor White
            Write-Host "  Timestamp: $($content.timestamp)" -ForegroundColor White
            if ($content.metadata -and $content.metadata.model) {
                Write-Host "  Model: $($content.metadata.model)" -ForegroundColor White
            }
            if ($content.metadata -and $content.metadata.responseTimeMs) {
                Write-Host "  Response Time: $($content.metadata.responseTimeMs)ms" -ForegroundColor White
            }
        } else {
            Write-Host "[OK] Chat test passed - Response received" -ForegroundColor Green
        }

        # Validate response structure for new Message format
        if (-not $content.id -or -not $content.sessionId -or -not $content.role -or -not $content.content) {
            if (-not $Quiet) {
                Write-Host "[WARNING] Response missing some expected fields" -ForegroundColor Yellow
            }
        }

        exit 0
    } else {
        if (-not $Quiet) {
            Write-Host "[FAILED] Test failed" -ForegroundColor Red
            Write-Host "Unexpected status code: $($response.StatusCode)" -ForegroundColor Red
        } else {
            Write-Host "[ERROR] Chat test failed - Status: $($response.StatusCode)" -ForegroundColor Red
        }
        exit 1
    }
} catch {
    if (-not $Quiet) {
        Write-Host "[FAILED] Test failed" -ForegroundColor Red
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host ""
        Write-Host "Possible issues:" -ForegroundColor Yellow
        Write-Host "  - Application not running" -ForegroundColor White
        Write-Host "  - Ollama not running or accessible" -ForegroundColor White
        Write-Host "  - Redis not running" -ForegroundColor White
        Write-Host "  - Invalid request format" -ForegroundColor White
    } else {
        Write-Host "[ERROR] Chat test failed - $($_.Exception.Message)" -ForegroundColor Red
    }
    exit 1
}

if (-not $Quiet -and -not $NoPause) {
    Write-Host ""
    Read-Host "Press Enter to continue"
}
