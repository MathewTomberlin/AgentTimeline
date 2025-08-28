param(
    [switch]$NoPause,
    [switch]$Verbose,
    [switch]$SkipChatTest,
    [string]$ChatSessionId = "test-runner-session"
)

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "AgentTimeline - Test Runner" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host "Running comprehensive API tests..." -ForegroundColor Yellow
Write-Host ""

# Test configuration
$tests = @(
    @{
        Name = "Health Endpoint"
        Script = ".\scripts\tests\test-health.ps1"
        Description = "Test application health and basic connectivity"
    },
    @{
        Name = "Chat Endpoint"
        Script = ".\scripts\tests\test-chat.ps1"
        Description = "Test AI chat functionality with Ollama integration"
        Skip = $SkipChatTest
    },
    @{
        Name = "Get All Messages"
        Script = ".\scripts\tests\test-get-messages.ps1"
        Description = "Test retrieving all stored messages from Redis"
    },
    @{
        Name = "Get Session Messages"
        Script = ".\scripts\tests\test-get-session-messages.ps1"
        Description = "Test session-based message filtering"
    }
)

# Results tracking
$results = @()
$passed = 0
$total = 0

# Function to run a test
function Run-Test {
    param($test)

    if ($test.Skip) {
        Write-Host "[SKIPPED] $($test.Name)" -ForegroundColor Yellow
        Write-Host "   $($test.Description)" -ForegroundColor Gray
        Write-Host ""

        return @{
            Name = $test.Name
            Status = "Skipped"
            ExitCode = 0
            Duration = 0
        }
    }

    $startTime = Get-Date

    if ($Verbose) {
        Write-Host "[TEST] Running: $($test.Name)" -ForegroundColor Cyan
        Write-Host "   $($test.Description)" -ForegroundColor Gray
        Write-Host ""
    } else {
        Write-Host "[TEST] $($test.Name)..." -ForegroundColor Cyan -NoNewline
    }

    try {
        $process = Start-Process -FilePath "powershell.exe" -ArgumentList "-ExecutionPolicy Bypass -File `"$($test.Script)`" -Quiet" -NoNewWindow -PassThru -Wait
        $exitCode = $process.ExitCode
    } catch {
        $exitCode = -1
    }

    $endTime = Get-Date
    $duration = [math]::Round(($endTime - $startTime).TotalSeconds, 2)

    if (-not $Verbose) {
        if ($exitCode -eq 0) {
            Write-Host " [PASSED] ($($duration)s)" -ForegroundColor Green
        } else {
            Write-Host " [FAILED] ($($duration)s)" -ForegroundColor Red
        }
    }

    return @{
        Name = $test.Name
        Status = if ($exitCode -eq 0) { "Passed" } else { "Failed" }
        ExitCode = $exitCode
        Duration = $duration
    }
}

# Pre-flight checks
Write-Host "Performing pre-flight checks..." -ForegroundColor Yellow
Write-Host ""

# Check if application is running
$healthCheck = Run-Test -test @{
    Name = "Application Health Check"
    Script = ".\scripts\tests\test-health.ps1"
    Description = "Verify application is running"
}

if ($healthCheck.ExitCode -ne 0) {
    Write-Host ""
    Write-Host "[ERROR] Application is not running!" -ForegroundColor Red
    Write-Host ""
    Write-Host "To start the application, run one of these commands:" -ForegroundColor Yellow
    Write-Host "  - .\scripts\start-app.ps1          (Start just the app)" -ForegroundColor White
    Write-Host "  - .\scripts\start-full.ps1         (Start Redis + Ollama + App)" -ForegroundColor White
    Write-Host "  - .\scripts\restart-full.ps1       (Restart all services)" -ForegroundColor White
    Write-Host ""

    if (-not $NoPause) {
        Read-Host "Press Enter to exit"
    }
    exit 1
}

Write-Host "[OK] Application is running" -ForegroundColor Green
Write-Host ""

# Run all tests
Write-Host "Running test suite..." -ForegroundColor Yellow
Write-Host ""

foreach ($test in $tests) {
    $result = Run-Test -test $test
    $results += $result
    $total++

    if ($result.Status -eq "Passed") {
        $passed++
    }
}

# Summary
Write-Host ""
Write-Host "====================================" -ForegroundColor Cyan
Write-Host "Test Results Summary" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

$results | ForEach-Object {
    $statusColor = switch ($_.Status) {
        "Passed" { "Green" }
        "Failed" { "Red" }
        "Skipped" { "Yellow" }
    }
    Write-Host "$($_.Name): " -NoNewline
    Write-Host "$($_.Status)" -ForegroundColor $statusColor -NoNewline
    Write-Host " ($($_.Duration)s)" -ForegroundColor Gray
}

Write-Host ""
Write-Host "Total Tests: $total" -ForegroundColor White
Write-Host "Passed: $passed" -ForegroundColor Green
Write-Host "Failed: $($total - $passed)" -ForegroundColor Red

$successRate = if ($total -gt 0) { [math]::Round(($passed / $total) * 100, 1) } else { 0 }
Write-Host "Success Rate: $successRate%" -ForegroundColor $(if ($successRate -eq 100) { "Green" } elseif ($successRate -ge 50) { "Yellow" } else { "Red" })

Write-Host ""

if ($passed -eq $total) {
    Write-Host "[SUCCESS] All tests passed!" -ForegroundColor Green
} elseif ($passed -gt 0) {
    Write-Host "[WARNING] Some tests failed. Check the output above." -ForegroundColor Yellow
} else {
    Write-Host "[ERROR] All tests failed!" -ForegroundColor Red
}

if (-not $NoPause) {
    Write-Host ""
    Read-Host "Press Enter to continue"
}

# Return exit code based on test results
exit $(if ($passed -eq $total) { 0 } else { 1 })
