@echo off
setlocal
cd /d "%~dp0"
if not exist "gradlew.bat" (
  echo gradlew.bat not found. Place this file in the project root.
  exit /b 1
)
if exist "app\build.gradle.kts" (
  powershell -NoProfile -Command "$c=Get-Content -Raw -LiteralPath 'app\build.gradle.kts'; $d=[regex]::Match($c,'(?s)\bdebug\s*\{(.*?)\r?\n\s*\}\s*\r?\n\s*release\s*\{').Groups[1].Value; if($d -match 'signingConfig\s*=' -or $d -match 'isMinifyEnabled\s*=\s*true'){exit 2}"
  if errorlevel 2 (
    echo Debug must use the standard Android debug key with minification disabled.
    exit /b 1
  )
)
call gradlew.bat :app:assembleDebug --console=plain --no-daemon
if errorlevel 1 exit /b %errorlevel%
echo.
echo APK: app\build\outputs\apk\debug\app-debug.apk
endlocal
