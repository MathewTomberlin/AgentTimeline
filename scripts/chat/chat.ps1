param(
    [string]$SessionId = "default",
    [switch]$NoPause,
    [switch]$ShowPrompt
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
if ($ShowPrompt) {
    Write-Host "Show LLM Prompt: Enabled" -ForegroundColor Yellow
}
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

        # Build query parameters
        $queryParams = "sessionId=$SessionId"
        if ($ShowPrompt) {
            $queryParams += "&includePrompt=true"
        }

        # Send request to API
        $response = Invoke-RestMethod -Uri "$chatEndpoint`?$queryParams" -Method POST -Body $body -ContentType "application/json" -TimeoutSec 30

        # Display assistant response
        if ($response) {
            if ($response.content) {
                # Standard response format (no prompt requested)
                Write-Host "Assistant: " -NoNewline -ForegroundColor Green
                Write-Host $response.content
            } elseif ($response.message -and $response.message.content) {
                # Debug response format with prompt (includePrompt=true)
                Write-Host "Assistant: " -NoNewline -ForegroundColor Green
                Write-Host $response.message.content

                if ($ShowPrompt -and $response.enhancedPrompt -and $response.enhancedPrompt.Length -gt 0) {
                    Write-Host ""
                    Write-Host "--- LLM PROMPT SENT ---" -ForegroundColor Magenta
                    Write-Host $response.enhancedPrompt -ForegroundColor Gray
                    Write-Host "--- END PROMPT ---" -ForegroundColor Magenta
                    Write-Host "Prompt Length: $($response.promptLength) characters, $($response.wordCount) words" -ForegroundColor DarkGray
                } elseif ($ShowPrompt) {
                    Write-Host ""
                    Write-Host "--- No Context Retrieved ---" -ForegroundColor DarkGray
                }
            } else {
                Write-Host "Assistant: " -NoNewline -ForegroundColor Green
                Write-Host "(No response)" -ForegroundColor Gray
            }
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
