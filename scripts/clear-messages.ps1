param(
    [switch]$Quiet,
    [switch]$NoPause,
    [string]$RedisHost = "localhost",
    [int]$RedisPort = 6379,
    [switch]$Force,
    [switch]$ShowKeys,
    [string]$RedisCliPath = $null,
    [switch]$ClearPostgres,
    [string]$PostgresHost = "localhost",
    [int]$PostgresPort = 5432,
    [string]$PostgresDatabase = "agent_timeline",
    [string]$PostgresUser = "postgres",
    [string]$PostgresPassword = "postgres"
)

if (-not $Quiet) {
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host "AgentTimeline - Clear Messages" -ForegroundColor Cyan
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host "Clearing stored message CONTENT from Redis..." -ForegroundColor Yellow
    Write-Host "Supports both Phase 1 (timeline_message*) and Phase 2 (message*) formats" -ForegroundColor Gray
    Write-Host "NOTE: Only deletes actual message data, preserves Redis indexes" -ForegroundColor Gray

    if ($ClearPostgres) {
        Write-Host "Also clearing PostgreSQL data (embedding chunks)..." -ForegroundColor Yellow
    }
    Write-Host ""
}

# Check if Redis is accessible
try {
    $redisConnection = Get-NetTCPConnection -LocalPort $RedisPort -ErrorAction SilentlyContinue | Where-Object { $_.State -eq "Listen" }
    if (-not $redisConnection) {
        throw "Redis server not found on port $RedisPort"
    }
} catch {
    if (-not $Quiet) {
        Write-Host "[ERROR] Cannot connect to Redis server on $RedisHost`:$RedisPort" -ForegroundColor Red
        Write-Host "Make sure Redis is running: .\redis\redis-server.exe --port 6379" -ForegroundColor Yellow
    } else {
        Write-Host "[ERROR] Redis connection failed" -ForegroundColor Red
    }
    exit 1
}

# Check if PostgreSQL is accessible (if needed)
if ($ClearPostgres) {
    try {
        $postgresConnection = Get-NetTCPConnection -LocalPort $PostgresPort -ErrorAction SilentlyContinue | Where-Object { $_.State -eq "Listen" }
        if (-not $postgresConnection) {
            throw "PostgreSQL server not found on port $PostgresPort"
        }

        # Test database connection using docker exec if it's the default container
        if ($PostgresHost -eq "localhost" -or $PostgresHost -eq "127.0.0.1") {
            try {
                $testConnection = docker exec agent-pg psql -U $PostgresUser -d $PostgresDatabase -c "SELECT 1;" 2>$null
                if ($LASTEXITCODE -ne 0) {
                    throw "Cannot connect to PostgreSQL database"
                }
            } catch {
                if (-not $Quiet) {
                    Write-Host "[WARNING] PostgreSQL container 'agent-pg' not accessible, trying direct connection..." -ForegroundColor Yellow
                }
            }
        }
    } catch {
        if (-not $Quiet) {
            Write-Host "[ERROR] Cannot connect to PostgreSQL server on $PostgresHost`:$PostgresPort" -ForegroundColor Red
            Write-Host "Make sure PostgreSQL is running or use -PostgresHost and -PostgresPort to specify connection" -ForegroundColor Yellow
        } else {
            Write-Host "[ERROR] PostgreSQL connection failed" -ForegroundColor Red
        }
        exit 1
    }
}

# Find redis-cli executable
$redisCliPath = $null

# Get the script's directory to ensure we look in the right place
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = Split-Path -Parent $scriptDir

