@echo off
echo Sapier Direct Install and Run Script
echo ===================================

REM Check if ADB is available
adb version > nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Error: ADB not found in PATH. Please ensure Android SDK platform-tools are in your PATH.
    exit /b 1
)

echo Checking device connection...
adb devices | findstr "device$" > nul
if %ERRORLEVEL% NEQ 0 (
    echo Error: No device connected or device not authorized.
    echo Please connect a device and ensure USB debugging is enabled.
    exit /b 1
)

echo Device connected successfully.

REM Set variables for paths
set APK_PATH=app\build\outputs\apk\debug\app-debug.apk
set PACKAGE_NAME=com.example.sapier
set MAIN_ACTIVITY=com.example.sapier.MainActivity

REM Check if we need to build the APK
if "%1"=="build" (
    echo Building APK with minimal resource processing...
    call gradlew assembleDebug -x processDebugResources --info
    if %ERRORLEVEL% NEQ 0 (
        echo Warning: Build failed, but continuing with installation of last built APK...
    )
)

REM Check if APK exists
if not exist %APK_PATH% (
    echo Error: APK not found at %APK_PATH%
    echo You may need to build the app first with: %0 build
    exit /b 1
)

echo Installing APK...
adb install -r %APK_PATH%
if %ERRORLEVEL% NEQ 0 (
    echo Error: Failed to install APK.
    exit /b 1
)

echo Launching app...
adb shell am start -n %PACKAGE_NAME%/%MAIN_ACTIVITY%
if %ERRORLEVEL% NEQ 0 (
    echo Error: Failed to launch app.
    exit /b 1
)

echo Success! App installed and launched.