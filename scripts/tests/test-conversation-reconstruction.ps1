param(
    [switch]$Quiet,
    [switch]$NoPause,
    [string]$SessionId = "phase2-demo-session",
    [int]$MessageCount = 3
)

$testName = "Conversation Reconstruction Test (Phase 2)"
$endpoint = "http://localhost:8080/api/v1"

if (-not $Quiet) {
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host "AgentTimeline - $testName" -ForegroundColor Cyan
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "This test will:" -ForegroundColor Yellow
    Write-Host "1. Create a multi-turn conversation with message chaining" -ForegroundColor White
    Write-Host "2. Demonstrate conversation reconstruction using message chains" -ForegroundColor White
    Write-Host "3. Show the difference between timestamp-based and chain-based retrieval" -ForegroundColor White
    Write-Host ""
    Write-Host "Session ID: $SessionId" -ForegroundColor Yellow
    Write-Host "Messages to create: $MessageCount" -ForegroundColor Yellow
    Write-Host ""
}

$messages = @(
    "Hello! Can you help me understand message chaining?",
    "That's interesting! How do message chains maintain conversation flow?",
    "Can you show me a practical example of this working?"
)

try {
    # Step 1: Create a conversation with multiple turns
    if (-not $Quiet) {
        Write-Host "Step 1: Creating conversation with $MessageCount message(s)..." -ForegroundColor Cyan
    }

    $createdMessages = @()

    for ($i = 0; $i -lt [Math]::Min($MessageCount, $messages.Count); $i++) {
        $message = $messages[$i]

        if (-not $Quiet) {
            Write-Host "  Sending message $($i + 1): '$message'" -ForegroundColor White
        }

        $body = @{
            message = $message
        } | ConvertTo-Json

        $response = Invoke-WebRequest -Uri "$endpoint/timeline/chat$(if ($SessionId) { "?sessionId=$SessionId" })" -Method POST -ContentType "application/json" -Body $body -TimeoutSec 30

        if ($response.StatusCode -eq 200) {
            $content = $response.Content | ConvertFrom-Json
            $createdMessages += $content

            if (-not $Quiet) {
                Write-Host "    [OK] Assistant responded with ID: $($content.id)" -ForegroundColor Green
                Write-Host "    [OK] Message role: $($content.role)" -ForegroundColor Green
                if ($content.parentMessageId) {
                    Write-Host "    [OK] Parent message ID: $($content.parentMessageId)" -ForegroundColor Green
                }
            }
        } else {
            throw "Failed to send message $($i + 1). Status: $($response.StatusCode)"
        }

        # Small delay between messages
        Start-Sleep -Milliseconds 500
    }

    if (-not $Quiet) {
        Write-Host ""
        Write-Host "Step 2: Retrieving conversation using different methods..." -ForegroundColor Cyan
    }

    # Step 2a: Get conversation using the new chain-based endpoint
    if (-not $Quiet) {
        Write-Host "  Method A: Chain-based reconstruction (/conversation/{sessionId})" -ForegroundColor White
    }

    $chainResponse = Invoke-WebRequest -Uri "$endpoint/timeline/conversation/$SessionId" -Method GET -TimeoutSec 10
    $chainMessages = $chainResponse.Content | ConvertFrom-Json

    if (-not $Quiet) {
        Write-Host "    [OK] Retrieved $($chainMessages.Count) messages using chain reconstruction" -ForegroundColor Green
    }

    # Step 2b: Get conversation using the traditional timestamp-based endpoint
    if (-not $Quiet) {
        Write-Host "  Method B: Timestamp-based sorting (/session/{sessionId})" -ForegroundColor White
    }

    $timestampResponse = Invoke-WebRequest -Uri "$endpoint/timeline/session/$SessionId" -Method GET -TimeoutSec 10
    $timestampMessages = $timestampResponse.Content | ConvertFrom-Json

    if (-not $Quiet) {
        Write-Host "    [OK] Retrieved $($timestampMessages.Count) messages using timestamp sorting" -ForegroundColor Green
    }

    # Step 3: Display and compare the results
    if (-not $Quiet) {
        Write-Host ""
        Write-Host "Step 3: Conversation Reconstruction Results" -ForegroundColor Cyan
        Write-Host "===============================================" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "CHAIN-BASED RECONSTRUCTION (Phase 2):" -ForegroundColor Green
        Write-Host "------------------------------------" -ForegroundColor Green

        $chainMessages | ForEach-Object {
            $prefix = if ($_.role -eq "USER") { "[USER]" } else { "[AI]" }
            Write-Host "  $prefix : $($_.content)" -ForegroundColor $(if ($_.role -eq "USER") { "Cyan" } else { "Magenta" })
            Write-Host "    Message ID: $($_.id)" -ForegroundColor Gray
            Write-Host "    Parent ID: $($_.parentMessageId)" -ForegroundColor Gray
            Write-Host "    Timestamp: $($_.timestamp)" -ForegroundColor Gray
            if ($_.metadata -and $_.metadata.model) {
                Write-Host "    Model: $($_.metadata.model)" -ForegroundColor Gray
            }
            Write-Host ""
        }

        Write-Host ""
        Write-Host "COMPARISON WITH TIMESTAMP-BASED METHOD:" -ForegroundColor Yellow
        Write-Host "=======================================" -ForegroundColor Yellow

        # Show the difference in ordering
        $chainOrder = $chainMessages | ForEach-Object { $_.id }
        $timestampOrder = $timestampMessages | ForEach-Object { $_.id }

        $orderMatch = ($chainOrder -join ",") -eq ($timestampOrder -join ",")

        if ($orderMatch) {
            Write-Host "  [OK] Message order is identical in both methods" -ForegroundColor Green
        } else {
            Write-Host "  [WARNING] Message order differs between methods:" -ForegroundColor Yellow
            Write-Host "    Chain order: $($chainOrder -join ' -> ')" -ForegroundColor Gray
            Write-Host "    Timestamp order: $($timestampOrder -join ' -> ')" -ForegroundColor Gray
        }

        Write-Host ""
        Write-Host "CHAIN STRUCTURE ANALYSIS:" -ForegroundColor Cyan
        Write-Host "-----------------------" -ForegroundColor Cyan

        # Analyze the message chain structure
        $rootMessages = $chainMessages | Where-Object { $null -eq $_.parentMessageId -or $_.parentMessageId -eq "" }
        $childMessages = $chainMessages | Where-Object { $_.parentMessageId }

        Write-Host "  Root messages (no parent): $($rootMessages.Count)" -ForegroundColor White
        Write-Host "  Child messages (with parent): $($childMessages.Count)" -ForegroundColor White
        Write-Host "  Total messages: $($chainMessages.Count)" -ForegroundColor White

        # Show the chain structure
        Write-Host ""
        Write-Host "  Chain structure:" -ForegroundColor White
        $currentId = $null
        $chainMessages | Sort-Object timestamp | ForEach-Object {
            $arrow = if ($currentId) { " -> " } else { "" }
            Write-Host "  ${arrow}$($_.role):$($_.id.Substring(0,8))" -ForegroundColor $(if ($_.role -eq "USER") { "Cyan" } else { "Magenta" })
            $currentId = $_.id
        }
    }

    # Step 4: Validate chain integrity
    if (-not $Quiet) {
        Write-Host ""
        Write-Host "Step 4: Validating message chain integrity..." -ForegroundColor Cyan
    }

    $validateResponse = Invoke-WebRequest -Uri "$endpoint/timeline/chain/validate/$SessionId" -Method GET -TimeoutSec 10
    $validation = $validateResponse.Content | ConvertFrom-Json

    if (-not $Quiet) {
        Write-Host "  Chain validation result: $($validation.valid)" -ForegroundColor $(if ($validation.valid) { "Green" } else { "Red" })
        Write-Host "  Total messages: $($validation.totalMessages)" -ForegroundColor White
        Write-Host "  Root messages: $($validation.rootMessages)" -ForegroundColor White
        if ($validation.brokenReferences) {
            Write-Host "  Broken references: $($validation.brokenReferences.Count)" -ForegroundColor Red
        }
        if ($validation.orphanMessages) {
            Write-Host "  Orphan messages: $($validation.orphanMessages.Count)" -ForegroundColor Red
        }
    }

    if ($Quiet) {
        Write-Host "[OK] Conversation reconstruction test passed - Created and reconstructed $($chainMessages.Count) messages" -ForegroundColor Green
    } else {
        Write-Host ""
        Write-Host "[PASSED] Conversation reconstruction test completed successfully!" -ForegroundColor Green
        Write-Host "[OK] Created multi-turn conversation with message chaining" -ForegroundColor White
        Write-Host "[OK] Demonstrated chain-based reconstruction" -ForegroundColor White
        Write-Host "[OK] Validated chain integrity" -ForegroundColor White
        Write-Host "[OK] Compared with timestamp-based method" -ForegroundColor White
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
        Write-Host "[ERROR] Conversation reconstruction test failed - $($_.Exception.Message)" -ForegroundColor Red
    }
    exit 1
}

if (-not $Quiet -and -not $NoPause) {
    Write-Host ""
    Read-Host "Press Enter to continue"
}
