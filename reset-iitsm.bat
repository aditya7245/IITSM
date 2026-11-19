@echo off
rem ------------------------------------------------------------------
rem  I-ITSM fresh start (Windows).
rem  Stops the backend, frontend and Docker containers, then DELETES the
rem  database volume and the caught e-mails. On the next start Flyway
rem  rebuilds the schema and the demo users and services, and incident
rem  numbers start again at INC1001.
rem  Usage:  reset-iitsm.bat          asks before wiping
rem          reset-iitsm.bat /y       wipes without asking
rem ------------------------------------------------------------------
setlocal
title I-ITSM reset
cd /d "%~dp0"

echo ==============================================
echo   I-ITSM - reset to a fresh start
echo ==============================================
echo.
echo This deletes ALL incidents, comments, notifications, e-mails and any
echo users you created. The demo accounts come back on the next start.
echo.

if /i not "%~1"=="/y" (
    choice /C YN /M "Wipe all data"
    if errorlevel 2 (echo Cancelled. Nothing was changed. & goto :end)
)

where docker >nul 2>nul || (echo [X] Docker was not found. Install Docker Desktop. & goto :fail)
docker info >nul 2>nul || (echo [X] Docker Desktop is not running. Start it, wait until it is running, then try again. & goto :fail)

echo [1/2] Stopping the backend and the frontend...
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8080,5173 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }"
taskkill /FI "WINDOWTITLE eq I-ITSM backend*" /T /F >nul 2>nul
taskkill /FI "WINDOWTITLE eq I-ITSM frontend*" /T /F >nul 2>nul

echo [2/2] Removing the containers and the database volume...
docker compose down -v --remove-orphans || (echo [X] Docker Compose failed. & goto :fail)

echo.
echo Done. All data is wiped.
echo If the browser is still logged in, it will go back to the login page.
echo.
choice /C YN /M "Start I-ITSM now"
if errorlevel 2 goto :end
call "%~dp0start-iitsm.bat"
exit /b 0

:end
pause
exit /b 0

:fail
echo.
pause
exit /b 1
