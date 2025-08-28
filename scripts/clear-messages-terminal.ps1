param(
    [switch]$NoLogo,
    [switch]$Force,
    [switch]$Quiet,
    [switch]$NoWait,
    [string]$RedisHost = "localhost",
    [int]$RedisPort = 6379,
    [string]$RedisCliPath = $null,
    [switch]$ClearPostgres,
    [string]$PostgresHost = "localhost",
    [int]$PostgresPort = 5432,
    [string]$PostgresDatabase = "agent_timeline",
    [string]$PostgresUser = "postgres",
    [string]$PostgresPassword = "postgres"
)

# Terminal-optimized clear messages script for PowerShell execution
# Designed for interactive terminal use with better feedback

$ErrorActionPreference = "Stop"

if (-not $NoLogo -and -not $Quiet) {
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host "AgentTimeline - Terminal Clear Messages" -ForegroundColor Cyan
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host "Clearing Redis messages and optionally PostgreSQL data..." -ForegroundColor Yellow
    Write-Host ""
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
        Write-Host "[ERROR] Specified redis-cli.exe path not found: $RedisCliPath" -ForegroundColor Red
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
    Write-Host "[ERROR] redis-cli.exe not found" -ForegroundColor Red
    Write-Host "Please specify the path to redis-cli.exe using the -RedisCliPath parameter" -ForegroundColor Yellow
    Write-Host "Or ensure Redis is installed and redis-cli.exe is in your PATH" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Usage examples:" -ForegroundColor Cyan
    Write-Host "  .\scripts\clear-messages-terminal.ps1 -RedisCliPath 'C:\path\to\redis-cli.exe'" -ForegroundColor White
    Write-Host "  .\scripts\clear-messages-terminal.ps1 -RedisCliPath 'C:\Program Files\Redis\redis-cli.exe'" -ForegroundColor White
    Write-Host ""
    Write-Host "To find redis-cli.exe on your system, you can:" -ForegroundColor Cyan
    Write-Host "  1. Check where Redis is installed" -ForegroundColor White
    Write-Host "  2. Run: where.exe redis-cli.exe" -ForegroundColor White
    Write-Host "  3. Or search for it in File Explorer" -ForegroundColor White
    exit 1
}

# Check if Redis is accessible
try {
    $redisConnection = Get-NetTCPConnection -LocalPort $RedisPort -ErrorAction SilentlyContinue | Where-Object { $_.State -eq "Listen" }
    if (-not $redisConnection) {
        throw "Redis server not found on port $RedisPort"
    }
} catch {
    Write-Host "[ERROR] Cannot connect to Redis server on $RedisHost`:$RedisPort" -ForegroundColor Red
    Write-Host "Make sure Redis server is running on $RedisHost`:$RedisPort" -ForegroundColor Yellow
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
                Write-Host "[WARNING] PostgreSQL container 'agent-pg' not accessible, trying direct connection..." -ForegroundColor Yellow
            }
        }
    } catch {
        Write-Host "[ERROR] Cannot connect to PostgreSQL server on $PostgresHost`:$PostgresPort" -ForegroundColor Red
        Write-Host "Make sure PostgreSQL is running or use -PostgresHost and -PostgresPort to specify connection" -ForegroundColor Yellow
        exit 1
    }
}

# Get all timeline message keys
$timelineKeys = @()

# Analyze PostgreSQL data (if requested)
$postgresMessageCount = 0
$postgresChunkCount = 0

if ($ClearPostgres) {
    try {
        # Count messages in PostgreSQL
        if ($PostgresHost -eq "localhost" -or $PostgresHost -eq "127.0.0.1") {
            # Use docker exec for container
            $postgresMessageCount = docker exec agent-pg psql -U $PostgresUser -d $PostgresDatabase -t -c "SELECT COUNT(*) FROM message;" 2>$null
            $postgresChunkCount = docker exec agent-pg psql -U $PostgresUser -d $PostgresDatabase -t -c "SELECT COUNT(*) FROM message_chunk_embeddings;" 2>$null
        } else {
            # Use direct psql connection (would need psql installed locally)
            Write-Host "[WARNING] Direct PostgreSQL connection not implemented - use container access" -ForegroundColor Yellow
        }

        # Clean up the output (remove whitespace)
        $postgresMessageCount = [int]($postgresMessageCount -replace '\s', '')
        $postgresChunkCount = [int]($postgresChunkCount -replace '\s', '')
    } catch {
        if (-not $Quiet) {
            Write-Host "[WARNING] Could not analyze PostgreSQL data: $($_.Exception.Message)" -ForegroundColor Yellow
        }
    }
}

