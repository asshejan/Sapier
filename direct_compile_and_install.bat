@echo off
echo Sapier Direct Compile and Install Script
echo =======================================

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
set PACKAGE_NAME=com.example.sapier
set MAIN_ACTIVITY=com.example.sapier.MainActivity

REM Check if we need to build the APK
if "%1"=="build" (
    echo Attempting to build with minimal resource processing...
    
    REM Try to build with a special flag to skip R.jar generation
    call gradlew assembleDebug "-PskipResourceProcessing=true" --info
    
    if %ERRORLEVEL% NEQ 0 (
        echo Warning: Build with skipResourceProcessing failed.
        echo Trying alternative build approach...
        
        REM Try to build just the Java/Kotlin files without resource processing
        call gradlew compileDebugKotlin --info
        
        if %ERRORLEVEL% NEQ 0 (
            echo Warning: Compilation failed, but continuing with installation of last built APK...
        )
    )
)

REM Check for existing APK
set APK_PATH=app\build\outputs\apk\debug\app-debug.apk
if not exist %APK_PATH% (
    echo APK not found at %APK_PATH%
    echo Checking for previously installed version...
    
    REM Check if app is already installed
    adb shell pm list packages | findstr %PACKAGE_NAME% > nul
    if %ERRORLEVEL% NEQ 0 (
        echo Error: App not installed and no APK found to install.
        echo You may need to build the app first with a clean environment.
        exit /b 1
    ) else (
        echo App already installed. Proceeding to launch...
    )
) else (
    echo Installing APK...
    adb install -r %APK_PATH%
    if %ERRORLEVEL% NEQ 0 (
        echo Error: Failed to install APK.
        exit /b 1
    )
)

echo Launching app...
adb shell am start -n %PACKAGE_NAME%/%MAIN_ACTIVITY%
if %ERRORLEVEL% NEQ 0 (
    echo Error: Failed to launch app.
    exit /b 1
)

echo Success! App launched.

REM Offer to monitor logcat
echo.
echo Would you like to monitor app logs? (Y/N)
set /p MONITOR_LOGS=

if /i "%MONITOR_LOGS%"=="Y" (
    echo Showing logs for %PACKAGE_NAME%...
    echo Press Ctrl+C to stop monitoring.
    adb logcat -v time | findstr %PACKAGE_NAME%
)