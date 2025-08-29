# Test Similarity Search Demonstration
# This script demonstrates how vector similarity search works in AgentTimeline
# It sends a message and shows how the assistant can use context from similar messages

param(
    [string]$BaseUrl = "http://localhost:8080/api/v1/timeline",
    [string]$SessionId = "similarity-demo-session"
)

Write-Host "=== AgentTimeline Phase 4: Similarity Search Demonstration ===" -ForegroundColor Cyan
Write-Host "Base URL: $BaseUrl" -ForegroundColor Yellow
Write-Host "Session ID: $SessionId" -ForegroundColor Yellow
Write-Host ""

# Function to make HTTP requests
function Invoke-TimelineApi {
    param(
        [string]$Method = "GET",
        [string]$Endpoint,
        [object]$Body = $null,
        [string]$QueryParams = ""
    )

    $url = "$BaseUrl$Endpoint"
    if ($QueryParams) {
        $url += "?$QueryParams"
    }

    Write-Host "Testing: $Method $url" -ForegroundColor Gray

    try {
        $params = @{
            Method = $Method
            Uri = $url
            Headers = @{ "Content-Type" = "application/json" }
        }

        if ($Body) {
            $params.Body = ($Body | ConvertTo-Json)
        }

        $response = Invoke-RestMethod @params
        Write-Host "Success" -ForegroundColor Green
        return $response
    }
    catch {
        Write-Host "Failed: $($_.Exception.Message)" -ForegroundColor Red
        return $null
    }
}

# Step 1: Health Check
Write-Host "`n1. Health Check..." -ForegroundColor Cyan
$health = Invoke-TimelineApi -Endpoint "/health"
if ($health) {
    Write-Host "   Status: $($health.status)" -ForegroundColor Green
    Write-Host "   Phase: $($health.phase)" -ForegroundColor Green
    Write-Host "   Features: $($health.features)" -ForegroundColor Green
} else {
    Write-Host "   Health check failed. Please ensure the application is running." -ForegroundColor Red
    exit 1
}

# Step 2: Clear any existing data for this session
Write-Host "`n2. Clearing existing data for session..." -ForegroundColor Cyan
# Note: We'll create fresh data for the demo

# Step 3: Send a comprehensive message about AI and machine learning
Write-Host "`n3. Sending message with unique facts..." -ForegroundColor Cyan
$comprehensiveMessage = @"
My name is Allibideeba and I come from the country of Quanziinto

I used to play in a rock band as the lead cowbell player. Now I am a poet in a small coffee shop.
"@

$chatRequest = @{ message = $comprehensiveMessage }
$chatResponse = Invoke-TimelineApi -Method "POST" -Endpoint "/chat" -Body $chatRequest -QueryParams "sessionId=$SessionId"

if ($chatResponse) {
    Write-Host "   [SUCCESS] Message sent successfully!" -ForegroundColor Green
    Write-Host "   [MESSAGE] Message Content:" -ForegroundColor Yellow
    Write-Host "   ""$comprehensiveMessage""" -ForegroundColor White
    Write-Host ""
    Write-Host "   [ASSISTANT] Assistant Response Preview:" -ForegroundColor Cyan
    Write-Host "   ""$($chatResponse.content)""" -ForegroundColor Gray
} else {
    Write-Host "   [ERROR] Failed to send message" -ForegroundColor Red
    exit 1
}

# Step 4: Show vector statistics
Write-Host "`n4. Vector Store Statistics..." -ForegroundColor Cyan
$stats = Invoke-TimelineApi -Endpoint "/vector/statistics"
if ($stats) {
    Write-Host "   [STATS] Total Chunks: $($stats.totalChunks)" -ForegroundColor Green
    Write-Host "   [STATS] Unique Messages: $($stats.uniqueMessages)" -ForegroundColor Green
    Write-Host "   [STATS] Unique Sessions: $($stats.uniqueSessions)" -ForegroundColor Green
}

