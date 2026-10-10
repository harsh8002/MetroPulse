@echo off
cd /d "%~dp0"
call gradlew.bat assembleDebug
if errorlevel 1 exit /b %ERRORLEVEL%
echo.
echo APK created at:
echo %~dp0app\build\outputs\apk\debug\app-debug.apk
