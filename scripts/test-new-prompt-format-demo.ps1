# Demonstrate the new prompt format
param(
    [string]$SessionId = "demo-new-format-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
)

Write-Host "=== Demonstrating New Prompt Format ===" -ForegroundColor Cyan
Write-Host "Session ID: $SessionId" -ForegroundColor Yellow
Write-Host ""

$baseUrl = "http://localhost:8080/api/v1"

# Step 1: Tell the assistant something
$message1 = "I love programming with Java and Python"
Write-Host "Step 1: Sharing information..." -ForegroundColor Green
Write-Host "Message: $message1" -ForegroundColor Gray

$body1 = @{ message = $message1 } | ConvertTo-Json
$response1 = Invoke-WebRequest -Uri "$baseUrl/timeline/chat?sessionId=$SessionId&includePrompt=true" -Method POST -Body $body1 -ContentType "application/json"
$result1 = $response1.Content | ConvertFrom-Json
Write-Host "Assistant: $($result1.message.content)" -ForegroundColor Green

if ($result1.enhancedPrompt) {
    Write-Host ""
    Write-Host "--- PROMPT FOR STEP 1 ---" -ForegroundColor Magenta
    Write-Host $result1.enhancedPrompt -ForegroundColor Gray
    Write-Host "--- END PROMPT ---" -ForegroundColor Magenta
}

# Step 2: Ask a question that should retrieve context
$message2 = "What programming languages do I like?"
Write-Host ""
Write-Host "Step 2: Asking question that should use context..." -ForegroundColor Green
Write-Host "Message: $message2" -ForegroundColor Gray

$body2 = @{ message = $message2 } | ConvertTo-Json
$response2 = Invoke-WebRequest -Uri "$baseUrl/timeline/chat?sessionId=$SessionId&includePrompt=true" -Method POST -Body $body2 -ContentType "application/json"
$result2 = $response2.Content | ConvertFrom-Json
Write-Host "Assistant: $($result2.message.content)" -ForegroundColor Green

if ($result2.enhancedPrompt) {
    Write-Host ""
    Write-Host "--- PROMPT FOR STEP 2 ---" -ForegroundColor Magenta
    Write-Host $result2.enhancedPrompt -ForegroundColor Gray
    Write-Host "--- END PROMPT ---" -ForegroundColor Magenta
    Write-Host "Prompt Length: $($result2.promptLength) characters, $($result2.wordCount) words" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "=== Key Improvements ===" -ForegroundColor Cyan
Write-Host "1. ✅ Clean format: 'Past Conversation Context:' instead of verbose headers" -ForegroundColor Green
Write-Host "2. ✅ Role-based: 'User:' and 'Assistant:' prefixes for clarity" -ForegroundColor Green
Write-Host "3. ✅ No bullets: Removed â¢ symbols for cleaner text" -ForegroundColor Green
Write-Host "4. ✅ Clear instruction: 'Respond to this user message with the past conversation context:'" -ForegroundColor Green
Write-Host "5. ✅ Chronological order: Messages sorted by timestamp" -ForegroundColor Green
Write-Host ""
Write-Host "This format should reduce LLM hallucinations by being more structured and clear!" -ForegroundColor Yellow
