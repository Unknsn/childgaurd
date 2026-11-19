# SafeBand Guardian v4.0.0 - Backend Status Script (PowerShell)

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " SafeBand Guardian Backend Status Check" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$backendRunning = $false
$tunnelRunning = $false

if (Test-Path ".backend.pid") {
    $bPid = Get-Content ".backend.pid" -ErrorAction SilentlyContinue
    if ($bPid -and (Get-Process -Id $bPid -ErrorAction SilentlyContinue)) {
        $backendRunning = $true
        Write-Host " Backend Service:   RUNNING (PID: $bPid)" -ForegroundColor Green
    }
}
if (-not $backendRunning) {
    Write-Host " Backend Service:   STOPPED" -ForegroundColor Red
}

if (Test-Path ".tunnel.pid") {
    $tPid = Get-Content ".tunnel.pid" -ErrorAction SilentlyContinue
    if ($tPid -and (Get-Process -Id $tPid -ErrorAction SilentlyContinue)) {
        $tunnelRunning = $true
        Write-Host " Secure Tunnel:     RUNNING (PID: $tPid)" -ForegroundColor Green
    }
}
if (-not $tunnelRunning) {
    Write-Host " Secure Tunnel:     STOPPED" -ForegroundColor Yellow
}

$publicUrl = if (Test-Path ".public-url") { Get-Content ".public-url" } else { "http://localhost:8080" }
Write-Host " Configured URL:    $publicUrl" -ForegroundColor Cyan

# Health Query
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/health" -Method Get -TimeoutSec 2 -ErrorAction Stop
    Write-Host " Health Status:     $($health.status) (v$($health.version))" -ForegroundColor Green
} catch {
    Write-Host " Health Status:     UNREACHABLE ($($_.Exception.Message))" -ForegroundColor Red
}

Write-Host "============================================================" -ForegroundColor Cyan
