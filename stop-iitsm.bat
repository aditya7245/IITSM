@echo off
rem Stops the I-ITSM backend, frontend and Docker containers. The database data is kept.
cd /d "%~dp0"
echo Stopping I-ITSM...
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8080,5173 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }"
taskkill /FI "WINDOWTITLE eq I-ITSM backend*" /T /F >nul 2>nul
taskkill /FI "WINDOWTITLE eq I-ITSM frontend*" /T /F >nul 2>nul
docker compose stop
echo.
echo Stopped. The data is kept. To wipe all incidents and users you created, run:  reset-iitsm.bat
pause
