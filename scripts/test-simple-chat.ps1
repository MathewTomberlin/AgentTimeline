# Simple Chat Test
param(
    [string]$SessionId = "test-phase5-session",
    [string]$Message = "Hi, my name is Alibideeba"
)

$baseUrl = "http://localhost:8080/api/v1"

Write-Host "Testing chat endpoint..." -ForegroundColor Green
Write-Host "Session: $SessionId" -ForegroundColor Yellow
Write-Host "Message: $Message" -ForegroundColor Yellow

try {
    $body = @{
        message = $Message
    } | ConvertTo-Json

    Write-Host "Request Body: $body" -ForegroundColor Gray

    $response = Invoke-WebRequest -Uri "$baseUrl/timeline/chat?sessionId=$SessionId" -Method POST -Body $body -ContentType "application/json"

    Write-Host "Status Code: $($response.StatusCode)" -ForegroundColor Green

    $responseData = $response.Content | ConvertFrom-Json
    Write-Host "Response:" -ForegroundColor Green
    Write-Host "ID: $($responseData.id)" -ForegroundColor White
    Write-Host "Content: $($responseData.content)" -ForegroundColor White
    Write-Host "Role: $($responseData.role)" -ForegroundColor White
    Write-Host "Session: $($responseData.sessionId)" -ForegroundColor White

} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ErrorDetails.Message) {
        Write-Host "Response: $($_.ErrorDetails.Message)" -ForegroundColor Red
    }
}
