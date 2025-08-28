param(
    [switch]$Quiet,
    [switch]$NoPause,
    [string]$RedisHost = "localhost",
    [int]$RedisPort = 6379,
    [switch]$Force,
    [string]$RedisCliPath = $null
)

if (-not $Quiet) {
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host "AgentTimeline - Clear Messages" -ForegroundColor Cyan
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host "Clearing stored messages from Redis..." -ForegroundColor Yellow
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

# Get all timeline message keys
$timelineKeys = @()

try {
    # Use redis-cli to get all keys matching the pattern
    $keysOutput = & $redisCliPath -h $RedisHost -p $RedisPort KEYS "timeline_message*"
    $timelineKeys = $keysOutput | Where-Object { $_ -and $_.Trim() -ne "" }
} catch {
    if (-not $Quiet) {
        Write-Host "[ERROR] Failed to query Redis keys" -ForegroundColor Red
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host ""
        Write-Host "Troubleshooting:" -ForegroundColor Yellow
        Write-Host "  - Make sure Redis server is running on $RedisHost`:$RedisPort" -ForegroundColor White
        Write-Host "  - Check if redis-cli.exe is working: & $redisCliPath ping" -ForegroundColor White
    } else {
        Write-Host "[ERROR] Redis query failed" -ForegroundColor Red
    }
    exit 1
}

$messageCount = $timelineKeys.Count

if (-not $Quiet) {
    Write-Host "Found $messageCount message(s) in Redis" -ForegroundColor White

    if ($messageCount -eq 0) {
        Write-Host "[INFO] No messages to clear" -ForegroundColor Gray
        exit 0
    }

    if (-not $Force) {
        Write-Host ""
        $confirm = Read-Host "Are you sure you want to delete all $messageCount message(s)? (y/N)"
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
$errors = @()

foreach ($key in $timelineKeys) {
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

# Summary
if (-not $Quiet) {
    Write-Host ""
    Write-Host "====================================" -ForegroundColor Green
    Write-Host "Clear Messages Summary" -ForegroundColor Green
    Write-Host "====================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Total messages found: $messageCount" -ForegroundColor White
    Write-Host "Successfully deleted: $deletedCount" -ForegroundColor Green

    if ($errors.Count -gt 0) {
        Write-Host "Errors encountered: $($errors.Count)" -ForegroundColor Red
        Write-Host ""
        Write-Host "Errors:" -ForegroundColor Red
        foreach ($error in $errors) {
            Write-Host "  - $error" -ForegroundColor Red
        }
    }

    if ($deletedCount -eq $messageCount -and $errors.Count -eq 0) {
        Write-Host ""
        Write-Host "[SUCCESS] All messages cleared successfully!" -ForegroundColor Green
    } elseif ($deletedCount -gt 0) {
        Write-Host ""
        Write-Host "[WARNING] Some messages were cleared, but errors occurred" -ForegroundColor Yellow
    } else {
        Write-Host ""
        Write-Host "[ERROR] Failed to clear any messages" -ForegroundColor Red
    }
} else {
    # Quiet mode - just show result
    if ($deletedCount -gt 0) {
        Write-Host "[SUCCESS] Cleared $deletedCount message(s)" -ForegroundColor Green
    } else {
        Write-Host "[INFO] No messages to clear" -ForegroundColor Gray
    }
}

if (-not $Quiet -and -not $NoPause) {
    Write-Host ""
    Read-Host "Press Enter to continue"
}

# Return appropriate exit code
if ($errors.Count -gt 0 -or $deletedCount -eq 0) {
    exit 1
} else {
    exit 0
}