# If user provided a specific path, use it
if ($RedisCliPath) {
    if (Test-Path $RedisCliPath) {
        $redisCliPath = $RedisCliPath
    } else {
        if (-not $Quiet) {
            Write-Host "[ERROR] Specified redis-cli.exe path not found: $RedisCliPath" -ForegroundColor Red
        } else {
            Write-Host "[ERROR] Specified redis-cli.exe path not found" -ForegroundColor Red
        }
        exit 1
    }
} else {
    # Search for redis-cli.exe in common locations, prioritizing project directory
    $possiblePaths = @(
        "$projectDir\redis\redis-cli.exe",
        "$scriptDir\..\redis\redis-cli.exe",
        ".\redis\redis-cli.exe",
        "redis-cli.exe",
        "$env:ProgramFiles\Redis\redis-cli.exe",
        "$env:ProgramFiles(x86)\Redis\redis-cli.exe",
        "$env:ProgramW6432\Redis\redis-cli.exe",
        "$env:USERPROFILE\redis\redis-cli.exe",
        "$env:USERPROFILE\AppData\Local\Microsoft\WindowsApps\redis-cli.exe",
        "$env:USERPROFILE\AppData\Roaming\Microsoft\Windows\Start Menu\Programs\Redis\redis-cli.exe",
        "$env:ChocolateyInstall\bin\redis-cli.exe",
        "$env:ChocolateyInstall\lib\redis\tools\redis-cli.exe",
        "$env:SCOOP\apps\redis\current\redis-cli.exe",
        "$env:SCOOP\apps\redis-cli\current\redis-cli.exe",
        "$env:LOCALAPPDATA\Microsoft\WindowsApps\redis-cli.exe"
    )

    foreach ($path in $possiblePaths) {
        if (Test-Path $path) {
            $redisCliPath = $path
            break
        }
    }

    # If not found in common locations, try to find it in PATH
    if (-not $redisCliPath) {
        try {
            $redisCliPath = (Get-Command redis-cli.exe -ErrorAction Stop).Source
        } catch {
            # Try redis-cli (without .exe) for some installations
            try {
                $redisCliPath = (Get-Command redis-cli -ErrorAction Stop).Source
            } catch {
                # Continue to error message
            }
        }
    }
}

if (-not $redisCliPath) {
    if (-not $Quiet) {
        Write-Host "[ERROR] redis-cli.exe not found" -ForegroundColor Red
        Write-Host "Please specify the path to redis-cli.exe using the -RedisCliPath parameter" -ForegroundColor Yellow
        Write-Host "Or ensure Redis is installed and redis-cli.exe is in your PATH" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "Usage examples:" -ForegroundColor Cyan
        Write-Host "  .\scripts\clear-messages.ps1 -RedisCliPath 'C:\path\to\redis-cli.exe'" -ForegroundColor White
        Write-Host "  .\scripts\clear-messages.ps1 -RedisCliPath 'C:\Program Files\Redis\redis-cli.exe'" -ForegroundColor White
        Write-Host ""
        Write-Host "To find redis-cli.exe on your system, you can:" -ForegroundColor Cyan
        Write-Host "  1. Check where Redis is installed" -ForegroundColor White
        Write-Host "  2. Run: where.exe redis-cli.exe" -ForegroundColor White
        Write-Host "  3. Or search for it in File Explorer" -ForegroundColor White
    } else {
        Write-Host "[ERROR] redis-cli.exe not found" -ForegroundColor Red
    }
    exit 1
}

# Get all message keys (supports both Phase 1 and Phase 2 formats)
$messageKeys = @()

