@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-backend.ps1" %*
if errorlevel 1 (
    echo.
    echo [ERROR] Backend startup encountered an error.
    echo Check tools\backend.log and tools\backend_err.log for details.
    echo.
    pause
    exit /b 1
)
if "%1"=="" (
    echo.
    echo Launcher finished. Backend is running in the background.
    echo Press any key to close this launcher window...
    pause >nul
)
