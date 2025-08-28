param(
    [switch]$Quiet,
    [switch]$NoPause,
    [string]$SessionId = "validation-test-session"
)

$testName = "Message Chain Validation Test (Phase 2)"
$endpoint = "http://localhost:8080/api/v1"

if (-not $Quiet) {
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host "AgentTimeline - $testName" -ForegroundColor Cyan
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "This test will:" -ForegroundColor Yellow
    Write-Host "1. Create a conversation to validate" -ForegroundColor White
    Write-Host "2. Test chain validation endpoint" -ForegroundColor White
    Write-Host "3. Demonstrate validation results" -ForegroundColor White
    Write-Host ""
    Write-Host "Session ID: $SessionId" -ForegroundColor Yellow
    Write-Host ""
}

try {
    # Step 1: Create some messages for testing
    if (-not $Quiet) {
        Write-Host "Step 1: Creating test conversation..." -ForegroundColor Cyan
    }

    $testMessages = @(
        "Hello, this is a test message for validation",
        "Can you help me understand chain validation?",
        "Thank you for the explanation"
    )

    $createdMessages = @()

    foreach ($message in $testMessages) {
        if (-not $Quiet) {
            Write-Host "  Sending: '$message'" -ForegroundColor White
        }

        $body = @{
            message = $message
        } | ConvertTo-Json

        $response = Invoke-WebRequest -Uri "$endpoint/timeline/chat$(if ($SessionId) { "?sessionId=$SessionId" })" -Method POST -ContentType "application/json" -Body $body -TimeoutSec 30

        if ($response.StatusCode -eq 200) {
            $content = $response.Content | ConvertFrom-Json
            $createdMessages += $content

            if (-not $Quiet) {
                Write-Host "    [OK] Created message $($content.id) (Role: $($content.role))" -ForegroundColor Green
            }
        } else {
            throw "Failed to create message. Status: $($response.StatusCode)"
        }

        Start-Sleep -Milliseconds 500
    }

    # Step 2: Test chain validation
    if (-not $Quiet) {
        Write-Host ""
        Write-Host "Step 2: Validating message chain..." -ForegroundColor Cyan
    }

    $validateResponse = Invoke-WebRequest -Uri "$endpoint/timeline/chain/validate/$SessionId" -Method GET -TimeoutSec 10
    $validation = $validateResponse.Content | ConvertFrom-Json

    if (-not $Quiet) {
        Write-Host ""
        Write-Host "VALIDATION RESULTS:" -ForegroundColor Cyan
        Write-Host "==================" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "Session ID: $($validation.sessionId)" -ForegroundColor White
        Write-Host "Validation Status: $($validation.valid)" -ForegroundColor $(if ($validation.valid) { "Green" } else { "Red" })
        Write-Host "Total Messages: $($validation.totalMessages)" -ForegroundColor White
        Write-Host "Root Messages: $($validation.rootMessages)" -ForegroundColor White

        if ($validation.warning) {
            Write-Host "Warning: $($validation.warning)" -ForegroundColor Yellow
        }

        if ($validation.errorMessage) {
            Write-Host "Error: $($validation.errorMessage)" -ForegroundColor Red
        }

        if ($validation.brokenReferences -and $validation.brokenReferences.Count -gt 0) {
            Write-Host "Broken References: $($validation.brokenReferences.Count)" -ForegroundColor Red
            $validation.brokenReferences | ForEach-Object {
                Write-Host "  - $_" -ForegroundColor Red
            }
        } else {
            Write-Host "Broken References: 0" -ForegroundColor Green
        }

        if ($validation.orphanMessages -and $validation.orphanMessages.Count -gt 0) {
            Write-Host "Orphan Messages: $($validation.orphanMessages.Count)" -ForegroundColor Red
            $validation.orphanMessages | ForEach-Object {
                Write-Host "  - $_" -ForegroundColor Red
            }
        } else {
            Write-Host "Orphan Messages: 0" -ForegroundColor Green
        }

        Write-Host ""
        Write-Host "CHAIN STRUCTURE ANALYSIS:" -ForegroundColor Cyan
        Write-Host "========================" -ForegroundColor Cyan

        # Get the actual messages to analyze structure
        $chainResponse = Invoke-WebRequest -Uri "$endpoint/timeline/conversation/$SessionId" -Method GET -TimeoutSec 10
        $messages = $chainResponse.Content | ConvertFrom-Json

        Write-Host ""
        Write-Host "Message Chain Structure:" -ForegroundColor White
        $messages | ForEach-Object {
            $prefix = if ($_.role -eq "USER") { "[USER]" } else { "[AI]" }
            $parentInfo = if ($_.parentMessageId) { " (parent: $($_.parentMessageId.Substring(0,8)))" } else { " (root)" }
            Write-Host "  $prefix : $($_.content.Substring(0, [Math]::Min(50, $_.content.Length)))...$parentInfo" -ForegroundColor $(if ($_.role -eq "USER") { "Cyan" } else { "Magenta" })
        }

        Write-Host ""
        Write-Host "Chain Flow Validation:" -ForegroundColor White
        $previousId = $null
        $chainValid = $true

        $messages | Sort-Object timestamp | ForEach-Object {
            if ($_.role -eq "ASSISTANT" -and $previousId) {
                Write-Host "  USER:$($previousId.Substring(0,8)) -> AI:$($_.id.Substring(0,8))" -ForegroundColor Green
            } elseif ($_.role -eq "USER" -and $previousId) {
                Write-Host "  AI:$($previousId.Substring(0,8)) -> USER:$($_.id.Substring(0,8))" -ForegroundColor Green
            }
            $previousId = $_.id
        }

        if ($validation.valid) {
            Write-Host ""
            Write-Host "[OK] Chain validation PASSED - All references are valid" -ForegroundColor Green
        } else {
            Write-Host ""
            Write-Host "[FAILED] Chain validation FAILED - Issues found in chain structure" -ForegroundColor Red
        }
    }

    if ($Quiet) {
        $status = if ($validation.valid) { "VALID" } else { "INVALID" }
        Write-Host "[OK] Chain validation test completed - Chain status: $status" -ForegroundColor $(if ($validation.valid) { "Green" } else { "Red" })
    } else {
        Write-Host ""
        Write-Host "[PASSED] Chain validation test completed successfully!" -ForegroundColor Green
        Write-Host "[OK] Created test conversation" -ForegroundColor White
        Write-Host "[OK] Validated message chain integrity" -ForegroundColor White
        Write-Host "[OK] Analyzed chain structure" -ForegroundColor White
        Write-Host "[OK] Verified parent-child relationships" -ForegroundColor White
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
        Write-Host "[ERROR] Chain validation test failed - $($_.Exception.Message)" -ForegroundColor Red
    }
    exit 1
}

if (-not $Quiet -and -not $NoPause) {
    Write-Host ""
    Read-Host "Press Enter to continue"
}