try {
    # Use redis-cli to get all keys matching the pattern
    # Try Phase 2 pattern first (message*), then fall back to Phase 1 pattern (timeline_message*)
    $phase2Keys = & $redisCliPath -h $RedisHost -p $RedisPort KEYS "message*"
    $phase1Keys = & $redisCliPath -h $RedisHost -p $RedisPort KEYS "timeline_message*"

    $phase2Count = ($phase2Keys | Where-Object { $_ -and $_.Trim() -ne "" }).Count
    $phase1Count = ($phase1Keys | Where-Object { $_ -and $_.Trim() -ne "" }).Count

    $totalPatternKeys = ($keysOutput | Where-Object { $_ -and $_.Trim() -ne "" }).Count

    if (-not $Quiet) {
        Write-Host "Phase 2 keys found: $phase2Count" -ForegroundColor White
        Write-Host "Phase 1 keys found: $phase1Count" -ForegroundColor White
        Write-Host "Total Redis keys matching patterns: $totalPatternKeys" -ForegroundColor White
    }

    $keysOutput = $phase2Keys + $phase1Keys

    # Debug: Show raw keys found
    if (-not $Quiet -and $keysOutput.Count -gt 0) {
        Write-Host "Raw keys found (before filtering):" -ForegroundColor Gray
        $keysOutput | Select-Object -First 10 | ForEach-Object {
            Write-Host "  - $_" -ForegroundColor DarkGray
        }
        if ($keysOutput.Count -gt 10) {
            Write-Host "  ... and $($keysOutput.Count - 10) more raw keys" -ForegroundColor DarkGray
        }
    }

    # Separate content keys from index keys
    $contentKeys = $keysOutput | Where-Object {
        $_ -and $_.Trim() -ne "" -and
        $_ -notmatch ":idx$" -and           # Exclude index keys
        $_ -notmatch "^message$" -and       # Exclude bare "message" key
        $_ -notmatch ":sessionId:" -and     # Exclude session index keys
        $_ -notmatch ":parentMessageId:"    # Exclude parent message index keys
    } | Select-Object -Unique

    $indexKeys = $keysOutput | Where-Object {
        $_ -and $_.Trim() -ne "" -and
        ($_ -match ":idx$" -or           # Index keys
         $_ -match ":sessionId:" -or     # Session index keys
         $_ -match ":parentMessageId:" -or # Parent message index keys
         $_ -eq "message")               # Bare "message" key
    } | Select-Object -Unique

    # Decide which keys to delete based on cleanup mode
    if ($ClearPostgres) {
        # Complete cleanup: delete everything
        $messageKeys = $keysOutput | Where-Object { $_ -and $_.Trim() -ne "" } | Select-Object -Unique
        $cleanupMode = "Complete (Redis + PostgreSQL - deleting all keys)"
    } else {
        # Redis-only cleanup: preserve indexes
        $messageKeys = $contentKeys
        $cleanupMode = "Redis Only (preserving index keys)"
    }

    # Debug: Show filtering results
    if (-not $Quiet) {
        Write-Host "Cleanup mode: $cleanupMode" -ForegroundColor Cyan
        Write-Host "Content keys found: $($contentKeys.Count)" -ForegroundColor White
        Write-Host "Index keys found: $($indexKeys.Count)" -ForegroundColor White
        Write-Host "Total keys to delete: $($messageKeys.Count)" -ForegroundColor Yellow

        if ($indexKeys.Count -gt 0) {
            Write-Host "Index keys that will be deleted:" -ForegroundColor Gray
            $indexKeys | Select-Object -First 3 | ForEach-Object {
                Write-Host "  - $_" -ForegroundColor DarkGray
            }
            if ($indexKeys.Count -gt 3) {
                Write-Host "  ... and $($indexKeys.Count - 3) more index keys" -ForegroundColor DarkGray
            }
        }
    }

    if (-not $Quiet -and $messageKeys.Count -gt 0) {
        Write-Host "Keys to delete:" -ForegroundColor White
        $messageKeys | Select-Object -First 5 | ForEach-Object {
            Write-Host "  - $_" -ForegroundColor Gray
        }
        if ($messageKeys.Count -gt 5) {
            Write-Host "  ... and $($messageKeys.Count - 5) more" -ForegroundColor Gray
        }
    } elseif (-not $Quiet -and $keysOutput.Count -gt 0) {
        Write-Host "No keys to delete (all keys were index/metadata keys)" -ForegroundColor Yellow
    }
    } catch {
        if (-not $Quiet) {
            Write-Host "[ERROR] Failed to query Redis keys" -ForegroundColor Red
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
            Write-Host ""
            Write-Host "Troubleshooting:" -ForegroundColor Yellow
            Write-Host "  - Make sure Redis server is running on $RedisHost`:$RedisPort" -ForegroundColor White
            Write-Host "  - Check if redis-cli.exe is working: & $redisCliPath ping" -ForegroundColor White
            Write-Host ""
            Write-Host "What this script does:" -ForegroundColor Cyan
            Write-Host "  - Finds all Redis keys matching message patterns" -ForegroundColor White
            Write-Host "  - Filters out index/metadata keys (preserves them)" -ForegroundColor White
            Write-Host "  - Only deletes actual message content keys" -ForegroundColor White
        } else {
            Write-Host "[ERROR] Redis query failed" -ForegroundColor Red
        }
        if (-not $ClearPostgres) {
            exit 1
        }
    }

