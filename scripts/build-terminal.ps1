param(
    [switch]$NoLogo,
    [switch]$Verbose
)

# Terminal-optimized build script for PowerShell execution
# Designed for interactive terminal use, not double-clicking

$ErrorActionPreference = "Stop"

if (-not $NoLogo) {
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host "AgentTimeline - Terminal Build" -ForegroundColor Cyan
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host ""
}

$projectDir = Split-Path -Parent $PSScriptRoot
$originalLocation = Get-Location

try {
    Set-Location $projectDir

    if ($Verbose) {
        Write-Host "Project directory: $projectDir" -ForegroundColor Gray
        Write-Host "Building AgentTimeline application..." -ForegroundColor Yellow
        Write-Host ""
    }

    # Run Maven build with real-time output
    $buildProcess = Start-Process -FilePath "mvn" -ArgumentList "clean", "compile" -NoNewWindow -PassThru -Wait

    if ($buildProcess.ExitCode -eq 0) {
        if (-not $NoLogo) {
            Write-Host ""
            Write-Host "[SUCCESS] Build completed successfully!" -ForegroundColor Green
            Write-Host ""
            Write-Host "Next steps:" -ForegroundColor Cyan
            Write-Host "  • Start application: .\scripts\start-app-terminal.ps1" -ForegroundColor White
            Write-Host "  • Run tests: .\scripts\run-tests-terminal.ps1" -ForegroundColor White
            Write-Host "  • Start all services: .\scripts\start-full-terminal.ps1" -ForegroundColor White
        }
        exit 0
    } else {
        if (-not $NoLogo) {
            Write-Host ""
            Write-Host "[ERROR] Build failed with exit code $($buildProcess.ExitCode)" -ForegroundColor Red
            Write-Host ""
            Write-Host "Common solutions:" -ForegroundColor Yellow
            Write-Host "  • Check Java installation: java -version" -ForegroundColor White
            Write-Host "  • Check Maven installation: mvn -version" -ForegroundColor White
            Write-Host "  • Clean and try again: .\scripts\build-terminal.ps1 -Verbose" -ForegroundColor White
        }
        exit $buildProcess.ExitCode
    }
} catch {
    if (-not $NoLogo) {
        Write-Host ""
        Write-Host "[ERROR] Build failed with exception: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host ""
        Write-Host "Make sure Java and Maven are installed and in your PATH." -ForegroundColor Yellow
    }
    exit 1
} finally {
    Set-Location $originalLocation
}
