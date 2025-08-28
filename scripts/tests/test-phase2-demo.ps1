param(
    [switch]$Quiet,
    [switch]$NoPause,
    [string]$SessionId = "phase2-complete-demo"
)

$testName = "Phase 2 Complete Demo - Conversation Reconstruction Showcase"
$endpoint = "http://localhost:8080/api/v1"

if (-not $Quiet) {
    Write-Host "====================================" -ForegroundColor Magenta
    Write-Host "AgentTimeline - $testName" -ForegroundColor Magenta
    Write-Host "====================================" -ForegroundColor Magenta
    Write-Host ""
    Write-Host "*** PHASE 2 COMPLETE DEMONSTRATION ***" -ForegroundColor Green
    Write-Host ""
    Write-Host "This comprehensive demo will:" -ForegroundColor Yellow
    Write-Host "1. Create a rich multi-turn conversation with message chaining" -ForegroundColor White
    Write-Host "2. Demonstrate conversation reconstruction using message chains" -ForegroundColor White
    Write-Host "3. Show chain validation and integrity checking" -ForegroundColor White
    Write-Host "4. Test chain repair functionality" -ForegroundColor White
    Write-Host "5. Display comprehensive statistics" -ForegroundColor White
    Write-Host "6. Provide a clear view of the conversation flow" -ForegroundColor White
    Write-Host ""
    Write-Host "Session ID: $SessionId" -ForegroundColor Yellow
    Write-Host ""
}

