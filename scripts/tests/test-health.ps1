param(
    [switch]$Quiet,
    [switch]$NoPause
)

$testName = "Health Endpoint Test"
$endpoint = "http://localhost:8080/api/v1/timeline/health"

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
        $content = $response.Content | ConvertFrom-Json

        if (-not $Quiet) {
            Write-Host "[PASSED] Test completed successfully" -ForegroundColor Green
            Write-Host "Status: $($content.status)" -ForegroundColor White
            Write-Host "Service: $($content.service)" -ForegroundColor White
            Write-Host "Timestamp: $($content.timestamp)" -ForegroundColor White
        }

        if ($Quiet) {
            Write-Host "[OK] Health check passed" -ForegroundColor Green
        }

        exit 0
    } else {
        if (-not $Quiet) {
            Write-Host "[FAILED] Test failed" -ForegroundColor Red
            Write-Host "Unexpected status code: $($response.StatusCode)" -ForegroundColor Red
        } else {
            Write-Host "[ERROR] Health check failed - Status: $($response.StatusCode)" -ForegroundColor Red
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
        Write-Host "[ERROR] Health check failed - Connection error" -ForegroundColor Red
    }
    exit 1
}

if (-not $Quiet -and -not $NoPause) {
    Write-Host ""
    Read-Host "Press Enter to continue"
}
