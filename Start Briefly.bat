@echo off
setlocal
cd /d "%~dp0"
echo Starting Briefly document summarizer...
where gradle >nul 2>&1
if errorlevel 1 (
  start "Briefly" cmd /c "gradlew.bat bootRun"
) else (
  start "Briefly" cmd /c "gradle bootRun"
)
echo Waiting for the application to start...
for /l %%i in (1,1,60) do (
  powershell -NoProfile -Command "try { $r = Invoke-WebRequest -UseBasicParsing http://localhost:8080 -TimeoutSec 1; if ($r.StatusCode -eq 200) { exit 0 } } catch {}; exit 1" >nul 2>&1
  if not errorlevel 1 goto ready
  timeout /t 2 /nobreak >nul
)
echo The application may still be starting. Open http://localhost:8080 in your browser.
start "" http://localhost:8080
exit /b
:ready
start "" http://localhost:8080
echo Briefly is ready at http://localhost:8080
