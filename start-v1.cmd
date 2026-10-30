@echo off
setlocal

rem ============================================================
rem  Japanese Expression Learning System V1 - launcher
rem
rem  Starts the packaged V1 (Spring Boot JAR) and opens the browser.
rem  This script does NOT build anything. Build first (see README.md).
rem  Any arguments are passed to Spring Boot as they are.
rem
rem  Note: this file is intentionally ASCII only, because cmd.exe can
rem  misread multibyte (UTF-8) lines in batch files.
rem ============================================================

rem Resolve paths from the location of this file (works from any current directory)
set "BACKEND_DIR=%~dp0backend"
set "TARGET_DIR=%BACKEND_DIR%\target"
set "APP_URL=http://localhost:8080/"

rem Find the executable JAR in backend\target (no fixed file name, so a version change is OK).
rem Candidates: names ending with ".jar", excluding *.jar.original and
rem non-executable jars (-sources / -javadoc / -tests / -plain).
rem The JAR is started only when exactly one candidate is found.
set "JAR_LIST=dir /b /a-d "%TARGET_DIR%\*.jar" 2^>nul ^| findstr /i /e /c:".jar" ^| findstr /v /i /e /c:"-sources.jar" /c:"-javadoc.jar" /c:"-tests.jar" /c:"-plain.jar""
set "JAR_FILE="
set "JAR_COUNT=0"
for /f "delims=" %%F in ('%JAR_LIST%') do (
  set "JAR_FILE=%TARGET_DIR%\%%F"
  set /a JAR_COUNT+=1
)

if %JAR_COUNT% EQU 0 (
  echo [ERROR] No executable JAR file found in:
  echo   "%TARGET_DIR%"
  echo Build the frontend and package the backend first. See README.md.
  pause
  exit /b 1
)

if %JAR_COUNT% GTR 1 (
  echo [ERROR] More than one JAR file found in:
  echo   "%TARGET_DIR%"
  for /f "delims=" %%F in ('%JAR_LIST%') do echo   - %%F
  echo Run "mvnw.cmd clean package" in the backend folder so that only one JAR remains.
  pause
  exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] "java" command not found. Install Java 17 and add it to PATH.
  pause
  exit /b 1
)

rem The SQLite DB path (data/japanese_expression_learning.db) is relative to "backend"
cd /d "%BACKEND_DIR%"

echo Starting Japanese Expression Learning System V1 ...
echo Using: %JAR_FILE%
echo The browser will open %APP_URL% shortly.
echo To stop the application, press Ctrl+C or close this window.
echo.

rem Wait a little for Spring Boot to start, then open the default browser (runs in background)
start "" /b powershell -NoProfile -Command "Start-Sleep -Seconds 8; Start-Process '%APP_URL%'"

java -jar "%JAR_FILE%" %*
