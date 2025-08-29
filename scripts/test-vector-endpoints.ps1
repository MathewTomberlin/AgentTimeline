# Test Vector Endpoints for Phase 4
# This script tests the new vector search and embedding functionality

param(
    [string]$BaseUrl = "http://localhost:8080/api/v1/timeline",
    [string]$SessionId = "test-vector-session"
)

Write-Host "=== AgentTimeline Phase 4 Vector Endpoints Test ===" -ForegroundColor Cyan
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

# Test 1: Health Check
Write-Host "`n1. Testing Health Check..." -ForegroundColor Cyan
$health = Invoke-TimelineApi -Endpoint "/health"
if ($health) {
    Write-Host "   Status: $($health.status)" -ForegroundColor Green
    Write-Host "   Phase: $($health.phase)" -ForegroundColor Green
    Write-Host "   Features: $($health.features)" -ForegroundColor Green
}

# Test 2: Send a chat message to generate embeddings
Write-Host "`n2. Sending chat message to generate embeddings..." -ForegroundColor Cyan
$chatRequest = @{ message = "This is a test message about artificial intelligence and machine learning. It should be chunked and embedded for vector search." }
$chatResponse = Invoke-TimelineApi -Method "POST" -Endpoint "/chat" -Body $chatRequest -QueryParams "sessionId=$SessionId"
if ($chatResponse) {
    Write-Host "   Message processed successfully" -ForegroundColor Green
    Write-Host "   Response length: $($chatResponse.content.Length) characters" -ForegroundColor Green
}

# Wait a moment for async processing
Start-Sleep -Seconds 2

# Test 3: Get vector store statistics
Write-Host "`n3. Getting vector store statistics..." -ForegroundColor Cyan
$stats = Invoke-TimelineApi -Endpoint "/vector/statistics"
if ($stats) {
    Write-Host "   Total chunks: $($stats.totalChunks)" -ForegroundColor Green
    Write-Host "   Unique messages: $($stats.uniqueMessages)" -ForegroundColor Green
    Write-Host "   Unique sessions: $($stats.uniqueSessions)" -ForegroundColor Green
}

# Test 4: Get chunks for session
Write-Host "`n4. Getting chunks for session..." -ForegroundColor Cyan
$chunks = Invoke-TimelineApi -Endpoint "/chunks/session/$SessionId"
if ($chunks -and $chunks.Count -gt 0) {
    Write-Host "   Found $($chunks.Count) chunks for session" -ForegroundColor Green
    Write-Host "   First chunk preview: $($chunks[0].chunkText.Substring(0, [Math]::Min(50, $chunks[0].chunkText.Length)))..." -ForegroundColor Green
} else {
    Write-Host "   No chunks found for session" -ForegroundColor Yellow
}
Start-Sleep -Seconds 10
# Test 5: Vector similarity search within session
Write-Host "`n5. Testing vector similarity search within session..." -ForegroundColor Cyan
$searchRequest = @{
    query = "artificial intelligence machine learning"
    limit = 3
}
$searchResults = Invoke-TimelineApi -Method "POST" -Endpoint "/search/similar" -Body $searchRequest -QueryParams "sessionId=$SessionId"
if ($searchResults -and $searchResults.Count -gt 0) {
    Write-Host "   Found $($searchResults.Count) similar chunks" -ForegroundColor Green
    foreach ($result in $searchResults) {
        Write-Host "   Chunk: $($result.chunkText.Substring(0, [Math]::Min(40, $result.chunkText.Length)))..." -ForegroundColor Green
    }
} else {
    Write-Host "   No similar chunks found" -ForegroundColor Yellow
}

# Test 6: Global vector similarity search
Write-Host "`n6. Testing global vector similarity search..." -ForegroundColor Cyan
$globalSearchRequest = @{
    query = "artificial intelligence"
    limit = 5
}
$globalSearchResults = Invoke-TimelineApi -Method "POST" -Endpoint "/search/similar/global" -Body $globalSearchRequest
if ($globalSearchResults -and $globalSearchResults.Count -gt 0) {
    Write-Host "   Found $($globalSearchResults.Count) similar chunks globally" -ForegroundColor Green
} else {
    Write-Host "   No similar chunks found globally" -ForegroundColor Yellow
}

# Test 7: Threshold-based search
Write-Host "`n7. Testing threshold-based similarity search..." -ForegroundColor Cyan

# Test with a reasonable threshold based on the content
$thresholdRequest = @{
    query = "artificial intelligence"
    threshold = 0.6
}
$thresholdResults = Invoke-TimelineApi -Method "POST" -Endpoint "/search/threshold/$SessionId" -Body $thresholdRequest
if ($thresholdResults -and $thresholdResults.Count -gt 0) {
    Write-Host "   Found $($thresholdResults.Count) chunks within threshold 0.6" -ForegroundColor Green
} else {
    Write-Host "   No chunks found within threshold (this may be expected if similarity scores are below threshold)" -ForegroundColor Yellow
}

