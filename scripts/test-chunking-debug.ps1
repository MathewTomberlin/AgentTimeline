# Test Chunking Debug Endpoint
param(
    [string]$Text = ("What did I say my name was? My name is Alibideeba and I live in New York City. " * 20) + "This should definitely exceed the chunk limit and force multiple chunks to be created."
)

$baseUrl = "http://localhost:8080/api/v1"
$endpoint = "$baseUrl/timeline/debug/chunking"

Write-Host "Testing chunking debug endpoint..." -ForegroundColor Green
Write-Host "Text: $Text" -ForegroundColor Yellow
Write-Host "Endpoint: $endpoint" -ForegroundColor Cyan

try {
    $body = @{
        text = $Text
    } | ConvertTo-Json

    Write-Host "Request Body: $body" -ForegroundColor Gray

    $response = Invoke-RestMethod -Uri $endpoint -Method POST -Body $body -ContentType "application/json"

    Write-Host "`nResponse:" -ForegroundColor Green
    Write-Host ($response | ConvertTo-Json -Depth 10) -ForegroundColor White

} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Response: $($_.ErrorDetails.Message)" -ForegroundColor Red
}
