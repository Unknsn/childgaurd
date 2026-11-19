# SafeBand Guardian v4.0.0 - Unified Backend Startup Script (PowerShell)
[CmdletBinding()]
param(
    [switch]$NoTunnel,
    [switch]$Foreground
)

Set-Location $PSScriptRoot

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " SafeBand Guardian v4.0.0 - Laptop Backend Starter" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Ensure tools and data directories exist
if (-not (Test-Path "tools")) { New-Item -ItemType Directory -Path "tools" | Out-Null }
if (-not (Test-Path "data")) { New-Item -ItemType Directory -Path "data" | Out-Null }
if (-not (Test-Path "backend/data")) { New-Item -ItemType Directory -Path "backend/data" | Out-Null }

# 2. Check / Initialize and load .env file
if (-not (Test-Path ".env")) {
    if (Test-Path ".env.example") {
        Write-Host "[SETUP] .env not found. Generating default from .env.example..." -ForegroundColor Yellow
        Copy-Item ".env.example" ".env"
    }
}

if (Test-Path ".env") {
    Get-Content ".env" | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $key = $parts[0].Trim()
            $val = $parts[1].Trim().Trim('"').Trim("'")
            if (-not [string]::IsNullOrEmpty($key)) {
                [Environment]::SetEnvironmentVariable($key, $val, "Process")
            }
        }
    }
    Write-Host "[CONFIG] Environment variables loaded from .env" -ForegroundColor Gray
}

$port = if ($env:PORT) { [int]$env:PORT } else { 8080 }

# 3. Aggressive Pre-Cleanup: Ensure port and previous instances are free
Write-Host "[CHECK] Inspecting port $port and lingering processes..." -ForegroundColor Cyan

# Kill any process listening on the target port
try {
    $existingConns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($existingConns) {
        foreach ($conn in $existingConns) {
            $pId = $conn.OwningProcess
            if ($pId -gt 0) {
                Write-Host "[CLEANUP] Port $port is occupied by PID $pId. Stopping process..." -ForegroundColor Yellow
                Stop-Process -Id $pId -Force -ErrorAction SilentlyContinue
            }
        }
        Start-Sleep -Milliseconds 800
    }
} catch {}

# Stop previous PID in .backend.pid if any
if (Test-Path ".backend.pid") {
    $oldPid = Get-Content ".backend.pid" -ErrorAction SilentlyContinue
    if ($oldPid -and (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {
        Write-Host "[CLEANUP] Stopping previous backend recorded in .backend.pid (PID: $oldPid)..." -ForegroundColor Yellow
        Stop-Process -Id $oldPid -Force -ErrorAction SilentlyContinue
    }
    Remove-Item ".backend.pid" -Force -ErrorAction SilentlyContinue
}

# Stop previous tunnel instance if any
if (Test-Path ".tunnel.pid") {
    $oldTunPid = Get-Content ".tunnel.pid" -ErrorAction SilentlyContinue
    if ($oldTunPid -and (Get-Process -Id $oldTunPid -ErrorAction SilentlyContinue)) {
        Stop-Process -Id $oldTunPid -Force -ErrorAction SilentlyContinue
    }
    Remove-Item ".tunnel.pid" -Force -ErrorAction SilentlyContinue
}
Get-Process -Name "cloudflared" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

# 4. Locate and Verify Java runtime
$javaExe = "java"
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $javaExe = "$env:JAVA_HOME\bin\java.exe"
} else {
    $jdkCandidates = Get-ChildItem "C:\Program Files\Java\" -Recurse -Filter "java.exe" -ErrorAction SilentlyContinue | Where-Object { $_.FullName -notmatch "jre" }
    if ($jdkCandidates) {
        $javaExe = $jdkCandidates[0].FullName
    } else {
        $cmdJava = Get-Command java -ErrorAction SilentlyContinue
        if ($cmdJava) { $javaExe = $cmdJava.Source }
    }
}

try {
    $javaVer = & $javaExe -version 2>&1 | Select-String "version"
    Write-Host "[CHECK] Java runtime detected: $javaVer (Path: $javaExe)" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Java is not installed or not in PATH! Please install JDK 21+." -ForegroundColor Red
    exit 1
}

# 5. Build executable bootJar if not present
$jarPath = "$PSScriptRoot\backend\build\libs\backend-4.0.0.jar"
if (-not (Test-Path $jarPath)) {
    Write-Host "[BUILD] Backend executable JAR not found. Packaging via Gradle..." -ForegroundColor Cyan
    & "$PSScriptRoot\gradlew.bat" :backend:bootJar --no-daemon -q
    if (-not (Test-Path $jarPath)) {
        $candidates = Get-ChildItem "$PSScriptRoot\backend\build\libs\*.jar" -ErrorAction SilentlyContinue | Where-Object { $_.Name -notlike "*plain*" }
        if ($candidates) {
            $jarPath = $candidates[0].FullName
        } else {
            Write-Host "[ERROR] Failed to build backend executable JAR!" -ForegroundColor Red
            exit 1
        }
    }
}
Write-Host "[OK] Using backend executable: $jarPath" -ForegroundColor Green

# 6. Start Spring Boot Backend Server
if ($Foreground) {
    Write-Host "[START] Launching SafeBand Backend Service in FOREGROUND on port $port..." -ForegroundColor Cyan
    Write-Host "Press Ctrl+C to stop." -ForegroundColor Yellow
    & $javaExe -Dfile.encoding=UTF-8 -jar $jarPath
    exit $LASTEXITCODE
}

Write-Host "[START] Launching SafeBand Backend Service as detached daemon on port $port..." -ForegroundColor Cyan
$backendLog = "$PSScriptRoot\tools\backend.log"
$backendErr = "$PSScriptRoot\tools\backend_err.log"

if (Test-Path $backendLog) { Clear-Content $backendLog -ErrorAction SilentlyContinue }
if (Test-Path $backendErr) { Clear-Content $backendErr -ErrorAction SilentlyContinue }

# Launch using Win32_Process to decouple completely from the parent console / PowerShell session
$backendCmd = "cmd.exe /c java -Dfile.encoding=UTF-8 -jar backend\build\libs\backend-4.0.0.jar > tools\backend.log 2> tools\backend_err.log"
$createResult = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
    CommandLine = $backendCmd
    CurrentDirectory = $PSScriptRoot
}

