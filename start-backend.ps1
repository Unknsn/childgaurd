# SafeBand Guardian v4.0.0 - Unified Backend Startup Script (PowerShell)
param(
    [switch]$NoTunnel,
    [switch]$Foreground
)

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " SafeBand Guardian v4.0.0 - Laptop Backend Starter" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Ensure tools and data directories exist
if (-not (Test-Path "tools")) { New-Item -ItemType Directory -Path "tools" | Out-Null }
if (-not (Test-Path "backend/data")) { New-Item -ItemType Directory -Path "backend/data" | Out-Null }

# 2. Check / Initialize .env file
if (-not (Test-Path ".env")) {
    Write-Host "[SETUP] .env not found. Generating default from .env.example..." -ForegroundColor Yellow
    Copy-Item ".env.example" ".env"
}

# 3. Verify Java runtime
try {
    $javaVer = java -version 2>&1 | Select-String "version"
    Write-Host "[CHECK] Java runtime detected: $javaVer" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Java is not installed or not in PATH! Please install JDK 21+." -ForegroundColor Red
    exit 1
}

# 4. Stop any existing running backend / tunnel instance
if (Test-Path ".backend.pid") {
    $oldPid = Get-Content ".backend.pid" -ErrorAction SilentlyContinue
    if ($oldPid -and (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {
        Write-Host "[INFO] Stopping previous backend instance (PID: $oldPid)..." -ForegroundColor Yellow
        Stop-Process -Id $oldPid -Force -ErrorAction SilentlyContinue
    }
    Remove-Item ".backend.pid" -Force -ErrorAction SilentlyContinue
}

if (Test-Path ".tunnel.pid") {
    $oldTunPid = Get-Content ".tunnel.pid" -ErrorAction SilentlyContinue
    if ($oldTunPid -and (Get-Process -Id $oldTunPid -ErrorAction SilentlyContinue)) {
        Write-Host "[INFO] Stopping previous tunnel instance (PID: $oldTunPid)..." -ForegroundColor Yellow
        Stop-Process -Id $oldTunPid -Force -ErrorAction SilentlyContinue
    }
    Remove-Item ".tunnel.pid" -Force -ErrorAction SilentlyContinue
}

# 5. Build executable bootJar if not present or rebuild requested
$jarPath = "backend/build/libs/backend-4.0.0.jar"
if (-not (Test-Path $jarPath)) {
    Write-Host "[BUILD] Packaging backend executable JAR via Gradle..." -ForegroundColor Cyan
    .\gradlew :backend:bootJar -q
    if (-not (Test-Path $jarPath)) {
        # Fallback to bootRun if packaging didn't produce exact filename
        $candidates = Get-ChildItem "backend/build/libs/*.jar" -ErrorAction SilentlyContinue | Where-Object { $_.Name -notlike "*plain*" }
        if ($candidates) { $jarPath = $candidates[0].FullName }
    }
}

# 6. Start Spring Boot Backend Server
Write-Host "[START] Launching SafeBand Backend Service on port 8080..." -ForegroundColor Cyan
$backendLog = "tools/backend.log"
$backendProcess = Start-Process -FilePath "java" -ArgumentList "-jar", $jarPath -PassThru -NoNewWindow -RedirectStandardOutput $backendLog -RedirectStandardError "tools/backend_err.log"
$backendProcess.Id | Out-File ".backend.pid" -Encoding ascii
Write-Host "[OK] Backend started with PID $($backendProcess.Id). Awaiting healthy response..." -ForegroundColor Green

# 7. Health Check Polling
$healthy = $false
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 1
    try {
        $resp = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/health" -Method Get -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($resp -and $resp.status -eq "UP") {
            $healthy = $true
            break
        }
    } catch {}
}

if (-not $healthy) {
    Write-Host "[WARNING] Backend health check timed out. Check tools/backend.log for details." -ForegroundColor Yellow
} else {
    Write-Host "[OK] Backend health verified: UP (HTTP 200)" -ForegroundColor Green
}

# 8. Start Secure Public Tunnel
$publicUrl = "http://localhost:8080"
if (-not $NoTunnel) {
    $cloudflaredPath = "tools/cloudflared.exe"
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
        $tunnelLog = "tools/tunnel.log"
        if (Test-Path $tunnelLog) { Remove-Item $tunnelLog -Force }
        $tunProcess = Start-Process -FilePath $cloudflaredPath -ArgumentList "tunnel", "--url", "http://localhost:8080" -PassThru -NoNewWindow -RedirectStandardError $tunnelLog
        $tunProcess.Id | Out-File ".tunnel.pid" -Encoding ascii

        # Poll tunnel log for assigned trycloudflare.com URL
        for ($j = 0; $j -lt 20; $j++) {
            Start-Sleep -Seconds 1
            if (Test-Path $tunnelLog) {
                $content = Get-Content $tunnelLog -Raw -ErrorAction SilentlyContinue
                if ($content -match "https://[a-zA-Z0-9-]+\.trycloudflare\.com") {
                    $publicUrl = $matches[0]
                    break
                }
            }
        }
    }
}

$publicUrl | Out-File ".public-url" -Encoding ascii

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host " SafeBand Guardian Backend v4.0.0 is LIVE & READY!" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host " Local API URL:       http://localhost:8080" -ForegroundColor White
Write-Host " Public HTTPS URL:    $publicUrl" -ForegroundColor Cyan
Write-Host " WebSocket Endpoint:  $($publicUrl.Replace('https://','wss://').Replace('http://','ws://'))/ws/parent" -ForegroundColor Cyan
Write-Host " Health Endpoint:     $publicUrl/api/v1/health" -ForegroundColor White
Write-Host " Backend PID:         $($backendProcess.Id)" -ForegroundColor Gray
Write-Host " Logs:                tools/backend.log, tools/tunnel.log" -ForegroundColor Gray
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "To stop the backend, run: .\stop-backend.bat" -ForegroundColor Yellow