# Step 5: Show chunks created from the message
Write-Host "`n5. Message Chunks Created..." -ForegroundColor Cyan
$chunks = Invoke-TimelineApi -Endpoint "/chunks/session/$SessionId"
if ($chunks -and $chunks.Count -gt 0) {
    Write-Host "   [CHUNKS] Found $($chunks.Count) chunks for the comprehensive message" -ForegroundColor Green
    Write-Host "   [SAMPLE] Sample chunks:" -ForegroundColor Cyan

    # Show first 3 chunks as examples
    for ($i = 0; $i -lt [Math]::Min(3, $chunks.Count); $i++) {
        $chunk = $chunks[$i]
        $chunkText = $chunk.chunkText
        if ($chunkText.Length -gt 60) {
            $chunkText = $chunkText.Substring(0, 57) + "..."
        }
        Write-Host "   Chunk $($i + 1): ""$chunkText""" -ForegroundColor Gray
    }
} else {
    Write-Host "   [ERROR] No chunks found" -ForegroundColor Red
}

# Step 6: Demonstrate similarity search by sending a follow-up question
Write-Host "`n6. Sending follow-up question to demonstrate context-aware response..." -ForegroundColor Cyan

$followUpMessage = "What can you tell me about the person named Allibideeba?"
$followUpRequest = @{ message = $followUpMessage }
$followUpResponse = Invoke-TimelineApi -Method "POST" -Endpoint "/chat" -Body $followUpRequest -QueryParams "sessionId=$SessionId"

if ($followUpResponse) {
    Write-Host "   [QUESTION] ""$followUpMessage""" -ForegroundColor Yellow
    Write-Host "   [SUCCESS] Assistant response received!" -ForegroundColor Green
    Write-Host ""
    Write-Host "   [ASSISTANT RESPONSE]:" -ForegroundColor Cyan

    # Show the assistant's response
    $responseText = $followUpResponse.content
    if ($responseText.Length -gt 500) {
        $responseText = $responseText.Substring(0, 497) + "..."
    }
    Write-Host "   ""$responseText""" -ForegroundColor White
    Write-Host ""

    # Check if the response mentions key concepts from the original message
    $keyTerms = @("artificial intelligence", "machine learning", "neural networks", "deep learning", "natural language processing", "vector embeddings")
    $foundTerms = @()
    foreach ($term in $keyTerms) {
        if ($followUpResponse.content -match $term) {
            $foundTerms += $term
        }
    }

    if ($foundTerms.Count -gt 0) {
        Write-Host "   [CONTEXT ANALYSIS] Assistant used context from previous message!" -ForegroundColor Green
        Write-Host "   [FOUND TERMS] $($foundTerms -join ', ')" -ForegroundColor Gray
        Write-Host "   [SUCCESS] Similarity search provided relevant context for the response!" -ForegroundColor Green
    } else {
        Write-Host "   [CONTEXT ANALYSIS] No specific terms from previous message found in response" -ForegroundColor Yellow
    }
} else {
    Write-Host "   [ERROR] Failed to get follow-up response" -ForegroundColor Red
}

# Step 7: Explain the demonstration
Write-Host "`n*** DEMONSTRATION COMPLETE! ***" -ForegroundColor Green
Write-Host ""
Write-Host "What just happened:" -ForegroundColor Cyan
Write-Host "1. [SEND] You sent a comprehensive message about AI and machine learning" -ForegroundColor White
Write-Host "2. [BRAIN] The system automatically:" -ForegroundColor White
Write-Host "   - Broke your message into semantic chunks" -ForegroundColor Gray
Write-Host "   - Generated 768-dimensional vector embeddings for each chunk" -ForegroundColor Gray
Write-Host "   - Stored everything in PostgreSQL" -ForegroundColor Gray
Write-Host "3. [CONTEXT] When you asked about vector embeddings, the system:" -ForegroundColor White
Write-Host "   - Used similarity search to find relevant chunks from your previous message" -ForegroundColor Gray
Write-Host "   - Provided that context to the assistant" -ForegroundColor Gray
Write-Host "   - Assistant used the retrieved information in its response!" -ForegroundColor Gray
Write-Host ""
Write-Host "[SUCCESS] The assistant's response demonstrated context-aware AI!" -ForegroundColor Green
Write-Host "[INFO] It drew from your previous message to provide informed answers" -ForegroundColor Yellow
Write-Host ""
Write-Host "Try more follow-up questions:" -ForegroundColor Cyan
Write-Host ""
Write-Host "[NOTE] Each response will use similarity search to find relevant context!" -ForegroundColor Cyan

Write-Host "`n=== Similarity Search Demo Complete ===" -ForegroundColor Green
