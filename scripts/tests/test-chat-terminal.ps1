param(
    [switch]$Quiet,
    [switch]$NoWait,
    [string]$SessionId = "test-session",
    [string]$Message = "Hello from terminal test!"
)

# Terminal-optimized chat endpoint test
# Designed for interactive terminal use with real-time feedback

$testName = "Chat Endpoint Test"
$endpoint = "http://localhost:8080/api/v1/timeline/chat"

Write-Host "Testing: $endpoint" -ForegroundColor Yellow
Write-Host "Session: $SessionId" -ForegroundColor Yellow
Write-Host "Message: $Message" -ForegroundColor Yellow
Write-Host ""

$body = @{
    message = $Message
} | ConvertTo-Json

try {
    $startTime = Get-Date
    $response = Invoke-WebRequest -Uri "$endpoint$(if ($SessionId) { "?sessionId=$SessionId" })" -Method POST -ContentType "application/json" -Body $body -TimeoutSec 30
    $endTime = Get-Date
    $duration = [math]::Round(($endTime - $startTime).TotalSeconds, 2)

    if ($response.StatusCode -eq 200) {
        $content = $response.Content | ConvertFrom-Json

        Write-Host "[PASSED] Test completed successfully ($($duration)s)" -ForegroundColor Green
        Write-Host "ID: $($content.id)" -ForegroundColor White
        Write-Host "Session: $($content.sessionId)" -ForegroundColor White
        Write-Host "User Message: $($content.userMessage)" -ForegroundColor White
        Write-Host "AI Response: $($content.assistantResponse)" -ForegroundColor White
        Write-Host "Model Used: $($content.modelUsed)" -ForegroundColor White
        Write-Host "Response Time: $($content.responseTime)ms" -ForegroundColor White

        # Validate response structure
        if (-not $content.id -or -not $content.sessionId -or -not $content.userMessage -or -not $content.assistantResponse) {
            Write-Host ""
            Write-Host "[WARNING] Response missing some expected fields" -ForegroundColor Yellow
        }

        if (-not $Quiet -and -not $NoWait) {
            Read-Host "`nPress Enter to continue"
        }

        exit 0
    } else {
        Write-Host "[FAILED] Unexpected status code: $($response.StatusCode) ($($duration)s)" -ForegroundColor Red

        if (-not $Quiet -and -not $NoWait) {
            Read-Host "`nPress Enter to continue"
        }

        exit 1
    }
} catch {
    Write-Host "[FAILED] Test failed ($($_.Exception.Message))" -ForegroundColor Red
    Write-Host ""
    Write-Host "Possible issues:" -ForegroundColor Yellow
    Write-Host "  - Application not running" -ForegroundColor White
    Write-Host "  - Ollama not running or accessible" -ForegroundColor White
    Write-Host "  - Redis not running" -ForegroundColor White
    Write-Host "  - Invalid request format" -ForegroundColor White

    if (-not $Quiet -and -not $NoWait) {
        Read-Host "`nPress Enter to continue"
    }

    exit 1
}