# Analyze PostgreSQL data (if requested)
[int]$postgresChunkCount = 0

if ($ClearPostgres) {
    try {
        # Check if PostgreSQL container is running
        $containerStatus = & docker ps --filter "name=agent-pg" --format "{{.Status}}" 2>$null
        if (-not $containerStatus -or $containerStatus -notlike "*Up*") {
            Write-Host "[WARNING] PostgreSQL container 'agent-pg' is not running" -ForegroundColor Yellow
            Write-Host "Skipping PostgreSQL data analysis" -ForegroundColor Yellow
            $postgresChunkCount = 0
        } else {
            # Count chunks in PostgreSQL (only embeddings are stored there)
            if ($PostgresHost -eq "localhost" -or $PostgresHost -eq "127.0.0.1") {
                # Use docker exec for container - capture output properly
                $postgresChunkOutput = & docker exec agent-pg psql -U $PostgresUser -d $PostgresDatabase -t -c "SELECT COUNT(*) FROM message_chunk_embeddings;" 2>$null

                # Debug: Show raw output
                if (-not $Quiet) {
                    Write-Host "PostgreSQL chunk count output: '$postgresChunkOutput'" -ForegroundColor Gray
                }

                # Convert output to string and extract the number
                try {
                    $postgresChunkCount = [int](([string]$postgresChunkOutput).Trim())
                } catch {
                    Write-Host "[WARNING] Could not parse PostgreSQL chunk count: $postgresChunkOutput" -ForegroundColor Yellow
                    $postgresChunkCount = 0
                }
            } else {
                # Use direct psql connection (would need psql installed locally)
                Write-Host "[WARNING] Direct PostgreSQL connection not implemented - use container access" -ForegroundColor Yellow
                $postgresChunkCount = 0
            }
        }

        if (-not $Quiet) {
            Write-Host "Found $postgresChunkCount embedding chunk(s) in PostgreSQL" -ForegroundColor White
        }
    } catch {
        if (-not $Quiet) {
            Write-Host "[WARNING] Could not analyze PostgreSQL data: $($_.Exception.Message)" -ForegroundColor Yellow
        }
        $postgresChunkCount = 0
    }
}

$messageCount = $messageKeys.Count

