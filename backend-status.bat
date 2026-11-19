@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0backend-status.ps1" %*
if "%1"=="" (
    pause
)