if ($createResult.ReturnValue -ne 0) {
    # Fallback to Start-Process if CIM is restricted
    Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "java -Dfile.encoding=UTF-8 -jar backend\build\libs\backend-4.0.0.jar > tools\backend.log 2> tools\backend_err.log" -WorkingDirectory $PSScriptRoot -WindowStyle Hidden
}

Write-Host "[OK] Backend daemon spawned. Awaiting healthy response..." -ForegroundColor Green

# 7. Health Check Polling
$healthy = $false
for ($i = 0; $i -lt 35; $i++) {
    Start-Sleep -Seconds 1
    try {
        $resp = Invoke-RestMethod -Uri "http://localhost:$port/api/v1/health" -Method Get -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($resp -and $resp.status -eq "UP") {
            $healthy = $true
            break
        }
    } catch {}
}

if (-not $healthy) {
    Write-Host "[WARNING] Backend health check timed out. Checking tools/backend.log..." -ForegroundColor Yellow
    if (Test-Path $backendErr) { Get-Content $backendErr -Tail 10 }
    if (Test-Path $backendLog) { Get-Content $backendLog -Tail 15 }
} else {
    Write-Host "[OK] Backend health verified: UP (HTTP 200)" -ForegroundColor Green
}

# 8. Record Real Listening PID
$realBackendPid = $null
try {
    $listenConn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($listenConn) {
        $realBackendPid = $listenConn[0].OwningProcess
        $realBackendPid | Out-File "$PSScriptRoot\.backend.pid" -Encoding ascii
    }
} catch {}

if ($realBackendPid) {
    Write-Host "[OK] Active Backend Service PID: $realBackendPid (Port: $port)" -ForegroundColor Green
} else {
    Write-Host "[WARNING] Could not identify listening PID on port $port." -ForegroundColor Yellow
}

# 9. Start Secure Public Tunnel
$publicUrl = "http://localhost:$port"
if (-not $NoTunnel) {
    $cloudflaredPath = "$PSScriptRoot\tools\cloudflared.exe"
    if (-not (Test-Path $cloudflaredPath)) {
        Write-Host "[TUNNEL] cloudflared.exe not found. Downloading official Cloudflare Tunnel client..." -ForegroundColor Cyan
        try {
            $url = "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe"
            Invoke-WebRequest -Uri $url -OutFile $cloudflaredPath -UseBasicParsing
            Write-Host "[OK] cloudflared.exe downloaded successfully." -ForegroundColor Green
        } catch {
            Write-Host "[WARNING] Could not auto-download cloudflared: $($_.Exception.Message)" -ForegroundColor Yellow
        }
    }

    if (Test-Path $cloudflaredPath) {
        Write-Host "[TUNNEL] Starting Cloudflare secure tunnel..." -ForegroundColor Cyan
        $tunnelLog = "$PSScriptRoot\tools\tunnel.log"
        if (Test-Path $tunnelLog) { Remove-Item $tunnelLog -Force -ErrorAction SilentlyContinue }

        $tunCmd = "cmd.exe /c tools\cloudflared.exe tunnel --url http://localhost:$port 2> tools\tunnel.log"
        $tunResult = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
            CommandLine = $tunCmd
            CurrentDirectory = $PSScriptRoot
        }
        if ($tunResult.ReturnValue -ne 0) {
            Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "tools\cloudflared.exe tunnel --url http://localhost:$port 2> tools\tunnel.log" -WorkingDirectory $PSScriptRoot -WindowStyle Hidden
        }

        # Poll tunnel log for assigned trycloudflare.com URL
        for ($j = 0; $j -lt 25; $j++) {
            Start-Sleep -Seconds 1
            if (Test-Path $tunnelLog) {
                $content = Get-Content $tunnelLog -Raw -ErrorAction SilentlyContinue
                if ($content -match "https://[a-zA-Z0-9-]+\.trycloudflare\.com") {
                    $publicUrl = $matches[0]
                    break
                }
            }
        }

        # Save cloudflared PID
        $cfProcs = Get-Process -Name "cloudflared" -ErrorAction SilentlyContinue
        if ($cfProcs) {
            $cfProcs[0].Id | Out-File "$PSScriptRoot\.tunnel.pid" -Encoding ascii
        }
    }
}

$publicUrl | Out-File "$PSScriptRoot\.public-url" -Encoding ascii

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host " SafeBand Guardian Backend v4.0.0 is LIVE & READY!" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host " Local API URL:       http://localhost:$port" -ForegroundColor White
Write-Host " Public HTTPS URL:    $publicUrl" -ForegroundColor Cyan
Write-Host " WebSocket Endpoint:  $($publicUrl.Replace('https://','wss://').Replace('http://','ws://'))/ws/parent" -ForegroundColor Cyan
Write-Host " Health Endpoint:     $publicUrl/api/v1/health" -ForegroundColor White
if ($realBackendPid) {
    Write-Host " Backend PID:         $realBackendPid" -ForegroundColor Gray
}
Write-Host " Logs:                tools/backend.log, tools/tunnel.log" -ForegroundColor Gray
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "To stop the backend, run: .\stop-backend.bat" -ForegroundColor Yellow
