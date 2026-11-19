# SafeBand Guardian v4.0.1 - Backend Status Script (PowerShell)
Set-Location $PSScriptRoot

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " SafeBand Guardian Backend Status Check (v4.0.1)" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Determine configured port
$port = 8080
if (Test-Path ".env") {
    Get-Content ".env" | ForEach-Object {
        $line = $_.Trim()
        if ($line.StartsWith("PORT=")) {
            $val = $line.Split("=")[1].Trim()
            if ($val) { $port = [int]$val }
        }
    }
}

$backendRunning = $false
$realPid = $null

# Check port listener first
try {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($conns) {
        $realPid = $conns[0].OwningProcess
        $backendRunning = $true
        # Sync .backend.pid
        $realPid | Out-File "$PSScriptRoot\.backend.pid" -Encoding ascii
    }
} catch {}

if (-not $backendRunning -and (Test-Path ".backend.pid")) {
    $bPid = Get-Content ".backend.pid" -ErrorAction SilentlyContinue
    if ($bPid -and (Get-Process -Id $bPid -ErrorAction SilentlyContinue)) {
        $backendRunning = $true
        $realPid = $bPid
    }
}

if ($backendRunning) {
    Write-Host " Backend Service:   RUNNING (PID: $realPid, Port: $port)" -ForegroundColor Green
} else {
    Write-Host " Backend Service:   STOPPED (Port $port is free)" -ForegroundColor Red
}

# 2. Check Tunnel Process
$tunnelRunning = $false
$tPid = $null
if (Test-Path ".tunnel.pid") {
    $candPid = Get-Content ".tunnel.pid" -ErrorAction SilentlyContinue
    if ($candPid -and (Get-Process -Id $candPid -ErrorAction SilentlyContinue)) {
        $tunnelRunning = $true
        $tPid = $candPid
    }
}
if (-not $tunnelRunning) {
    $cfProcs = Get-Process -Name "cloudflared" -ErrorAction SilentlyContinue
    if ($cfProcs) {
        $tunnelRunning = $true
        $tPid = $cfProcs[0].Id
    }
}

if ($tunnelRunning) {
    Write-Host " Secure Tunnel:     RUNNING (PID: $tPid)" -ForegroundColor Green
} else {
    Write-Host " Secure Tunnel:     STOPPED" -ForegroundColor Yellow
}

$publicUrl = if (Test-Path ".public-url") { Get-Content ".public-url" } else { "http://localhost:$port" }
Write-Host " Configured URL:    $publicUrl" -ForegroundColor Cyan

# 3. Health Query
try {
    $health = Invoke-RestMethod -Uri "http://localhost:$port/api/v1/health" -Method Get -TimeoutSec 3 -ErrorAction Stop
    Write-Host " Health Status:     $($health.status) (Version: $($health.version))" -ForegroundColor Green
} catch {
    Write-Host " Health Status:     UNREACHABLE ($($_.Exception.Message))" -ForegroundColor Red
}

Write-Host "============================================================" -ForegroundColor Cyan
