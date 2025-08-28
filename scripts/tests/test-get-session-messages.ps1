param(
    [switch]$Quiet,
    [switch]$NoPause,
    [string]$SessionId = "test-session"
)

$testName = "Get Session Messages Test"
$endpoint = "http://localhost:8080/api/v1/timeline/session/$SessionId"

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
            Write-Host "Found $($messages.Count) message(s) for session '$SessionId'" -ForegroundColor White
            Write-Host ""
        } else {
            Write-Host "[OK] Get session messages test passed - Found $($messages.Count) message(s)" -ForegroundColor Green
        }

        # Validate session filtering
        $invalidMessages = $messages | Where-Object { $_.sessionId -ne $SessionId }
        if ($invalidMessages.Count -gt 0) {
            if (-not $Quiet) {
                Write-Host "[WARNING] Found $($invalidMessages.Count) messages not belonging to session '$SessionId'" -ForegroundColor Yellow
            }
        }

        # Show details of messages if not quiet
        if (-not $Quiet -and $messages.Count -gt 0) {
            Write-Host "Session messages:" -ForegroundColor Cyan
            $messages | Select-Object -First 3 | ForEach-Object {
                $roleIcon = if ($_.role -eq "USER") { "👤" } else { "🤖" }
                $roleColor = if ($_.role -eq "USER") { "Cyan" } else { "Magenta" }
                Write-Host "  $roleIcon $($_.role): $($_.content)" -ForegroundColor $roleColor
                Write-Host "    ID: $($_.id)" -ForegroundColor White
                Write-Host "    Parent: $($_.parentMessageId)" -ForegroundColor White
                Write-Host "    Time: $($_.timestamp)" -ForegroundColor White
                if ($_.metadata -and $_.metadata.model) {
                    Write-Host "    Model: $($_.metadata.model)" -ForegroundColor White
                }
                Write-Host ""
            }

            if ($messages.Count -gt 3) {
                Write-Host "... and $($messages.Count - 3) more message(s)" -ForegroundColor Gray
                Write-Host ""
            }
        } elseif (-not $Quiet -and $messages.Count -eq 0) {
            Write-Host "No messages found for session '$SessionId'" -ForegroundColor Gray
            Write-Host "Try sending a message first with: .\scripts\tests\test-chat.ps1 -SessionId $SessionId" -ForegroundColor Yellow
            Write-Host ""
        }

        exit 0
    } else {
        if (-not $Quiet) {
            Write-Host "[FAILED] Test failed" -ForegroundColor Red
            Write-Host "Unexpected status code: $($response.StatusCode)" -ForegroundColor Red
        } else {
            Write-Host "[ERROR] Get session messages test failed - Status: $($response.StatusCode)" -ForegroundColor Red
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
        Write-Host "[ERROR] Get session messages test failed - Connection error" -ForegroundColor Red
    }
    exit 1
}

if (-not $Quiet -and -not $NoPause) {
    Write-Host ""
    Read-Host "Press Enter to continue"
}
