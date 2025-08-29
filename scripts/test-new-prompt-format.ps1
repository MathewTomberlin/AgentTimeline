# Test the new prompt format
param(
    [string]$SessionId = "test-new-format-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
)

Write-Host "=== Testing New Prompt Format ===" -ForegroundColor Cyan
Write-Host "Session ID: $SessionId" -ForegroundColor Yellow
Write-Host ""

$baseUrl = "http://localhost:8080/api/v1"

# Step 1: Send first message
$message1 = "Hi, my name is Alice and I live in Boston"
Write-Host "Step 1: $message1" -ForegroundColor Green

$body1 = @{ message = $message1 } | ConvertTo-Json
$response1 = Invoke-WebRequest -Uri "$baseUrl/timeline/chat?sessionId=$SessionId&includePrompt=true" -Method POST -Body $body1 -ContentType "application/json"
$result1 = $response1.Content | ConvertFrom-Json
Write-Host "Assistant: $($result1.message.content)" -ForegroundColor Green

if ($result1.enhancedPrompt) {
    Write-Host ""
    Write-Host "--- PROMPT SENT TO LLM ---" -ForegroundColor Magenta
    Write-Host $result1.enhancedPrompt -ForegroundColor Gray
    Write-Host "--- END PROMPT ---" -ForegroundColor Magenta
}

# Step 2: Send second message to trigger context retrieval
$message2 = "What's my name?"
Write-Host ""
Write-Host "Step 2: $message2" -ForegroundColor Green

$body2 = @{ message = $message2 } | ConvertTo-Json
$response2 = Invoke-WebRequest -Uri "$baseUrl/timeline/chat?sessionId=$SessionId&includePrompt=true" -Method POST -Body $body2 -ContentType "application/json"
$result2 = $response2.Content | ConvertFrom-Json
Write-Host "Assistant: $($result2.message.content)" -ForegroundColor Green

if ($result2.enhancedPrompt) {
    Write-Host ""
    Write-Host "--- PROMPT SENT TO LLM ---" -ForegroundColor Magenta
    Write-Host $result2.enhancedPrompt -ForegroundColor Gray
    Write-Host "--- END PROMPT ---" -ForegroundColor Magenta
    Write-Host "Prompt Length: $($result2.promptLength) characters, $($result2.wordCount) words" -ForegroundColor DarkGray
} else {
    Write-Host "No enhanced prompt found (no context retrieved)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Summary ===" -ForegroundColor Cyan
Write-Host "The new prompt format should show:" -ForegroundColor White
Write-Host "1. 'Past Conversation Context:' header" -ForegroundColor White
Write-Host "2. 'User: [message]' format for user messages" -ForegroundColor White
Write-Host "3. 'Assistant: [message]' format for assistant messages" -ForegroundColor White
Write-Host "4. 'Respond to this user message with the past conversation context:' for the current query" -ForegroundColor White
