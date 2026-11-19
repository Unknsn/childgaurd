# SafeBand Guardian v4.0.1 - Stop Backend Script (PowerShell)
Set-Location $PSScriptRoot

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " Stopping SafeBand Guardian Backend & Tunnel..." -ForegroundColor Yellow
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

# 2. Terminate by Listening Port (Most reliable)
try {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($conns) {
        foreach ($c in $conns) {
            $pId = $c.OwningProcess
            if ($pId -gt 0) {
                Write-Host "[STOP] Stopping backend process on port $port (PID: $pId)..." -ForegroundColor Yellow
                Stop-Process -Id $pId -Force -ErrorAction SilentlyContinue
            }
        }
    }
} catch {}

# 3. Terminate by .backend.pid
if (Test-Path ".backend.pid") {
    $bPid = Get-Content ".backend.pid" -ErrorAction SilentlyContinue
    if ($bPid) {
        $p = Get-Process -Id $bPid -ErrorAction SilentlyContinue
        if ($p) {
            Stop-Process -Id $bPid -Force -ErrorAction SilentlyContinue
            Write-Host "[OK] Stopped backend service (PID: $bPid)" -ForegroundColor Green
        }
    }
    Remove-Item ".backend.pid" -Force -ErrorAction SilentlyContinue
}

# 4. Terminate Tunnel Process
if (Test-Path ".tunnel.pid") {
    $tPid = Get-Content ".tunnel.pid" -ErrorAction SilentlyContinue
    if ($tPid) {
        $p = Get-Process -Id $tPid -ErrorAction SilentlyContinue
        if ($p) {
            Stop-Process -Id $tPid -Force -ErrorAction SilentlyContinue
            Write-Host "[OK] Stopped secure tunnel (PID: $tPid)" -ForegroundColor Green
        }
    }
    Remove-Item ".tunnel.pid" -Force -ErrorAction SilentlyContinue
}
Get-Process -Name "cloudflared" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

if (Test-Path ".public-url") {
    Remove-Item ".public-url" -Force -ErrorAction SilentlyContinue
}

Start-Sleep -Milliseconds 500

# 5. Verification
$remainingConn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
if ($remainingConn) {
    Write-Host "[WARNING] Port $port is still bound by PID $($remainingConn[0].OwningProcess)!" -ForegroundColor Red
} else {
    Write-Host "[OK] Port $port is free. SafeBand Backend is safely stopped." -ForegroundColor Green
}
Write-Host "============================================================" -ForegroundColor Cyan
