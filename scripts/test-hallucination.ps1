# Test script to demonstrate the hallucination issue
param(
    [string]$SessionId = "test-hallucination-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
)

Write-Host "=== Testing Hallucination Issue ===" -ForegroundColor Cyan
Write-Host "Session ID: $SessionId" -ForegroundColor Yellow
Write-Host ""

$baseUrl = "http://localhost:8080/api/v1"
$chatEndpoint = "$baseUrl/timeline/chat"

# Step 1: Send the initial message about the band
$message1 = "I used to play in a rock band but now I play cow bell"
Write-Host "Step 1: Sending message about the band..." -ForegroundColor Green
Write-Host "Message: $message1" -ForegroundColor Gray

$body1 = @{ message = $message1 } | ConvertTo-Json
$response1 = Invoke-RestMethod -Uri "$chatEndpoint`?sessionId=$SessionId&includePrompt=true" -Method POST -Body $body1 -ContentType "application/json"
Write-Host "Assistant: $($response1.message.content)" -ForegroundColor Green

if ($response1.enhancedPrompt) {
    Write-Host ""
    Write-Host "--- PROMPT FOR MESSAGE 1 ---" -ForegroundColor Magenta
    Write-Host $response1.enhancedPrompt -ForegroundColor Gray
    Write-Host "--- END PROMPT ---" -ForegroundColor Magenta
}

Start-Sleep -Seconds 2

# Step 2: Ask about the band
$message2 = "What kind of band did I play in?"
Write-Host ""
Write-Host "Step 2: Asking about the band..." -ForegroundColor Green
Write-Host "Message: $message2" -ForegroundColor Gray

$body2 = @{ message = $message2 } | ConvertTo-Json
$response2 = Invoke-RestMethod -Uri "$chatEndpoint`?sessionId=$SessionId&includePrompt=true" -Method POST -Body $body2 -ContentType "application/json"
Write-Host "Assistant: $($response2.message.content)" -ForegroundColor Green

if ($response2.enhancedPrompt) {
    Write-Host ""
    Write-Host "--- PROMPT FOR MESSAGE 2 ---" -ForegroundColor Magenta
    Write-Host $response2.enhancedPrompt -ForegroundColor Gray
    Write-Host "--- END PROMPT ---" -ForegroundColor Magenta
    Write-Host "Prompt Length: $($response2.promptLength) characters, $($response2.wordCount) words" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "=== Analysis ===" -ForegroundColor Cyan
Write-Host "Check the prompts above to see:"
Write-Host "1. What context was retrieved for the second question"
Write-Host "2. How the context is formatted in the prompt"
Write-Host "3. Whether the LLM is properly instructed to use only provided context"
Write-Host ""
Write-Host "If hallucination occurs, the prompt format may need adjustment." -ForegroundColor Yellow
