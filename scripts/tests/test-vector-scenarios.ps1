# Vector Search Scenarios Test
# Tests different types of content and scenarios for vector similarity search

param(
    [string]$BaseUrl = "http://localhost:8080/api/v1/timeline"
)

Write-Host "=== AgentTimeline Vector Search Scenarios Test ===" -ForegroundColor Cyan
Write-Host "Testing different content types and scenarios" -ForegroundColor Yellow
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

# Scenario 1: Technical Programming Content
Write-Host "`n--- SCENARIO 1: Technical Programming Content ---" -ForegroundColor Magenta
$techSession = "tech-programming-test"
$techMessage = @"
I'm working on a Java Spring Boot application with PostgreSQL database integration. I need to implement:

1. REST API endpoints for CRUD operations
2. Entity models with JPA annotations
3. Service layer with business logic
4. Repository layer with custom queries
5. Error handling and validation

Can you explain the best practices for structuring a Spring Boot application with proper separation of concerns? I'm particularly interested in dependency injection, transaction management, and testing strategies.
"@

$techRequest = @{ message = $techMessage }
$techResponse = Invoke-TimelineApi -Method "POST" -Endpoint "/chat" -Body $techRequest -QueryParams "sessionId=$techSession"

if ($techResponse) {
    Write-Host "[TECH] Initial message sent successfully" -ForegroundColor Green
    Start-Sleep -Seconds 3  # Wait for processing

    # Follow-up question
    $techFollowUp = "What are the best practices for Spring Boot testing and how should I structure my test classes?"
    $techFollowUpRequest = @{ message = $techFollowUp }
    $techFollowUpResponse = Invoke-TimelineApi -Method "POST" -Endpoint "/chat" -Body $techFollowUpRequest -QueryParams "sessionId=$techSession"

    if ($techFollowUpResponse) {
        Write-Host "[TECH] Follow-up question: '$techFollowUp'" -ForegroundColor Yellow
        $techTerms = @("Spring Boot", "PostgreSQL", "REST API", "JPA", "dependency injection", "testing")
        $foundTechTerms = @()
        foreach ($term in $techTerms) {
            if ($techFollowUpResponse.content -match $term) {
                $foundTechTerms += $term
            }
        }
        Write-Host "[TECH] Context terms found: $($foundTechTerms -join ', ')" -ForegroundColor Green
    }
}

# Scenario 2: Casual Conversation
Write-Host "`n--- SCENARIO 2: Casual Conversation ---" -ForegroundColor Magenta
$casualSession = "casual-conversation-test"
$casualMessage = @"
Hey, I'm planning a weekend trip to the mountains for hiking and camping. I need to prepare:

- What hiking gear should I bring?
- How do I choose a good campsite?
- What about weather considerations?
- Any safety tips for mountain hiking?

I've never really done much backpacking before, so I'm looking for beginner-friendly advice. My main concerns are staying safe, not getting lost, and making sure I have the right equipment.
"@

$casualRequest = @{ message = $casualMessage }
$casualResponse = Invoke-TimelineApi -Method "POST" -Endpoint "/chat" -Body $casualRequest -QueryParams "sessionId=$casualSession"

if ($casualResponse) {
    Write-Host "[CASUAL] Initial message sent successfully" -ForegroundColor Green
    Start-Sleep -Seconds 3

    # Follow-up question
    $casualFollowUp = "What should I do if I get lost while hiking?"
    $casualFollowUpRequest = @{ message = $casualFollowUp }
    $casualFollowUpResponse = Invoke-TimelineApi -Method "POST" -Endpoint "/chat" -Body $casualFollowUpRequest -QueryParams "sessionId=$casualSession"

    if ($casualFollowUpResponse) {
        Write-Host "[CASUAL] Follow-up question: '$casualFollowUp'" -ForegroundColor Yellow
        $casualTerms = @("hiking", "camping", "mountains", "backpacking", "safety", "equipment")
        $foundCasualTerms = @()
        foreach ($term in $casualTerms) {
            if ($casualFollowUpResponse.content -match $term) {
                $foundCasualTerms += $term
            }
        }
        Write-Host "[CASUAL] Context terms found: $($foundCasualTerms -join ', ')" -ForegroundColor Green
    }
}

# Scenario 3: Short vs Long Messages
Write-Host "`n--- SCENARIO 3: Message Length Comparison ---" -ForegroundColor Magenta

# Short message
$shortSession = "short-message-test"
$shortMessage = "Explain quantum computing in simple terms."
$shortRequest = @{ message = $shortMessage }
$shortResponse = Invoke-TimelineApi -Method "POST" -Endpoint "/chat" -Body $shortRequest -QueryParams "sessionId=$shortSession"

# Long message
$longSession = "long-message-test"
$longMessage = @"
I'm researching quantum computing for my thesis and need a comprehensive understanding. Can you explain:

