<#
.SYNOPSIS
    One-click launcher for PocketServer Cloud Relay + Public Cloudflare Tunnel.
#>

$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "       ⚡ PocketServer - One-Click Relay Launcher          " -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Locate ADB
$adbPath = $null
if (Get-Command adb -ErrorAction SilentlyContinue) {
    $adbPath = "adb"
} elseif (Test-Path "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe") {
    $adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
}

# 2. Setup ADB reverse bridge to phone
if ($adbPath) {
    Write-Host "[1/4] Checking connected Android devices via ADB..." -ForegroundColor Cyan
    $devices = & $adbPath devices | Select-String "device$"
    if ($devices) {
        & $adbPath reverse tcp:8088 tcp:8088 | Out-Null
        Write-Host "  -> Port 8088 bridged to connected Android device! [OK]" -ForegroundColor Green
    } else {
        Write-Host "  -> No USB device detected. If using Wi-Fi, ensure phone reaches your PC IP." -ForegroundColor Yellow
    }
} else {
    Write-Host "[1/4] ADB not found in PATH or standard Android SDK. Skipping USB bridge." -ForegroundColor Yellow
}

# 3. Ensure Cloudflared is present
$toolsDir = Join-Path $PSScriptRoot ".tools"
$cloudflaredPath = Join-Path $toolsDir "cloudflared.exe"

if (-not (Test-Path $cloudflaredPath)) {
    Write-Host "[2/4] Downloading cloudflared standalone tunnel..." -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    Invoke-WebRequest -Uri "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe" -OutFile $cloudflaredPath
    Write-Host "  -> Download complete! [OK]" -ForegroundColor Green
} else {
    Write-Host "[2/4] Cloudflare Tunnel binary ready [OK]" -ForegroundColor Green
}

# 4. Check & Kill any existing process on 8088
$existing = Get-NetTCPConnection -LocalPort 8088 -ErrorAction SilentlyContinue
if ($existing) {
    $pidToKill = $existing.OwningProcess | Select-Object -Unique
    foreach ($p in $pidToKill) {
        if ($p -gt 0) {
            Write-Host "  -> Releasing port 8088 (PID: $p)..." -ForegroundColor DarkGray
            Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
        }
    }
    Start-Sleep -Seconds 1
}

# 5. Start NettyGatewayServer
Write-Host "[3/4] Starting Netty Gateway Relay on port 8088..." -ForegroundColor Cyan
$gradlew = Join-Path $PSScriptRoot "gradlew.bat"
$relayProcess = Start-Process -FilePath $gradlew -ArgumentList ":relay:run", "--args=`"8088 localhost`"" -WorkingDirectory $PSScriptRoot -PassThru -WindowStyle Hidden

# Wait for port 8088 to become active
$attempts = 0
$serverReady = $false
while ($attempts -lt 30) {
    Start-Sleep -Milliseconds 800
    try {
        $resp = Invoke-RestMethod -Uri "http://localhost:8088/" -TimeoutSec 1 -ErrorAction Stop
        if ($resp.status -eq "UP" -or $resp.gateway) {
            $serverReady = $true
            break
        }
    } catch {}
    $attempts++
}

if (-not $serverReady) {
    Write-Host "  -> Failed to start NettyGatewayServer. Check gradle logs." -ForegroundColor Red
    if ($relayProcess) { Stop-Process -Id $relayProcess.Id -Force -ErrorAction SilentlyContinue }
    exit 1
}
Write-Host "  -> Netty Cloud Relay is LIVE on port 8088! [OK]" -ForegroundColor Green

# 6. Start Cloudflare Tunnel and capture public URL
Write-Host "[4/4] Starting Cloudflare Tunnel to the internet..." -ForegroundColor Cyan

$tunnelLog = [System.IO.Path]::GetTempFileName()
$tunnelProcess = Start-Process -FilePath $cloudflaredPath -ArgumentList "tunnel", "--url", "http://localhost:8088" -RedirectStandardError $tunnelLog -PassThru -WindowStyle Hidden

$publicUrl = $null
$timeout = 0
while ($timeout -lt 30) {
    Start-Sleep -Milliseconds 700
    if (Test-Path $tunnelLog) {
        $content = Get-Content $tunnelLog -Raw -ErrorAction SilentlyContinue
        if ($content -match "https://[a-zA-Z0-9-]+\.trycloudflare\.com") {
            $publicUrl = $matches[0]
            break
        }
    }
    $timeout++
}

Write-Host ""
if ($publicUrl) {
    try { Set-Clipboard $publicUrl } catch {}
    Write-Host "==========================================================" -ForegroundColor Green
    Write-Host " 🎉 POCKETSERVER CLOUD RELAY IS READY!" -ForegroundColor Green
    Write-Host "==========================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host " 🌍 Public Internet URL:  $publicUrl" -ForegroundColor Yellow -BackgroundColor Black
    Write-Host " 🔗 Local URL:            http://localhost:8088" -ForegroundColor White
    Write-Host " 📋 (The public link has been copied to your clipboard!)" -ForegroundColor Cyan
    Write-Host ""
    Write-Host " Anyone on mobile data or another Wi-Fi can now visit:" -ForegroundColor Gray
    Write-Host " $publicUrl" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "==========================================================" -ForegroundColor Green
} else {
    Write-Host "Tunnel started, but could not parse URL automatically. Check log: $tunnelLog" -ForegroundColor Yellow
}

Write-Host "Press [Enter] at any time to stop the relay server." -ForegroundColor Magenta
[void][System.Console]::ReadLine()

Write-Host ""
Write-Host "Shutting down relay and tunnel..." -ForegroundColor Yellow
if ($tunnelProcess -and -not $tunnelProcess.HasExited) { Stop-Process -Id $tunnelProcess.Id -Force -ErrorAction SilentlyContinue }
if ($relayProcess -and -not $relayProcess.HasExited) { Stop-Process -Id $relayProcess.Id -Force -ErrorAction SilentlyContinue }
if (Test-Path $tunnelLog) { Remove-Item $tunnelLog -Force -ErrorAction SilentlyContinue }
Write-Host "PocketServer Relay stopped cleanly." -ForegroundColor Green
