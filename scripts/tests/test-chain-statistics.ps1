param(
    [switch]$Quiet,
    [switch]$NoPause,
    [int]$SessionsToCreate = 2
)

$testName = "Message Chain Statistics Test (Phase 2)"
$endpoint = "http://localhost:8080/api/v1"

if (-not $Quiet) {
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host "AgentTimeline - $testName" -ForegroundColor Cyan
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Creating $SessionsToCreate test sessions..." -ForegroundColor Yellow
    Write-Host ""
}

try {
    # Create a simple conversation for testing
    $messages = @("Hello!", "How are you?")
    $totalMessagesCreated = 0

    foreach ($message in $messages) {
        $body = @{
            message = $message
        } | ConvertTo-Json

        $response = Invoke-WebRequest -Uri "$endpoint/timeline/chat" -Method POST -ContentType "application/json" -Body $body -TimeoutSec 30

        if ($response.StatusCode -eq 200) {
            $totalMessagesCreated += 1
            if (-not $Quiet) {
                Write-Host "[OK] Created message $totalMessagesCreated" -ForegroundColor Green
            }
        } else {
            throw "Failed to create message. Status: $($response.StatusCode)"
        }

        Start-Sleep -Milliseconds 500
    }

    # Get chain statistics
    if (-not $Quiet) {
        Write-Host ""
        Write-Host "Retrieving chain statistics..." -ForegroundColor Cyan
    }

    $statsResponse = Invoke-WebRequest -Uri "$endpoint/timeline/chain/statistics" -Method GET -TimeoutSec 10
    $statistics = $statsResponse.Content | ConvertFrom-Json

    if (-not $Quiet) {
        Write-Host ""
        Write-Host "CHAIN STATISTICS RESULTS:" -ForegroundColor Green
        Write-Host "========================" -ForegroundColor Green
        Write-Host ""
        Write-Host "Total Sessions: $($statistics.totalSessions)" -ForegroundColor White
        Write-Host "Valid Chains: $($statistics.validChains)" -ForegroundColor Green
        Write-Host "Invalid Chains: $($statistics.invalidChains)" -ForegroundColor $(if ($statistics.invalidChains -gt 0) { "Red" } else { "Green" })
        Write-Host "Total Messages: $($statistics.totalMessages)" -ForegroundColor White
        Write-Host ""
        Write-Host "[OK] Statistics retrieved successfully!" -ForegroundColor Green
    }

    if ($Quiet) {
        Write-Host "[OK] Chain statistics test completed - $totalMessagesCreated messages created" -ForegroundColor Green
    } else {
        Write-Host ""
        Write-Host "[PASSED] Chain statistics test completed successfully!" -ForegroundColor Green
        Write-Host "[OK] Created test conversation" -ForegroundColor White
        Write-Host "[OK] Retrieved statistics" -ForegroundColor White
    }

    exit 0

} catch {
    if (-not $Quiet) {
        Write-Host "[FAILED] Test failed" -ForegroundColor Red
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host ""
        Write-Host "Make sure the application is running on http://localhost:8080" -ForegroundColor Yellow
        Write-Host "Also ensure Ollama is running and accessible" -ForegroundColor Yellow
    } else {
        Write-Host "[ERROR] Chain statistics test failed - $($_.Exception.Message)" -ForegroundColor Red
    }
    exit 1
}

if (-not $Quiet -and -not $NoPause) {
    Write-Host ""
    Read-Host "Press Enter to continue"
}