1. What is quantum computing and how does it differ from classical computing?
2. What are qubits and how do they work?
3. What are the main quantum computing platforms (IBM, Google, etc.)?
4. What are some practical applications of quantum computing?
5. What are the current challenges and limitations?
6. How does quantum supremacy relate to quantum advantage?
7. What programming languages are used for quantum computing?
8. What are the prospects for quantum computing in the next 5-10 years?

I'm particularly interested in understanding the mathematical foundations, the current state of the technology, and potential real-world applications in fields like cryptography, drug discovery, and optimization problems.
"@

$longRequest = @{ message = $longMessage }
$longResponse = Invoke-TimelineApi -Method "POST" -Endpoint "/chat" -Body $longRequest -QueryParams "sessionId=$longSession"

Start-Sleep -Seconds 5  # Give time for processing

# Test both with follow-up questions
$followUpQuestion = "What are the main challenges in quantum computing today?"

# Short message follow-up
$shortFollowUpRequest = @{ message = $followUpQuestion }
$shortFollowUpResponse = Invoke-TimelineApi -Method "POST" -Endpoint "/chat" -Body $shortFollowUpRequest -QueryParams "sessionId=$shortSession"

if ($shortFollowUpResponse) {
    $shortTerms = @("quantum", "computing", "qubits", "classical")
    $foundShortTerms = @()
    foreach ($term in $shortTerms) {
        if ($shortFollowUpResponse.content -match $term) {
            $foundShortTerms += $term
        }
    }
    Write-Host "[SHORT MESSAGE] Context terms found: $($foundShortTerms -join ', ')" -ForegroundColor Green
}

# Long message follow-up
$longFollowUpRequest = @{ message = $followUpQuestion }
$longFollowUpResponse = Invoke-TimelineApi -Method "POST" -Endpoint "/chat" -Body $longFollowUpRequest -QueryParams "sessionId=$longSession"

if ($longFollowUpResponse) {
    $longTerms = @("quantum", "computing", "challenges", "limitations", "cryptography", "optimization")
    $foundLongTerms = @()
    foreach ($term in $longTerms) {
        if ($longFollowUpResponse.content -match $term) {
            $foundLongTerms += $term
        }
    }
    Write-Host "[LONG MESSAGE] Context terms found: $($foundLongTerms -join ', ')" -ForegroundColor Green
}

# Scenario 4: Multiple Sessions Isolation
Write-Host "`n--- SCENARIO 4: Session Isolation Test ---" -ForegroundColor Magenta

# Create two different sessions with different topics
$session1 = "science-session"
$session2 = "cooking-session"

$scienceMessage = "Explain the theory of relativity and its implications for modern physics."
$cookingMessage = "How do I make the perfect chocolate chip cookies?"

$scienceRequest = @{ message = $scienceMessage }
$cookingRequest = @{ message = $cookingMessage }

$scienceResponse = Invoke-TimelineApi -Method "POST" -Endpoint "/chat" -Body $scienceRequest -QueryParams "sessionId=$session1"
$cookingResponse = Invoke-TimelineApi -Method "POST" -Endpoint "/chat" -Body $cookingRequest -QueryParams "sessionId=$session2"

Start-Sleep -Seconds 3

# Test cross-session contamination
$scienceFollowUp = "What are some implications for space travel?"
$cookingFollowUp = "How long should I bake them?"

$scienceFollowUpRequest = @{ message = $scienceFollowUp }
$cookingFollowUpRequest = @{ message = $cookingFollowUp }

$scienceFollowUpResponse = Invoke-TimelineApi -Method "POST" -Endpoint "/chat" -Body $scienceFollowUpRequest -QueryParams "sessionId=$session1"
$cookingFollowUpResponse = Invoke-TimelineApi -Method "POST" -Endpoint "/chat" -Body $cookingFollowUpRequest -QueryParams "sessionId=$session2"

# Check for session isolation
$scienceHasCooking = $scienceFollowUpResponse.content -match "cookies|chocolate|baking"
$cookingHasScience = $cookingFollowUpResponse.content -match "relativity|physics|Einstein"

Write-Host "[SESSION ISOLATION]" -ForegroundColor Yellow
if (-not $scienceHasCooking -and -not $cookingHasScience) {
    Write-Host "  Sessions are properly isolated - no cross-contamination detected" -ForegroundColor Green
} else {
    Write-Host "  Warning: Possible session cross-contamination detected" -ForegroundColor Yellow
}

# Final Statistics
Write-Host "`n--- FINAL STATISTICS ---" -ForegroundColor Cyan
$finalStats = Invoke-TimelineApi -Endpoint "/vector/statistics"
if ($finalStats) {
    Write-Host "Total Chunks: $($finalStats.totalChunks)" -ForegroundColor Green
    Write-Host "Unique Messages: $($finalStats.uniqueMessages)" -ForegroundColor Green
    Write-Host "Unique Sessions: $($finalStats.uniqueSessions)" -ForegroundColor Green
}

Write-Host "`n=== Vector Search Scenarios Test Complete ===" -ForegroundColor Green
Write-Host "Tested: Technical content, casual conversation, message length comparison, session isolation" -ForegroundColor Cyan