if (-not $Quiet) {
    Write-Host "Found $messageCount message(s) in Redis" -ForegroundColor White

    if ($messageCount -eq 0 -and -not $ClearPostgres) {
        Write-Host "[INFO] No messages to clear" -ForegroundColor Gray
        exit 0
    }

    if ($ClearPostgres) {
        $totalItems = $messageCount + $postgresMessageCount + $postgresChunkCount
        if ($totalItems -eq 0) {
            Write-Host "[INFO] No data to clear in Redis or PostgreSQL" -ForegroundColor Gray
            exit 0
        }
    } elseif ($messageCount -eq 0) {
        Write-Host "[INFO] No messages to clear" -ForegroundColor Gray
        exit 0
    }

    if ($ShowKeys) {
        Write-Host ""
        Write-Host "Detailed key analysis:" -ForegroundColor Cyan
        Write-Host "====================" -ForegroundColor Cyan

        Write-Host "All Redis keys found:" -ForegroundColor Yellow
        $keysOutput | ForEach-Object {
            if ($_ -and $_.Trim() -ne "") {
                Write-Host "  $_" -ForegroundColor Gray
            }
        }

        Write-Host ""
        Write-Host "Content keys (actual messages):" -ForegroundColor Yellow
        if ($contentKeys.Count -gt 0) {
            $contentKeys | ForEach-Object {
                Write-Host "  $_" -ForegroundColor Green
            }
        } else {
            Write-Host "  (none found)" -ForegroundColor Gray
        }

        Write-Host ""
        Write-Host "Index/Metadata keys:" -ForegroundColor Yellow
        if ($indexKeys.Count -gt 0) {
            $indexKeys | ForEach-Object {
                Write-Host "  $_" -ForegroundColor Cyan
            }
        } else {
            Write-Host "  (none found)" -ForegroundColor Gray
        }

        Write-Host ""
        if ($ClearPostgres) {
            Write-Host "Cleanup mode: Complete (Redis + PostgreSQL)" -ForegroundColor Magenta
            Write-Host "Keys to delete: ALL ($($keysOutput.Count) keys)" -ForegroundColor Red
        } else {
            Write-Host "Cleanup mode: Redis Only (preserves indexes)" -ForegroundColor Magenta
            Write-Host "Keys to delete: Content only ($($contentKeys.Count) keys)" -ForegroundColor Red
        }

        Write-Host ""
        Write-Host "[INFO] Use -Force to actually delete the keys" -ForegroundColor Yellow
        exit 0
    }

    if (-not $Force) {
        Write-Host ""
        if ($ClearPostgres) {
            $totalItems = $messageCount + $postgresChunkCount
            Write-Host "COMPLETE CLEANUP MODE:" -ForegroundColor Magenta
            Write-Host "This will delete ALL Redis keys ($($keysOutput.Count) total) including:" -ForegroundColor Red
            Write-Host "  • Content keys: $($contentKeys.Count) message(s)" -ForegroundColor White
            Write-Host "  • Index keys: $($indexKeys.Count) metadata/index entries" -ForegroundColor Cyan
            Write-Host "  • PostgreSQL embedding chunks: $postgresChunkCount" -ForegroundColor White
            Write-Host "  • Total items: $totalItems" -ForegroundColor Cyan
            Write-Host ""
            $confirm = Read-Host "Are you sure you want to delete ALL data and metadata? (y/N)"
        } else {
            Write-Host "REDIS-ONLY CLEANUP MODE:" -ForegroundColor Magenta
            Write-Host "This will preserve index/metadata keys for future use:" -ForegroundColor Yellow
            Write-Host "  • Content keys to delete: $($contentKeys.Count) message(s)" -ForegroundColor White
            Write-Host "  • Index keys to preserve: $($indexKeys.Count) metadata entries" -ForegroundColor Cyan
            Write-Host ""
            $confirm = Read-Host "Are you sure you want to delete $messageCount message(s) but keep indexes? (y/N)"
        }
        if ($confirm -notmatch "^[Yy]") {
            Write-Host "[CANCELLED] Operation cancelled by user" -ForegroundColor Yellow
            exit 0
        }
    }

    Write-Host ""
    Write-Host "Clearing messages..." -ForegroundColor Yellow
}

# Clear the messages
$deletedCount = 0
$postgresDeletedChunks = 0
$postgresErrors = @()
$errors = @()

# Clear Redis messages
foreach ($key in $messageKeys) {
    try {
        $result = & $redisCliPath -h $RedisHost -p $RedisPort DEL $key
        if ($result -eq "1") {
            $deletedCount++
        } else {
            $errors += "Failed to delete key: $key"
        }
    } catch {
        $errors += "Error deleting key '$key': $($_.Exception.Message)"
    }
}

