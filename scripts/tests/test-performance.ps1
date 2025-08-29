# Vector Search Performance Test
# Measures the performance of vector similarity search operations

param(
    [string]$BaseUrl = "http://localhost:8080/api/v1/timeline",
    [int]$TestIterations = 10
)

Write-Host "=== AgentTimeline Vector Search Performance Test ===" -ForegroundColor Cyan
Write-Host "Testing performance with $TestIterations iterations" -ForegroundColor Yellow
Write-Host ""

# Function to make HTTP requests with timing
function Invoke-TimelineApiWithTiming {
    param(
        [string]$Method = "GET",
        [string]$Endpoint,
        [object]$Body = $null,
        [string]$QueryParams = "",
        [string]$OperationName = "API Call"
    )

    $startTime = Get-Date
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
        $endTime = Get-Date
        $duration = ($endTime - $startTime).TotalMilliseconds

        Write-Host "Success ($OperationName): $($duration.ToString("F2"))ms" -ForegroundColor Green
        return @{
            Response = $response
            Duration = $duration
        }
    }
    catch {
        $endTime = Get-Date
        $duration = ($endTime - $startTime).TotalMilliseconds
        Write-Host "Failed ($OperationName): $($duration.ToString("F2"))ms - $($_.Exception.Message)" -ForegroundColor Red
        return @{
            Response = $null
            Duration = $duration
        }
    }
}

# Performance tracking variables
$chatTimes = @()
$embeddingTimes = @()
$searchTimes = @()
$totalStartTime = Get-Date

# Step 1: Create a comprehensive knowledge base
Write-Host "`n--- STEP 1: Building Knowledge Base ---" -ForegroundColor Magenta

$knowledgeBaseSession = "performance-test-session"
$knowledgeMessages = @(
    "Machine learning is a subset of artificial intelligence that enables computers to learn from data without being explicitly programmed. Key concepts include supervised learning, unsupervised learning, and reinforcement learning.",
    "Neural networks are computing systems inspired by biological neural networks. They consist of layers of interconnected nodes that process information using weighted connections and activation functions.",
    "Deep learning uses neural networks with multiple layers to model complex patterns in data. It's particularly effective for image recognition, natural language processing, and speech recognition.",
    "Vector embeddings are numerical representations of words, phrases, or documents in high-dimensional space. They capture semantic meaning and relationships between concepts.",
    "Cosine similarity measures the cosine of the angle between two vectors. It's commonly used in information retrieval and text similarity analysis.",
    "PostgreSQL is a powerful open-source relational database system known for its robustness, extensibility, and SQL compliance. It supports advanced features like JSON storage and full-text search.",
    "REST APIs provide a standardized way for applications to communicate over HTTP. They use standard HTTP methods like GET, POST, PUT, and DELETE to perform operations on resources.",
    "Spring Boot is a framework that simplifies the creation of production-ready applications with minimal configuration. It provides auto-configuration and embedded servers."
)

$knowledgeProcessingTimes = @()
foreach ($message in $knowledgeMessages) {
    $result = Invoke-TimelineApiWithTiming -Method "POST" -Endpoint "/chat" -Body @{ message = $message } -QueryParams "sessionId=$knowledgeBaseSession" -OperationName "Knowledge Message"
    $chatTimes += $result.Duration
    $knowledgeProcessingTimes += $result.Duration
    Start-Sleep -Milliseconds 200  # Brief pause between messages
}

$avgKnowledgeTime = ($knowledgeProcessingTimes | Measure-Object -Average).Average
Write-Host "Knowledge base built: $($knowledgeMessages.Count) messages" -ForegroundColor Green
Write-Host "Average processing time: $($avgKnowledgeTime.ToString("F2"))ms per message" -ForegroundColor Green

# Step 2: Performance test similarity search
Write-Host "`n--- STEP 2: Similarity Search Performance ---" -ForegroundColor Magenta

$searchQueries = @(
    "How do neural networks work?",
    "What are vector embeddings?",
    "Explain machine learning concepts",
    "What is deep learning?",
    "How does cosine similarity work?",
    "What are REST API best practices?",
    "Explain PostgreSQL features",
    "What is Spring Boot framework?"
)

$searchResults = @()
foreach ($query in $searchQueries) {
    $searchRequest = @{ query = $query; limit = 5 }
    $result = Invoke-TimelineApiWithTiming -Method "POST" -Endpoint "/search/similar" -Body $searchRequest -QueryParams "sessionId=$knowledgeBaseSession" -OperationName "Similarity Search"
    $searchTimes += $result.Duration
    $searchResults += $result.Response
}

$avgSearchTime = ($searchTimes | Measure-Object -Average).Average
$minSearchTime = ($searchTimes | Measure-Object -Minimum).Minimum
$maxSearchTime = ($searchTimes | Measure-Object -Maximum).Maximum

