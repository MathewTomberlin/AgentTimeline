# AgentTimeline Clear Messages Script with Phase 6 Support
# Supports clearing Redis, PostgreSQL, and Phase 6 memory caches
param(
    [switch]$ClearPostgres,
    [switch]$ClearPhase6,
    [switch]$ClearAll,
    [switch]$Force,
    [switch]$Quiet,
    [switch]$ShowKeys,
    [string]$AppUrl = "http://localhost:8080"
)

Write-Host "AgentTimeline Clear Messages Script" -ForegroundColor Cyan
Write-Host "===================================" -ForegroundColor Cyan
Write-Host "Phase 6 Enhanced Context Management Support" -ForegroundColor Yellow
Write-Host ""

# Function to clear Phase 6 memory caches
function Clear-Phase6Memory {
    param([string]$AppUrl, [bool]$Quiet = $false)

    if (!$Quiet) {
        Write-Host "Clearing Phase 6 memory caches..." -ForegroundColor Yellow
    }

    try {
        $clearUrl = "$AppUrl/api/v1/timeline/phase6/clear?force=true"
        $response = Invoke-RestMethod -Uri $clearUrl -Method DELETE -ContentType "application/json"

        if ($response.status -eq "partial_clear" -or $response.status -eq "clear_completed") {
            if (!$Quiet) {
                Write-Host "SUCCESS: Phase 6 memory caches cleared" -ForegroundColor Green

                # Show details of what was cleared
                if ($response.extractionCache) {
                    $entriesCleared = $response.extractionCache.entriesCleared
                    Write-Host "  - Extraction cache: $entriesCleared entries cleared" -ForegroundColor Green
                }

                if ($response.conversationHistory) {
                    $historyStatus = $response.conversationHistory.sessionsCleared
                    Write-Host "  - Conversation history: $historyStatus" -ForegroundColor Green
                }
            }
            return @{ Success = $true; Response = $response }
        } else {
            if (!$Quiet) {
                Write-Host "WARNING: Phase 6 clear returned status: $($response.status)" -ForegroundColor Yellow
            }
            return @{ Success = $true; Response = $response; Warning = $true }
        }
    } catch {
        if (!$Quiet) {
            Write-Host "ERROR: Failed to clear Phase 6 memory caches" -ForegroundColor Red
            Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
            Write-Host "  Make sure the application is running at $AppUrl" -ForegroundColor Yellow
        }
        return @{ Success = $false; Error = $_.Exception.Message }
    }
}

# Function to test application connectivity
function Test-ApplicationConnectivity {
    param([string]$AppUrl, [bool]$Quiet = $false)

    try {
        $healthUrl = "$AppUrl/api/v1/timeline/health"
        $response = Invoke-WebRequest -Uri $healthUrl -Method GET -TimeoutSec 5
        return $true
    } catch {
        if (!$Quiet) {
            Write-Host "WARNING: Application not accessible at $AppUrl" -ForegroundColor Yellow
            Write-Host "  Phase 6 memory caches will not be cleared" -ForegroundColor Yellow
        }
        return $false
    }
}

# Initialize variables
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectPath = Split-Path -Parent $scriptPath
$redisCliPath = Join-Path $projectPath "redis\redis-cli.exe"

$redisDeleted = 0
$pgDeleted = 0
$phase6Cleared = $false
$totalCleared = 0

# Handle ClearAll parameter
if ($ClearAll) {
    $ClearPostgres = $true
    $ClearPhase6 = $true
}

# If no specific parameters provided and we're running interactively, clear Phase 6 by default
# This ensures that when users run the script without parameters, they get a complete cleanup
if (!$ClearPostgres -and !$ClearPhase6 -and !$ClearAll -and !$ShowKeys -and !$Force -and !$Quiet) {
    Write-Host "No cleanup parameters specified. Defaulting to clear Phase 6 memory caches as well." -ForegroundColor Yellow
    Write-Host "Use -ShowKeys for debug mode or specific parameters for targeted cleanup." -ForegroundColor Yellow
    Write-Host ""
    $ClearPhase6 = $true
}

# Handle ShowKeys mode
if ($ShowKeys) {
    Write-Host "=== SHOWING ALL REDIS KEYS (DEBUG MODE) ===" -ForegroundColor Cyan
    Write-Host "Redis keys:" -ForegroundColor Yellow
    & $redisCliPath KEYS "*"
    Write-Host ""
    Write-Host "PostgreSQL tables:" -ForegroundColor Yellow
    & docker exec agent-pg psql -U postgres -d agent_timeline -c "\dt" 2>$null
    Write-Host ""
    Write-Host "PostgreSQL message_chunk_embeddings count:" -ForegroundColor Yellow
    & docker exec agent-pg psql -U postgres -d agent_timeline -t -c "SELECT COUNT(*) FROM message_chunk_embeddings;" 2>$null
    exit 0
}

# Test application connectivity for Phase 6 operations
$appAvailable = Test-ApplicationConnectivity -AppUrl $AppUrl -Quiet $Quiet

# Clear Phase 6 memory caches first (if requested and app is available)
if ($ClearPhase6 -and $appAvailable) {
    $phase6Result = Clear-Phase6Memory -AppUrl $AppUrl -Quiet $Quiet
    if ($phase6Result.Success) {
        $phase6Cleared = $true
        if (!$Quiet) {
            Write-Host "Phase 6 memory caches cleared successfully" -ForegroundColor Green
        }
    }
} elseif ($ClearPhase6 -and !$appAvailable) {
    if (!$Quiet) {
        Write-Host "WARNING: Cannot clear Phase 6 memory caches - application not running" -ForegroundColor Yellow
    }
}

