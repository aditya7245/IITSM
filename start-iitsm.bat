@echo off
rem ------------------------------------------------------------------
rem  I-ITSM one-click start (Windows).
rem  Double-click for normal mode, or use start-iitsm-demo.bat for the
rem  fast demo timings (SLA deadlines and escalations in seconds).
rem  Starts: database + Mailpit (Docker), backend, frontend, then opens
rem  the app in the browser. Stop everything with stop-iitsm.bat.
rem  Readiness check: any answer from the backend counts (the login-protected
rem  address answers 401, which is fine), so it never depends on the mail server.
rem ------------------------------------------------------------------
setlocal
title I-ITSM launcher
cd /d "%~dp0"

set "MODE=normal"
if /i "%~1"=="demo" set "MODE=demo"

echo ==============================================
echo   I-ITSM - starting everything (%MODE% mode)
echo ==============================================
echo.

where docker >nul 2>nul || (echo [X] Docker was not found. Install Docker Desktop. & goto :fail)
where mvn >nul 2>nul || (echo [X] Maven ^(mvn^) was not found on the PATH. & goto :fail)
where npm >nul 2>nul || (echo [X] Node.js ^(npm^) was not found. Install Node 18 or newer. & goto :fail)
docker info >nul 2>nul || (echo [X] Docker Desktop is not running. Start it, wait until it is running, then try again. & goto :fail)

echo [1/4] Database and mail catcher ^(Docker^)...
docker compose up -d --wait || (echo [X] Docker Compose failed. & goto :fail)

echo [2/4] Backend ^(a new window; the first start takes a while^)...
if "%MODE%"=="demo" (set "SPRING_PROFILES_ACTIVE=dev,demo") else (set "SPRING_PROFILES_ACTIVE=dev")
start "I-ITSM backend" /D "%~dp0backend" cmd /k "mvn spring-boot:run"

echo [3/4] Frontend ^(a new window; the first start installs packages^)...
start "I-ITSM frontend" /D "%~dp0frontend" cmd /k "if not exist node_modules (npm install) & npm run dev"

echo [4/4] Waiting until the backend and the frontend answer ^(up to about 5 minutes^)...
powershell -NoProfile -Command "foreach($u in 'http://localhost:8080/api/auth/me','http://localhost:5173'){ $ok=$false; for($i=0;$i -lt 150;$i++){ try{ Invoke-WebRequest -UseBasicParsing $u -TimeoutSec 5 | Out-Null; $ok=$true; break }catch{ if($_.Exception.Response){ $ok=$true; break }; Start-Sleep 2 } }; if(-not $ok){ Write-Host ('Gave up waiting for ' + $u); exit 1 } }"
if errorlevel 1 (
    echo [X] Something did not start. Look at the "I-ITSM backend" and "I-ITSM frontend" windows for the error.
    goto :fail
)

echo.
echo Ready.
echo   App:     http://localhost:5173
echo   Swagger: http://localhost:8080/swagger-ui.html
echo   Mailpit: http://localhost:8025
echo   Log in as admin@iitsm.local, tech1@iitsm.local or emp1@iitsm.local, password Password@123
echo.
start "" "http://localhost:5173"
if "%MODE%"=="demo" (
    start "" "http://localhost:8080/swagger-ui.html"
    start "" "http://localhost:8025"
)
echo Run stop-iitsm.bat to stop everything. You can close this window.
pause
exit /b 0

:fail
echo.
pause
exit /b 1