# Clear PostgreSQL data (if requested)
if ($ClearPostgres) {
    Write-Host "Clearing PostgreSQL data..." -ForegroundColor Yellow

    try {
        # Clear message chunks and embeddings
        & docker exec agent-pg psql -U $PostgresUser -d $PostgresDatabase -c "DELETE FROM message_chunk_embeddings;" 2>$null
        if ($LASTEXITCODE -eq 0) {
            # Get count of deleted chunks
            $chunkCountCheck = & docker exec agent-pg psql -U $PostgresUser -d $PostgresDatabase -t -c "SELECT COUNT(*) FROM message_chunk_embeddings;" 2>$null
            try {
                $chunkCountCheck = [int](([string]$chunkCountCheck).Trim())
                $postgresDeletedChunks = [int]$postgresChunkCount - [int]$chunkCountCheck
            } catch {
                Write-Host "[WARNING] Could not calculate deleted chunks count" -ForegroundColor Yellow
                $postgresDeletedChunks = 0
            }
        } else {
            $postgresErrors += "Failed to delete message chunks"
        }

        # Note: PostgreSQL only contains embedding chunks, not message content
        # Message content is stored in Redis only

        if (-not $Quiet) {
            Write-Host "PostgreSQL clearing completed" -ForegroundColor Green
        }
    } catch {
        $postgresErrors += "Error clearing PostgreSQL data: $($_.Exception.Message)"
        if (-not $Quiet) {
            Write-Host "[ERROR] Failed to clear PostgreSQL data: $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

# Summary
if (-not $Quiet) {
    Write-Host ""
    Write-Host "====================================" -ForegroundColor Green
    Write-Host "Clear Messages Summary" -ForegroundColor Green
    Write-Host "====================================" -ForegroundColor Green
    Write-Host ""

    # Redis results
    Write-Host "Redis Results:" -ForegroundColor Cyan
    Write-Host "  • Messages found: $messageCount" -ForegroundColor White
    Write-Host "  • Messages deleted: $deletedCount" -ForegroundColor Green

    # PostgreSQL results (if cleared)
    if ($ClearPostgres) {
        Write-Host ""
        Write-Host "PostgreSQL Results:" -ForegroundColor Cyan
        Write-Host "  • Embedding chunks found: $postgresChunkCount" -ForegroundColor White
        Write-Host "  • Chunks deleted: $postgresDeletedChunks" -ForegroundColor Green
        Write-Host "  • Note: Messages are stored in Redis, not PostgreSQL" -ForegroundColor Gray
    }

    # Error reporting
    $totalErrors = $errors.Count + $postgresErrors.Count
    if ($totalErrors -gt 0) {
        Write-Host ""
        Write-Host "Errors encountered: $totalErrors" -ForegroundColor Red
        Write-Host ""
        Write-Host "Redis Errors:" -ForegroundColor Red
        foreach ($err in $errors) {
            Write-Host "  - $err" -ForegroundColor Red
        }
        if ($postgresErrors.Count -gt 0) {
            Write-Host ""
            Write-Host "PostgreSQL Errors:" -ForegroundColor Red
            foreach ($err in $postgresErrors) {
                Write-Host "  - $err" -ForegroundColor Red
            }
        }
    }

    # Success determination
    $totalItemsFound = $messageCount
    $totalItemsDeleted = $deletedCount

    if ($ClearPostgres) {
        $totalItemsFound += $postgresChunkCount
        $totalItemsDeleted += $postgresDeletedChunks
    }

    if ($totalItemsDeleted -eq $totalItemsFound -and $totalErrors -eq 0) {
        Write-Host ""
        Write-Host "[SUCCESS] All data cleared successfully!" -ForegroundColor Green
        if ($ClearPostgres) {
            Write-Host "  • Redis: $deletedCount messages" -ForegroundColor Green
            Write-Host "  • PostgreSQL: $postgresDeletedMessages messages, $postgresDeletedChunks chunks" -ForegroundColor Green
        }
    } elseif ($totalItemsDeleted -gt 0) {
        Write-Host ""
        Write-Host "[WARNING] Some data was cleared, but errors occurred" -ForegroundColor Yellow
    } elseif ($totalItemsFound -eq 0) {
        Write-Host ""
        Write-Host "[INFO] No data found to clear" -ForegroundColor Gray
    } else {
        Write-Host ""
        Write-Host "[ERROR] Failed to clear data" -ForegroundColor Red
    }
} else {
    # Quiet mode - just show result
    if ($deletedCount -gt 0 -or ($ClearPostgres -and ($postgresDeletedMessages -gt 0 -or $postgresDeletedChunks -gt 0))) {
        $totalCleared = $deletedCount + $postgresDeletedMessages + $postgresDeletedChunks
        Write-Host "[SUCCESS] Cleared $totalCleared item(s)" -ForegroundColor Green
    } else {
        Write-Host "[INFO] No data to clear" -ForegroundColor Gray
    }
}

if (-not $Quiet -and -not $NoPause) {
    Write-Host ""
    Read-Host "Press Enter to continue"
}

# Return appropriate exit code
$totalErrors = $errors.Count + $postgresErrors.Count
$totalDeleted = $deletedCount + $postgresDeletedMessages + $postgresDeletedChunks

if ($ClearPostgres) {
    $totalExpected = $messageCount + $postgresMessageCount + $postgresChunkCount
} else {
    $totalExpected = $messageCount
}

if ($totalErrors -gt 0 -or $totalDeleted -eq 0) {
    exit 1
} else {
    exit 0
}
