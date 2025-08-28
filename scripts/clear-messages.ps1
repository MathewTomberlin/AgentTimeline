# Simple AgentTimeline Clear Messages Script
Write-Host "AgentTimeline Clear Messages Script" -ForegroundColor Cyan
Write-Host "===================================" -ForegroundColor Cyan

# Count Redis keys before clearing
Write-Host "Counting Redis keys before clearing..." -ForegroundColor Yellow
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectPath = Split-Path -Parent $scriptPath
$redisCliPath = Join-Path $projectPath "redis\redis-cli.exe"
$redisKeysBefore = & $redisCliPath DBSIZE
Write-Host "Redis keys before clearing: $redisKeysBefore" -ForegroundColor White

# Clear Redis messages
Write-Host "Clearing Redis messages..." -ForegroundColor Yellow
& $redisCliPath FLUSHDB
$redisKeysAfter = & $redisCliPath DBSIZE
$redisDeleted = [int]$redisKeysBefore - [int]$redisKeysAfter
if ($LASTEXITCODE -eq 0) {
    Write-Host "SUCCESS: Redis messages cleared successfully" -ForegroundColor Green
    Write-Host "Redis keys deleted: $redisDeleted" -ForegroundColor Green
    Write-Host "Redis keys remaining: $redisKeysAfter" -ForegroundColor White
} else {
    Write-Host "ERROR: Failed to clear Redis messages" -ForegroundColor Red
}

# Count PostgreSQL chunks before clearing
Write-Host "Counting PostgreSQL chunks before clearing..." -ForegroundColor Yellow
$pgCountBefore = & docker exec agent-pg psql -U postgres -d agent_timeline -t -c "SELECT COUNT(*) FROM message_chunk_embeddings;" 2>$null
$pgCountBefore = [int](([string]$pgCountBefore).Trim())
Write-Host "PostgreSQL chunks before clearing: $pgCountBefore" -ForegroundColor White

# Clear PostgreSQL chunks and embeddings
Write-Host "Clearing PostgreSQL chunks and embeddings..." -ForegroundColor Yellow
& docker exec agent-pg psql -U postgres -d agent_timeline -c "DELETE FROM message_chunk_embeddings;"
$pgCountAfter = & docker exec agent-pg psql -U postgres -d agent_timeline -t -c "SELECT COUNT(*) FROM message_chunk_embeddings;" 2>$null
$pgCountAfter = [int](([string]$pgCountAfter).Trim())
$pgDeleted = [int]$pgCountBefore - [int]$pgCountAfter
if ($LASTEXITCODE -eq 0) {
    Write-Host "SUCCESS: PostgreSQL chunks and embeddings cleared successfully" -ForegroundColor Green
    Write-Host "PostgreSQL chunks deleted: $pgDeleted" -ForegroundColor Green
    Write-Host "PostgreSQL chunks remaining: $pgCountAfter" -ForegroundColor White
} else {
    Write-Host "ERROR: Failed to clear PostgreSQL data" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== CLEAR MESSAGES SUMMARY ===" -ForegroundColor Cyan
Write-Host "Redis messages deleted: $redisDeleted" -ForegroundColor Green
Write-Host "PostgreSQL chunks deleted: $pgDeleted" -ForegroundColor Green
Write-Host "Total items cleared: $([int]$redisDeleted + [int]$pgDeleted)" -ForegroundColor Green
Write-Host ""
Write-Host "Clear Messages Script completed!" -ForegroundColor Green