if (-not $Quiet) {
    Write-Host "Connected to Redis at $RedisHost`:$RedisPort" -ForegroundColor Green
    Write-Host "Using redis-cli: $redisCliPath" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Scanning for timeline messages..." -ForegroundColor Yellow
}

try {
    # Use redis-cli to get all keys matching the pattern
    $keysOutput = & $redisCliPath -h $RedisHost -p $RedisPort KEYS "timeline_message*"
    $timelineKeys = $keysOutput | Where-Object { $_ -and $_.Trim() -ne "" }
} catch {
    Write-Host "[ERROR] Failed to query Redis keys" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    Write-Host "Troubleshooting:" -ForegroundColor Yellow
    Write-Host "  - Make sure Redis server is running on $RedisHost`:$RedisPort" -ForegroundColor White
    Write-Host "  - Check if redis-cli.exe is working: & '$redisCliPath' ping" -ForegroundColor White
    exit 1
}

$messageCount = $timelineKeys.Count

if (-not $Quiet) {
    Write-Host "Found $messageCount message(s) in Redis" -ForegroundColor White

    if ($ClearPostgres) {
        Write-Host "Found $postgresMessageCount message(s) in PostgreSQL" -ForegroundColor White
        Write-Host "Found $postgresChunkCount chunk(s) in PostgreSQL" -ForegroundColor White
    }

    $totalItems = $messageCount
    if ($ClearPostgres) {
        $totalItems += $postgresMessageCount + $postgresChunkCount
    }

    if ($totalItems -eq 0) {
        Write-Host "[INFO] No data to clear" -ForegroundColor Gray
        exit 0
    }

    Write-Host ""
    Write-Host "Data to be deleted:" -ForegroundColor Cyan

    if ($messageCount -gt 0) {
        Write-Host "Redis messages:" -ForegroundColor Yellow
        $timelineKeys | Select-Object -First 10 | ForEach-Object {
            Write-Host "  • $_" -ForegroundColor White
        }
        if ($timelineKeys.Count -gt 10) {
            Write-Host "  ... and $($timelineKeys.Count - 10) more" -ForegroundColor Gray
        }
    }

    if ($ClearPostgres -and ($postgresMessageCount -gt 0 -or $postgresChunkCount -gt 0)) {
        Write-Host ""
        Write-Host "PostgreSQL data:" -ForegroundColor Yellow
        if ($postgresMessageCount -gt 0) {
            Write-Host "  • $postgresMessageCount messages from 'message' table" -ForegroundColor White
        }
        if ($postgresChunkCount -gt 0) {
            Write-Host "  • $postgresChunkCount chunks from 'message_chunk_embeddings' table" -ForegroundColor White
        }
    }

    Write-Host ""
    Write-Host "Total items to delete: $totalItems" -ForegroundColor Cyan
    Write-Host ""
}

# Confirmation prompt (unless forced)
if (-not $Force -and $totalItems -gt 0) {
    if ($ClearPostgres) {
        Write-Host "Are you sure you want to delete ALL data from both Redis and PostgreSQL?" -ForegroundColor Yellow
        Write-Host "This will permanently remove:" -ForegroundColor Red
        if ($messageCount -gt 0) {
            Write-Host "  • $messageCount Redis message(s)" -ForegroundColor White
        }
        if ($postgresMessageCount -gt 0) {
            Write-Host "  • $postgresMessageCount PostgreSQL message(s)" -ForegroundColor White
        }
        if ($postgresChunkCount -gt 0) {
            Write-Host "  • $postgresChunkCount PostgreSQL chunk(s)" -ForegroundColor White
        }
        Write-Host ""
        $confirm = Read-Host "Type 'yes' to continue or any other key to cancel"
    } else {
        Write-Host "Are you sure you want to delete all $messageCount message(s)?" -ForegroundColor Yellow
        $confirm = Read-Host "Type 'yes' to continue or any other key to cancel"
    }

    if ($confirm -ne 'yes') {
        Write-Host "[CANCELLED] Operation cancelled by user" -ForegroundColor Yellow
        exit 0
    }
}

# Clear the messages
if (-not $Quiet) {
    Write-Host ""
    Write-Host "Deleting messages..." -ForegroundColor Yellow
}

$deletedCount = 0
$postgresDeletedMessages = 0
$postgresDeletedChunks = 0
$postgresErrors = @()
$errors = @()