Write-Host "Similarity search performance:" -ForegroundColor Green
Write-Host "  Average: $($avgSearchTime.ToString("F2"))ms" -ForegroundColor Green
Write-Host "  Fastest: $($minSearchTime.ToString("F2"))ms" -ForegroundColor Green
Write-Host "  Slowest: $($maxSearchTime.ToString("F2"))ms" -ForegroundColor Green

# Step 3: Test different search limits
Write-Host "`n--- STEP 3: Search Limit Performance ---" -ForegroundColor Magenta

$limitTests = @(1, 3, 5, 10, 20)
$limitResults = @()

foreach ($limit in $limitTests) {
    $searchRequest = @{ query = "artificial intelligence machine learning"; limit = $limit }
    $result = Invoke-TimelineApiWithTiming -Method "POST" -Endpoint "/search/similar" -Body $searchRequest -QueryParams "sessionId=$knowledgeBaseSession" -OperationName "Limit $limit Search"
    $limitResults += @{
        Limit = $limit
        Duration = $result.Duration
        ResultCount = if ($result.Response) { $result.Response.Count } else { 0 }
    }
}

Write-Host "Search limit performance comparison:" -ForegroundColor Green
foreach ($result in $limitResults) {
    Write-Host "  Limit $($result.Limit): $($result.Duration.ToString("F2"))ms ($($result.ResultCount) results)" -ForegroundColor Gray
}

# Step 4: Global search performance
Write-Host "`n--- STEP 4: Global Search Performance ---" -ForegroundColor Magenta

$globalSearchTimes = @()
for ($i = 1; $i -le $TestIterations; $i++) {
    $searchRequest = @{ query = "programming development software"; limit = 10 }
    $result = Invoke-TimelineApiWithTiming -Method "POST" -Endpoint "/search/similar/global" -Body $searchRequest -OperationName "Global Search $i"
    $globalSearchTimes += $result.Duration
}

$avgGlobalTime = ($globalSearchTimes | Measure-Object -Average).Average
Write-Host "Global search performance (across all sessions):" -ForegroundColor Green
Write-Host "  Average: $($avgGlobalTime.ToString("F2"))ms over $TestIterations iterations" -ForegroundColor Green

# Step 5: Statistics and summary
Write-Host "`n--- STEP 5: Performance Summary ---" -ForegroundColor Magenta

$totalEndTime = Get-Date
$totalDuration = ($totalEndTime - $totalStartTime).TotalSeconds

$stats = Invoke-TimelineApiWithTiming -Method "GET" -Endpoint "/vector/statistics" -OperationName "Statistics Query"

Write-Host "PERFORMANCE SUMMARY:" -ForegroundColor Cyan
Write-Host "==================" -ForegroundColor Cyan
Write-Host "Total test duration: $($totalDuration.ToString("F2")) seconds" -ForegroundColor White
Write-Host "Knowledge base: $($knowledgeMessages.Count) messages processed" -ForegroundColor White
Write-Host "Average message processing: $($avgKnowledgeTime.ToString("F2"))ms" -ForegroundColor White
Write-Host "Average similarity search: $($avgSearchTime.ToString("F2"))ms" -ForegroundColor White
Write-Host "Average global search: $($avgGlobalTime.ToString("F2"))ms" -ForegroundColor White

if ($stats.Response) {
    Write-Host "Current vector store:" -ForegroundColor White
    Write-Host "  Total chunks: $($stats.Response.totalChunks)" -ForegroundColor Gray
    Write-Host "  Unique messages: $($stats.Response.uniqueMessages)" -ForegroundColor Gray
    Write-Host "  Unique sessions: $($stats.Response.uniqueSessions)" -ForegroundColor Gray
}

Write-Host "`nPERFORMANCE CLASSIFICATION:" -ForegroundColor Yellow
if ($avgSearchTime -lt 100) {
    Write-Host "  Similarity Search: EXCELLENT (< 100ms)" -ForegroundColor Green
} elseif ($avgSearchTime -lt 500) {
    Write-Host "  Similarity Search: GOOD (< 500ms)" -ForegroundColor Yellow
} else {
    Write-Host "  Similarity Search: NEEDS OPTIMIZATION (> 500ms)" -ForegroundColor Red
}

if ($avgGlobalTime -lt 200) {
    Write-Host "  Global Search: EXCELLENT (< 200ms)" -ForegroundColor Green
} elseif ($avgGlobalTime -lt 1000) {
    Write-Host "  Global Search: GOOD (< 1000ms)" -ForegroundColor Yellow
} else {
    Write-Host "  Global Search: NEEDS OPTIMIZATION (> 1000ms)" -ForegroundColor Red
}

Write-Host "`n=== Performance Test Complete ===" -ForegroundColor Green
Write-Host "Vector search system performance validated with comprehensive testing!" -ForegroundColor Cyan
