param(
    [switch]$Quiet,
    [switch]$NoPause
)

$testName = "Get All Messages Test"
$endpoint = "http://localhost:8080/api/v1/timeline/messages"

if (-not $Quiet) {
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host "AgentTimeline - $testName" -ForegroundColor Cyan
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Testing: $endpoint" -ForegroundColor Yellow
    Write-Host ""
}

try {
    $response = Invoke-WebRequest -Uri $endpoint -Method GET -TimeoutSec 10

    if ($response.StatusCode -eq 200) {
        $messages = $response.Content | ConvertFrom-Json

        if (-not $Quiet) {
            Write-Host "[PASSED] Test completed successfully" -ForegroundColor Green
            Write-Host "Found $($messages.Count) message(s)" -ForegroundColor White
            Write-Host ""
        } else {
            Write-Host "[OK] Get messages test passed - Found $($messages.Count) message(s)" -ForegroundColor Green
        }

        # Show details of messages if not quiet
        if (-not $Quiet -and $messages.Count -gt 0) {
            Write-Host "Recent messages:" -ForegroundColor Cyan
            $messages | Select-Object -First 3 | ForEach-Object {
                Write-Host "  ID: $($_.id)" -ForegroundColor White
                Write-Host "  Session: $($_.sessionId)" -ForegroundColor White
                Write-Host "  User: $($_.userMessage)" -ForegroundColor White
                Write-Host "  AI: $($_.assistantResponse)" -ForegroundColor White
                Write-Host "  Model: $($_.modelUsed)" -ForegroundColor White
                Write-Host "  Time: $($_.timestamp)" -ForegroundColor White
                Write-Host ""
            }

            if ($messages.Count -gt 3) {
                Write-Host "... and $($messages.Count - 3) more message(s)" -ForegroundColor Gray
                Write-Host ""
            }
        }

        exit 0
    } else {
        if (-not $Quiet) {
            Write-Host "[FAILED] Test failed" -ForegroundColor Red
            Write-Host "Unexpected status code: $($response.StatusCode)" -ForegroundColor Red
        } else {
            Write-Host "[ERROR] Get messages test failed - Status: $($response.StatusCode)" -ForegroundColor Red
        }
        exit 1
    }
} catch {
    if (-not $Quiet) {
        Write-Host "[FAILED] Test failed" -ForegroundColor Red
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host ""
        Write-Host "Make sure the application is running on http://localhost:8080" -ForegroundColor Yellow
    } else {
        Write-Host "[ERROR] Get messages test failed - Connection error" -ForegroundColor Red
    }
    exit 1
}

if (-not $Quiet -and -not $NoPause) {
    Write-Host ""
    Read-Host "Press Enter to continue"
}
