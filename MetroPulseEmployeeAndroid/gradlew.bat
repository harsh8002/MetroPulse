@echo off
setlocal
set "GRADLE_VERSION=8.9"
set "GRADLE_HOME=%USERPROFILE%\.gradle\metropulse-gradle-%GRADLE_VERSION%"
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  echo MetroPulse needs Gradle %GRADLE_VERSION%. Downloading it once...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $zip=Join-Path $env:TEMP 'gradle-8.9-bin.zip'; $extract=Join-Path $env:TEMP 'metropulse-gradle-extract'; if (Test-Path $extract) { Remove-Item $extract -Recurse -Force }; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' -OutFile $zip; Expand-Archive -Path $zip -DestinationPath $extract -Force; $dest=Join-Path $env:USERPROFILE '.gradle\metropulse-gradle-8.9'; New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null; if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }; Move-Item (Join-Path $extract 'gradle-8.9') $dest; Remove-Item $zip -Force; Remove-Item $extract -Recurse -Force"
  if errorlevel 1 (
    echo Could not download Gradle. Check your internet connection and try again.
    exit /b 1
  )
)
if "%ANDROID_HOME%"=="" if exist "%LOCALAPPDATA%\Android\Sdk\platforms\android-35\android.jar" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if "%ANDROID_HOME%"=="" (
  echo Android SDK not found. Install Android Studio and Android SDK Platform 35 first.
  echo In Android Studio: Tools ^> SDK Manager ^> SDK Platforms ^> Android 15 / API 35.
  exit /b 2
)
"%GRADLE_HOME%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