# Clear Redis messages
foreach ($key in $timelineKeys) {
    try {
        $result = & $redisCliPath -h $RedisHost -p $RedisPort DEL $key
        if ($result -eq "1") {
            $deletedCount++
            if (-not $Quiet) {
                Write-Host "  [DELETED] $key" -ForegroundColor Green
            }
        } else {
            $errors += "Failed to delete key: $key"
            if (-not $Quiet) {
                Write-Host "  [ERROR] Failed to delete: $key" -ForegroundColor Red
            }
        }
    } catch {
        $errors += "Error deleting key '$key': $($_.Exception.Message)"
        if (-not $Quiet) {
            Write-Host "  [ERROR] Exception deleting: $key - $($_.Exception.Message)" -ForegroundColor Red
        }
    }
}

# Clear PostgreSQL data (if requested)
if ($ClearPostgres) {
    if (-not $Quiet) {
        Write-Host ""
        Write-Host "Clearing PostgreSQL data..." -ForegroundColor Yellow
    }

    try {
        # Clear message chunks and embeddings
        $result = docker exec agent-pg psql -U $PostgresUser -d $PostgresDatabase -c "DELETE FROM message_chunk_embeddings;" 2>$null
        if ($LASTEXITCODE -eq 0) {
            # Get count of deleted chunks
            $chunkCountCheck = docker exec agent-pg psql -U $PostgresUser -d $PostgresDatabase -t -c "SELECT COUNT(*) FROM message_chunk_embeddings;" 2>$null
            $chunkCountCheck = [int]($chunkCountCheck -replace '\s', '')
            $postgresDeletedChunks = $postgresChunkCount - $chunkCountCheck
            if (-not $Quiet) {
                Write-Host "  [DELETED] $postgresDeletedChunks chunks from message_chunk_embeddings" -ForegroundColor Green
            }
        } else {
            $postgresErrors += "Failed to delete message chunks"
            if (-not $Quiet) {
                Write-Host "  [ERROR] Failed to delete message chunks" -ForegroundColor Red
            }
        }

        # Clear messages
        $result = docker exec agent-pg psql -U $PostgresUser -d $PostgresDatabase -c "DELETE FROM message;" 2>$null
        if ($LASTEXITCODE -eq 0) {
            # Get count of deleted messages
            $messageCountCheck = docker exec agent-pg psql -U $PostgresUser -d $PostgresDatabase -t -c "SELECT COUNT(*) FROM message;" 2>$null
            $messageCountCheck = [int]($messageCountCheck -replace '\s', '')
            $postgresDeletedMessages = $postgresMessageCount - $messageCountCheck
            if (-not $Quiet) {
                Write-Host "  [DELETED] $postgresDeletedMessages messages from message table" -ForegroundColor Green
            }
        } else {
            $postgresErrors += "Failed to delete messages"
            if (-not $Quiet) {
                Write-Host "  [ERROR] Failed to delete messages" -ForegroundColor Red
            }
        }

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
        Write-Host "  • Messages found: $postgresMessageCount" -ForegroundColor White
        Write-Host "  • Messages deleted: $postgresDeletedMessages" -ForegroundColor Green
        Write-Host "  • Chunks found: $postgresChunkCount" -ForegroundColor White
        Write-Host "  • Chunks deleted: $postgresDeletedChunks" -ForegroundColor Green
    }

    # Error reporting
    $totalErrors = $errors.Count + $postgresErrors.Count
    if ($totalErrors -gt 0) {
        Write-Host ""
        Write-Host "Errors encountered: $totalErrors" -ForegroundColor Red
        Write-Host ""
        Write-Host "Redis Errors:" -ForegroundColor Red
        foreach ($error in $errors) {
            Write-Host "  - $error" -ForegroundColor Red
        }
        if ($postgresErrors.Count -gt 0) {
            Write-Host ""
            Write-Host "PostgreSQL Errors:" -ForegroundColor Red
            foreach ($error in $postgresErrors) {
                Write-Host "  - $error" -ForegroundColor Red
            }
        }
    }

    # Success determination
    $totalItemsFound = $messageCount
    $totalItemsDeleted = $deletedCount

    if ($ClearPostgres) {
        $totalItemsFound += $postgresMessageCount + $postgresChunkCount
        $totalItemsDeleted += $postgresDeletedMessages + $postgresDeletedChunks
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

if (-not $Quiet -and -not $NoWait) {
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