# Count and clear Redis keys
if (!$Quiet) {
    Write-Host "Counting Redis keys before clearing..." -ForegroundColor Yellow
}
$redisKeysBefore = & $redisCliPath DBSIZE 2>$null
$redisKeysBefore = [int](([string]$redisKeysBefore).Trim())
if (!$Quiet) {
    Write-Host "Redis keys before clearing: $redisKeysBefore" -ForegroundColor White
}

if (!$Quiet) {
    Write-Host "Clearing Redis messages..." -ForegroundColor Yellow
}
& $redisCliPath FLUSHDB
$redisKeysAfter = & $redisCliPath DBSIZE 2>$null
$redisKeysAfter = [int](([string]$redisKeysAfter).Trim())
$redisDeleted = [int]$redisKeysBefore - [int]$redisKeysAfter

if ($LASTEXITCODE -eq 0) {
    if (!$Quiet) {
        Write-Host "SUCCESS: Redis messages cleared successfully" -ForegroundColor Green
        Write-Host "Redis keys deleted: $redisDeleted" -ForegroundColor Green
        Write-Host "Redis keys remaining: $redisKeysAfter" -ForegroundColor White
    }
} else {
    if (!$Quiet) {
        Write-Host "ERROR: Failed to clear Redis messages" -ForegroundColor Red
    }
}

# Clear PostgreSQL data if requested
if ($ClearPostgres) {
    if (!$Quiet) {
        Write-Host "Counting PostgreSQL chunks before clearing..." -ForegroundColor Yellow
    }
    $pgCountBefore = & docker exec agent-pg psql -U postgres -d agent_timeline -t -c "SELECT COUNT(*) FROM message_chunk_embeddings;" 2>$null
    $pgCountBefore = [int](([string]$pgCountBefore).Trim())
    if (!$Quiet) {
        Write-Host "PostgreSQL chunks before clearing: $pgCountBefore" -ForegroundColor White
    }

    if (!$Quiet) {
        Write-Host "Clearing PostgreSQL chunks and embeddings..." -ForegroundColor Yellow
    }
    & docker exec agent-pg psql -U postgres -d agent_timeline -c "DELETE FROM message_chunk_embeddings;" 2>$null
    $pgCountAfter = & docker exec agent-pg psql -U postgres -d agent_timeline -t -c "SELECT COUNT(*) FROM message_chunk_embeddings;" 2>$null
    $pgCountAfter = [int](([string]$pgCountAfter).Trim())
    $pgDeleted = [int]$pgCountBefore - [int]$pgCountAfter

    if ($LASTEXITCODE -eq 0) {
        if (!$Quiet) {
            Write-Host "SUCCESS: PostgreSQL chunks and embeddings cleared successfully" -ForegroundColor Green
            Write-Host "PostgreSQL chunks deleted: $pgDeleted" -ForegroundColor Green
            Write-Host "PostgreSQL chunks remaining: $pgCountAfter" -ForegroundColor White
        }
    } else {
        if (!$Quiet) {
            Write-Host "ERROR: Failed to clear PostgreSQL data" -ForegroundColor Red
        }
    }
}

# Calculate totals
$totalCleared = [int]$redisDeleted + [int]$pgDeleted

# Display summary
if (!$Quiet) {
    Write-Host ""
    Write-Host "=== CLEAR MESSAGES SUMMARY ===" -ForegroundColor Cyan
    Write-Host "Redis keys deleted: $redisDeleted" -ForegroundColor Green
    if ($ClearPostgres) {
        Write-Host "PostgreSQL chunks deleted: $pgDeleted" -ForegroundColor Green
    }
    Write-Host "Total persistent data cleared: $totalCleared" -ForegroundColor Green

    if ($phase6Cleared) {
        Write-Host "Phase 6 memory caches: CLEARED" -ForegroundColor Green
    } elseif ($ClearPhase6) {
        Write-Host "Phase 6 memory caches: NOT CLEARED (application unavailable)" -ForegroundColor Yellow
    }

    Write-Host ""
    Write-Host "Clear Messages Script completed!" -ForegroundColor Green

    # Show usage examples if no parameters were provided (interactive mode)
    if (!$ClearPostgres -and !$ClearPhase6 -and !$ClearAll -and !$Force -and !$Quiet -and !$ShowKeys) {
        Write-Host ""
        Write-Host "=== USAGE EXAMPLES ===" -ForegroundColor Cyan
        Write-Host ".\clear-messages.ps1                           # Clear Redis only" -ForegroundColor White
        Write-Host ".\clear-messages.ps1 -ClearPostgres            # Clear Redis + PostgreSQL" -ForegroundColor White
        Write-Host ".\clear-messages.ps1 -ClearPhase6              # Clear Redis + Phase 6 memory" -ForegroundColor White
        Write-Host ".\clear-messages.ps1 -ClearAll                 # Clear everything" -ForegroundColor White
        Write-Host ".\clear-messages.ps1 -ShowKeys                 # Show all keys (no deletion)" -ForegroundColor White
        Write-Host ".\clear-messages.ps1 -Force -Quiet             # Silent complete cleanup" -ForegroundColor White
    }
}