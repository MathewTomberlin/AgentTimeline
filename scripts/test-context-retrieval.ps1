# Test Context Retrieval with Real Chat Data
param(
    [string]$SessionId = "test-context-session",
    [string]$UserMessage = "What did I say my name was?"
)

$baseUrl = "http://localhost:8080/api/v1"

# Step 1: Create some test messages to simulate a conversation
Write-Host "Step 1: Creating test conversation..." -ForegroundColor Green

$testMessages = @(
    @{
        message = "Hi, my name is Alibideeba and I live in New York City"
    },
    @{
        message = "Hello Alibideeba! Nice to meet you. How can I assist you today?"
    },
    @{
        message = "What did I say my name was?"
    }
)

foreach ($msg in $testMessages) {
    Write-Host "Sending: $($msg.message)" -ForegroundColor Gray
    $response = Invoke-RestMethod -Uri "$baseUrl/timeline/chat?sessionId=$SessionId" -Method POST -Body $msg -ContentType "application/json"
    Write-Host "Response: $($response.content)" -ForegroundColor Cyan
    Start-Sleep -Seconds 1
}

# Step 2: Test context retrieval
Write-Host "`nStep 2: Testing context retrieval..." -ForegroundColor Green

$contextEndpoint = "$baseUrl/timeline/debug/context/$SessionId"
$contextParams = @{
    userMessage = $UserMessage
} | ConvertTo-Json

Write-Host "Testing context retrieval for: $UserMessage" -ForegroundColor Yellow
Write-Host "Endpoint: $contextEndpoint" -ForegroundColor Cyan

try {
    $contextResponse = Invoke-RestMethod -Uri "$contextEndpoint`?userMessage=$([System.Web.HttpUtility]::UrlEncode($UserMessage))" -Method GET

    Write-Host "`nContext Retrieval Results:" -ForegroundColor Green
    Write-Host "Session ID: $($contextResponse.sessionId)" -ForegroundColor White
    Write-Host "User Message: $($contextResponse.userMessage)" -ForegroundColor White
    Write-Host "Enhanced Prompt Length: $($contextResponse.promptLength)" -ForegroundColor White

    Write-Host "`nExpanded Groups:" -ForegroundColor Yellow
    foreach ($group in $contextResponse.expandedGroups) {
        Write-Host "  Message ID: $($group.messageId)" -ForegroundColor Gray
        Write-Host "  Chunk Count: $($group.chunkCount)" -ForegroundColor Gray
        Write-Host "  Combined Text: $($group.combinedText)" -ForegroundColor White
        Write-Host "  Chunks:" -ForegroundColor Gray
        foreach ($chunk in $group.chunks) {
            Write-Host "    Index $($chunk.index): '$($chunk.text)' ($($chunk.textLength) chars)" -ForegroundColor DarkGray
        }
        Write-Host ""
    }

    Write-Host "Merged Groups:" -ForegroundColor Yellow
    foreach ($group in $contextResponse.mergedGroups) {
        Write-Host "  Message ID: $($group.messageId)" -ForegroundColor Gray
        Write-Host "  Total Chunks: $($group.totalChunks)" -ForegroundColor Gray
        Write-Host "  Combined Text: $($group.combinedText)" -ForegroundColor White
        Write-Host ""
    }

    Write-Host "Enhanced Prompt Preview:" -ForegroundColor Yellow
    Write-Host "$($contextResponse.enhancedPrompt)" -ForegroundColor White

} catch {
    Write-Host "Error in context retrieval: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Response: $($_.ErrorDetails.Message)" -ForegroundColor Red
}
