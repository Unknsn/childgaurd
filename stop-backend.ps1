# SafeBand Guardian v4.0.0 - Stop Backend Script (PowerShell)

Write-Host "Stopping SafeBand Guardian Backend & Tunnel..." -ForegroundColor Yellow

if (Test-Path ".backend.pid") {
    $bPid = Get-Content ".backend.pid" -ErrorAction SilentlyContinue
    if ($bPid) {
        $p = Get-Process -Id $bPid -ErrorAction SilentlyContinue
        if ($p) {
            Stop-Process -Id $bPid -Force
            Write-Host "[OK] Stopped backend service (PID: $bPid)" -ForegroundColor Green
        }
    }
    Remove-Item ".backend.pid" -Force -ErrorAction SilentlyContinue
}

if (Test-Path ".tunnel.pid") {
    $tPid = Get-Content ".tunnel.pid" -ErrorAction SilentlyContinue
    if ($tPid) {
        $p = Get-Process -Id $tPid -ErrorAction SilentlyContinue
        if ($p) {
            Stop-Process -Id $tPid -Force
            Write-Host "[OK] Stopped secure tunnel (PID: $tPid)" -ForegroundColor Green
        }
    }
    Remove-Item ".tunnel.pid" -Force -ErrorAction SilentlyContinue
}

Write-Host "SafeBand Backend is safely stopped." -ForegroundColor Green