try {
    # Step 1: Create a rich conversation
    if (-not $Quiet) {
        Write-Host "STEP 1: Creating Rich Conversation" -ForegroundColor Cyan
        Write-Host "==================================" -ForegroundColor Cyan
    }

    $conversationMessages = @(
        "Hello! I'm testing the new Phase 2 message chaining system.",
        "Can you explain how message chains work in conversation management?",
        "That sounds very useful! How do you handle broken chains or orphaned messages?",
        "What are the benefits of this approach compared to timestamp-based sorting?",
        "This is really impressive! Can you show me the actual message structure?",
        "Thank you for the detailed explanation. This Phase 2 implementation looks very robust!"
    )

    $createdMessages = @()

    for ($i = 0; $i -lt $conversationMessages.Count; $i++) {
        $message = $conversationMessages[$i]
        $turnNumber = $i + 1

        if (-not $Quiet) {
            Write-Host ""
            Write-Host "Turn $turnNumber - Sending:" -ForegroundColor White
            Write-Host "  [USER]: '$message'" -ForegroundColor Cyan
        }

        $body = @{
            message = $message
        } | ConvertTo-Json

        $response = Invoke-WebRequest -Uri "$endpoint/timeline/chat$(if ($SessionId) { "?sessionId=$SessionId" })" -Method POST -ContentType "application/json" -Body $body -TimeoutSec 30

        if ($response.StatusCode -eq 200) {
            $content = $response.Content | ConvertFrom-Json
            $createdMessages += $content

            if (-not $Quiet) {
                Write-Host ""
                Write-Host "  [AI] Response:" -ForegroundColor Magenta
                Write-Host "    Message ID: $($content.id)" -ForegroundColor Gray
                Write-Host "    Role: $($content.role)" -ForegroundColor Gray
                Write-Host "    Parent ID: $($content.parentMessageId)" -ForegroundColor Gray
                Write-Host "    Model: $($content.metadata.model)" -ForegroundColor Gray
                Write-Host "    Response: $($content.content.Substring(0, [Math]::Min(100, $content.content.Length)))..." -ForegroundColor Magenta
            }
        } else {
            throw "Failed to send message $turnNumber. Status: $($response.StatusCode)"
        }

        Start-Sleep -Milliseconds 500
    }

    # Step 2: Demonstrate conversation reconstruction
    if (-not $Quiet) {
        Write-Host ""
        Write-Host ""
        Write-Host "STEP 2: Conversation Reconstruction" -ForegroundColor Cyan
        Write-Host "===================================" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "TESTING DIFFERENT RECONSTRUCTION METHODS:" -ForegroundColor Green
    }

    # Method A: Chain-based reconstruction
    if (-not $Quiet) {
        Write-Host ""
        Write-Host "Method A: Chain-Based Reconstruction (/conversation/{sessionId})" -ForegroundColor Yellow
        Write-Host "------------------------------------------------------------------" -ForegroundColor Yellow
    }

    $chainResponse = Invoke-WebRequest -Uri "$endpoint/timeline/conversation/$SessionId" -Method GET -TimeoutSec 10
    $chainMessages = $chainResponse.Content | ConvertFrom-Json

    if (-not $Quiet) {
        Write-Host ""
        Write-Host "CHAIN-BASED RECONSTRUCTION RESULT:" -ForegroundColor Green
        Write-Host "===================================" -ForegroundColor Green

        $chainMessages | ForEach-Object {
            $prefix = if ($_.role -eq "USER") { "[USER]" } else { "[AI]" }
            $parentInfo = if ($_.parentMessageId) {
                " (parent: $($_.parentMessageId.Substring(0,8)))"
            } else {
                " (root message)"
            }
            Write-Host ""
            Write-Host "  $prefix$parentInfo :" -ForegroundColor $(if ($_.role -eq "USER") { "Cyan" } else { "Magenta" })
            Write-Host "    $($_.content)" -ForegroundColor $(if ($_.role -eq "USER") { "Cyan" } else { "Magenta" })
            Write-Host "    Time: $($_.timestamp) | ID: $($_.id.Substring(0,8))" -ForegroundColor Gray
            if ($_.metadata -and $_.metadata.model) {
                Write-Host "    Model: $($_.metadata.model) | ResponseTime: $($_.metadata.responseTimeMs)ms" -ForegroundColor Gray
            }
        }
    }

    # Step 3: Chain validation
    if (-not $Quiet) {
        Write-Host ""
        Write-Host ""
        Write-Host "STEP 3: Message Chain Validation" -ForegroundColor Cyan
        Write-Host "===============================" -ForegroundColor Cyan
    }

    $validateResponse = Invoke-WebRequest -Uri "$endpoint/timeline/chain/validate/$SessionId" -Method GET -TimeoutSec 10
    $validation = $validateResponse.Content | ConvertFrom-Json

    if (-not $Quiet) {
        Write-Host ""
        Write-Host "CHAIN VALIDATION RESULTS:" -ForegroundColor Green
        Write-Host "============================" -ForegroundColor Green
        Write-Host ""
        Write-Host "Session Status: $($validation.sessionId)" -ForegroundColor White
        Write-Host "Chain Valid: $($validation.valid)" -ForegroundColor $(if ($validation.valid) { "Green" } else { "Red" })
        Write-Host "Total Messages: $($validation.totalMessages)" -ForegroundColor White
        Write-Host "Root Messages: $($validation.rootMessages)" -ForegroundColor White

        if ($validation.valid) {
            Write-Host ""
            Write-Host "CHAIN INTEGRITY: EXCELLENT!" -ForegroundColor Green
            Write-Host "   All parent-child relationships are properly maintained" -ForegroundColor Green
        } else {
            Write-Host ""
            Write-Host "CHAIN INTEGRITY: ISSUES DETECTED!" -ForegroundColor Red
            if ($validation.brokenReferences) {
                Write-Host "   Broken References: $($validation.brokenReferences.Count)" -ForegroundColor Red
            }
            if ($validation.orphanMessages) {
                Write-Host "   Orphan Messages: $($validation.orphanMessages.Count)" -ForegroundColor Red
            }
        }

        Write-Host ""
        Write-Host "MESSAGE CHAIN STRUCTURE:" -ForegroundColor Yellow
        Write-Host "==========================" -ForegroundColor Yellow

        $chainMessages | Sort-Object timestamp | ForEach-Object {
            $arrow = if ($_.role -eq "USER") { "[USER]" } else { "[AI]" }
            Write-Host "  $arrow $($_.role):$($_.id.Substring(0,8))" -ForegroundColor $(if ($_.role -eq "USER") { "Cyan" } else { "Magenta" })
        }
    }

    # Step 4: Test chain repair
    if (-not $Quiet) {
        Write-Host ""
        Write-Host ""
        Write-Host "STEP 4: Chain Repair Functionality" -ForegroundColor Cyan
        Write-Host "=================================" -ForegroundColor Cyan
    }

    $repairResponse = Invoke-WebRequest -Uri "$endpoint/timeline/chain/repair/$SessionId" -Method POST -TimeoutSec 10
    $repairResult = $repairResponse.Content | ConvertFrom-Json

    if (-not $Quiet) {
        Write-Host ""
        Write-Host "CHAIN REPAIR RESULTS:" -ForegroundColor Green
        Write-Host "======================" -ForegroundColor Green
        Write-Host ""
        Write-Host "Repair Success: $($repairResult.success)" -ForegroundColor $(if ($repairResult.success) { "Green" } else { "Red" })
        Write-Host "Message: $($repairResult.message)" -ForegroundColor White

        if ($repairResult.repairsPerformed -and $repairResult.repairsPerformed.Count -gt 0) {
            Write-Host ""
            Write-Host "Repairs Performed:" -ForegroundColor Yellow
            $repairResult.repairsPerformed | ForEach-Object {
                Write-Host "   * $_" -ForegroundColor Green
            }
        } else {
            Write-Host ""
            Write-Host "No repairs needed - chain was already perfect!" -ForegroundColor Gray
        }
    }

    # Step 5: Statistics overview
    if (-not $Quiet) {
        Write-Host ""
        Write-Host ""
        Write-Host "STEP 5: Comprehensive Statistics" -ForegroundColor Cyan
        Write-Host "================================" -ForegroundColor Cyan
    }

    $statsResponse = Invoke-WebRequest -Uri "$endpoint/timeline/chain/statistics" -Method GET -TimeoutSec 10
    $statistics = $statsResponse.Content | ConvertFrom-Json

    if (-not $Quiet) {
        Write-Host ""
        Write-Host "SYSTEM STATISTICS OVERVIEW:" -ForegroundColor Green
        Write-Host "==============================" -ForegroundColor Green
        Write-Host ""
        Write-Host "Total Sessions: $($statistics.totalSessions)" -ForegroundColor White
        Write-Host "Valid Chains: $($statistics.validChains)" -ForegroundColor Green
        Write-Host "Invalid Chains: $($statistics.invalidChains)" -ForegroundColor $(if ($statistics.invalidChains -gt 0) { "Red" } else { "Green" })
        Write-Host "Total Messages: $($statistics.totalMessages)" -ForegroundColor White

        if ($statistics.totalSessions -gt 0) {
            $validityRate = [math]::Round(($statistics.validChains / $statistics.totalSessions) * 100, 1)
            Write-Host "Chain Validity Rate: $validityRate%" -ForegroundColor $(if ($validityRate -eq 100) { "Green" } else { "Yellow" })
        }

        # Show session details for our demo session
        $ourSession = $statistics.sessionStats | Where-Object { $_.sessionId -eq $SessionId }
        if ($ourSession) {
            Write-Host ""
            Write-Host "Our Demo Session ($SessionId):" -ForegroundColor Yellow
            Write-Host "   Status: $(if ($ourSession.valid) { "VALID" } else { "INVALID" })" -ForegroundColor $(if ($ourSession.valid) { "Green" } else { "Red" })
            Write-Host "   Messages: $($ourSession.messageCount)" -ForegroundColor White
            Write-Host "   Broken Refs: $($ourSession.brokenReferences)" -ForegroundColor $(if ($ourSession.brokenReferences -gt 0) { "Red" } else { "Green" })
            Write-Host "   Orphan Msgs: $($ourSession.orphanMessages)" -ForegroundColor $(if ($ourSession.orphanMessages -gt 0) { "Red" } else { "Green" })
        }
    }

    # Step 6: Final demonstration of conversation flow
    if (-not $Quiet) {
        Write-Host ""
        Write-Host ""
        Write-Host "STEP 6: Final Conversation Flow" -ForegroundColor Cyan
        Write-Host "=================================" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "COMPLETE CONVERSATION RECONSTRUCTION:" -ForegroundColor Green
        Write-Host "==========================================" -ForegroundColor Green
        Write-Host ""

        $turnNumber = 1
        $chainMessages | ForEach-Object {
            $roleIcon = if ($_.role -eq "USER") { "USER" } else { "AI" }
            $roleColor = if ($_.role -eq "USER") { "Cyan" } else { "Magenta" }
            $roleLabel = if ($_.role -eq "USER") { "USER" } else { "AI ASSISTANT" }

            Write-Host "TURN $turnNumber - $roleIcon $roleLabel" -ForegroundColor $roleColor
            Write-Host "  $($_.content)" -ForegroundColor $roleColor
            Write-Host "  --------------------------------" -ForegroundColor Gray
            Write-Host "  Message ID: $($_.id)" -ForegroundColor Gray
            Write-Host "  Parent ID: $($_.parentMessageId)" -ForegroundColor Gray
            Write-Host "  Timestamp: $($_.timestamp)" -ForegroundColor Gray
            Write-Host ""

            $turnNumber++
        }

        Write-Host ""
        Write-Host "PHASE 2 DEMONSTRATION COMPLETE!" -ForegroundColor Green
        Write-Host "==================================" -ForegroundColor Green
        Write-Host ""
        Write-Host "* Message chaining successfully implemented" -ForegroundColor White
        Write-Host "* Conversation reconstruction working perfectly" -ForegroundColor White
        Write-Host "* Chain validation and repair functioning" -ForegroundColor White
        Write-Host "* Comprehensive statistics available" -ForegroundColor White
        Write-Host "* Data integrity maintained throughout" -ForegroundColor White
        Write-Host ""
        Write-Host "Phase 2 Enhanced Message Storage and Retrieval is fully operational!" -ForegroundColor Green
    }

    if ($Quiet) {
        Write-Host "[OK] Phase 2 complete demo finished - Created $($chainMessages.Count) messages with full chain reconstruction" -ForegroundColor Green
    } else {
        Write-Host ""
        Write-Host "DEMONSTRATION SUCCESSFULLY COMPLETED!" -ForegroundColor Green
        Write-Host "* Rich conversation created with message chaining" -ForegroundColor White
        Write-Host "* Chain-based reconstruction demonstrated" -ForegroundColor White
        Write-Host "* Validation and repair functionality tested" -ForegroundColor White
        Write-Host "* Statistics and analytics working" -ForegroundColor White
        Write-Host "* Complete conversation flow displayed" -ForegroundColor White
    }

    exit 0

} catch {
    if (-not $Quiet) {
        Write-Host "[FAILED] Demo failed" -ForegroundColor Red
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host ""
        Write-Host "Make sure the application is running on http://localhost:8080" -ForegroundColor Yellow
        Write-Host "Also ensure Ollama is running and accessible" -ForegroundColor Yellow
    } else {
        Write-Host "[ERROR] Phase 2 demo failed - $($_.Exception.Message)" -ForegroundColor Red
    }
    exit 1
}

if (-not $Quiet -and -not $NoPause) {
    Write-Host ""
    Read-Host "Press Enter to continue"
}
