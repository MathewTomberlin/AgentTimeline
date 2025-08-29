param(
    [string]$SessionId = "default",
    [switch]$NoPause
)

# Configuration
$baseUrl = "http://localhost:8080/api/v1"
$chatEndpoint = "$baseUrl/timeline/chat"

Write-Host "====================================" -ForegroundColor Cyan
Write-Host "AgentTimeline - Chat Interface" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan
Write-Host ""

# Check if server is running
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/timeline/health" -Method GET -TimeoutSec 5
    if ($response.StatusCode -eq 200) {
        Write-Host "Server is running" -ForegroundColor Green
    }
} catch {
    Write-Host "Server is not running or not accessible" -ForegroundColor Red
    Write-Host "Please start the application first using start-app.ps1" -ForegroundColor Yellow
    Write-Host ""
    if (-not $NoPause) {
        Read-Host "Press Enter to exit"
    }
    exit 1
}

Write-Host "Session ID: $SessionId" -ForegroundColor Gray
Write-Host "Type 'quit' or 'exit' to end the conversation" -ForegroundColor Gray
Write-Host ""

# Main chat loop
while ($true) {
    # Get user input
    Write-Host "You: " -NoNewline -ForegroundColor Blue
    $userInput = Read-Host

    # Check for exit commands
    if ($userInput -eq "quit" -or $userInput -eq "exit") {
        Write-Host ""
        Write-Host "Goodbye!" -ForegroundColor Cyan
        break
    }

    # Skip empty input
    if ([string]::IsNullOrWhiteSpace($userInput)) {
        continue
    }

    try {
        # Prepare request
        $body = @{
            "message" = $userInput
        } | ConvertTo-Json

        # Send request to API
        $response = Invoke-RestMethod -Uri "$chatEndpoint`?sessionId=$SessionId" -Method POST -Body $body -ContentType "application/json" -TimeoutSec 30

        # Display assistant response
        if ($response -and $response.content) {
            Write-Host "Assistant: " -NoNewline -ForegroundColor Green
            Write-Host $response.content
        } else {
            Write-Host "Assistant: " -NoNewline -ForegroundColor Green
            Write-Host "(No response)" -ForegroundColor Gray
        }

    } catch {
        Write-Host "Error: " -NoNewline -ForegroundColor Red
        Write-Host $_.Exception.Message -ForegroundColor Red
        Write-Host ""
        Write-Host "Please check that the server is running and try again." -ForegroundColor Yellow
    }

    Write-Host ""
}

if (-not $NoPause) {
    Read-Host "Press Enter to exit"
}