# Test 8: Debug chunks in session
Write-Host "`n8. Debugging chunks in session..." -ForegroundColor Cyan
$debugResponse = Invoke-TimelineApi -Endpoint "/debug/chunks/$SessionId"
if ($debugResponse) {
    Write-Host "   Session: $($debugResponse.sessionId)" -ForegroundColor Green
    Write-Host "   Total chunks: $($debugResponse.totalChunks)" -ForegroundColor Green

    if ($debugResponse.chunks -and $debugResponse.chunks.Count -gt 0) {
        Write-Host "   Sample chunks:" -ForegroundColor Cyan
        foreach ($chunk in $debugResponse.chunks | Select-Object -First 3) {
            Write-Host "     ID: $($chunk.id), Message: $($chunk.messageId), HasEmbedding: $($chunk.hasEmbedding), Dimensions: $($chunk.embeddingDimensions)" -ForegroundColor Gray
            Write-Host "     Text: $($chunk.chunkText)" -ForegroundColor Gray
        }
    }
} else {
    Write-Host "   Failed to get debug info" -ForegroundColor Red
}

# Test 9: Reprocess all messages in session
Write-Host "`n9. Reprocessing all messages in session..." -ForegroundColor Cyan
$reprocessResponse = Invoke-TimelineApi -Method "POST" -Endpoint "/vector/reprocess/$SessionId"
if ($reprocessResponse) {
    Write-Host "   Reprocessed messages: $($reprocessResponse.processedMessages)" -ForegroundColor Green
    Write-Host "   Total chunks created: $($reprocessResponse.totalChunks)" -ForegroundColor Green
    Write-Host "   Deleted chunks: $($reprocessResponse.deletedChunks)" -ForegroundColor Green
}

# Test 10: Retry similarity search after reprocessing
Write-Host "`n10. Retrying similarity search after reprocessing..." -ForegroundColor Cyan
$retrySearchResults = Invoke-TimelineApi -Method "POST" -Endpoint "/search/similar" -Body $searchRequest -QueryParams "sessionId=$SessionId"
if ($retrySearchResults -and $retrySearchResults.Count -gt 0) {
    Write-Host "   SUCCESS: Found $($retrySearchResults.Count) similar chunks after reprocessing!" -ForegroundColor Green
    foreach ($result in $retrySearchResults | Select-Object -First 2) {
        Write-Host "   Chunk: $($result.chunkText.Substring(0, [Math]::Min(60, $result.chunkText.Length)))..." -ForegroundColor Green
    }
} elseif ($retrySearchResults) {
    Write-Host "   Still no results found after reprocessing" -ForegroundColor Yellow
} else {
    Write-Host "   Search failed after reprocessing" -ForegroundColor Red
}

# Test 11: Manual vector processing
Write-Host "`n11. Testing manual vector processing..." -ForegroundColor Cyan
$processRequest = @{
    messageId = "manual-test-msg-123"
    messageText = "This is a manually processed message about natural language processing and vector databases."
    sessionId = "$SessionId-manual"
}
$processResponse = Invoke-TimelineApi -Method "POST" -Endpoint "/vector/process" -Body $processRequest
if ($processResponse) {
    Write-Host "   Manual processing result: $($processResponse.success)" -ForegroundColor Green
    Write-Host "   Chunks created: $($processResponse.chunksCreated)" -ForegroundColor Green
}

# Test 12: Test similarity search on manual session
Write-Host "`n12. Testing similarity search on manual session..." -ForegroundColor Cyan
$manualSearchRequest = @{
    query = "natural language processing"
    limit = 3
}
$manualSearchResults = Invoke-TimelineApi -Method "POST" -Endpoint "/search/similar" -Body $manualSearchRequest -QueryParams "sessionId=$SessionId-manual"
if ($manualSearchResults -and $manualSearchResults.Count -gt 0) {
    Write-Host "   SUCCESS: Found $($manualSearchResults.Count) similar chunks in manual session!" -ForegroundColor Green
    foreach ($result in $manualSearchResults | Select-Object -First 2) {
        Write-Host "   Chunk: $($result.chunkText.Substring(0, [Math]::Min(60, $result.chunkText.Length)))..." -ForegroundColor Green
    }
} elseif ($manualSearchResults) {
    Write-Host "   No results found in manual session" -ForegroundColor Yellow
} else {
    Write-Host "   Search failed in manual session" -ForegroundColor Red
}

Write-Host "`n=== Phase 4 Vector Endpoints Test Complete ===" -ForegroundColor Cyan
Write-Host "Vector search debugging complete. Check the logs for detailed information." -ForegroundColor Green
