@echo off
echo Sapier Development Helper
echo =======================

if "%1"=="" (
    echo Usage: dev [command]
    echo.
    echo Available commands:
    echo   run       - Install and run the app
    echo   build     - Build the app with minimal resource processing
    echo   clean     - Force clean the build directory
    echo   restart   - Restart the connected device
    echo   kill      - Kill all Java processes
    echo   logs      - Show app logs
    echo   help      - Show this help message
    exit /b 0
)

if "%1"=="run" (
    echo Running direct_install_and_run.bat...
    call direct_install_and_run.bat
    exit /b %ERRORLEVEL%
)

if "%1"=="build" (
    echo Building with minimal resource processing...
    call direct_compile_and_install.bat build
    exit /b %ERRORLEVEL%
)

if "%1"=="clean" (
    echo Force cleaning build directory...
    call gradlew forceClean --info
    exit /b %ERRORLEVEL%
)

if "%1"=="restart" (
    echo Restarting connected device...
    adb reboot
    echo Waiting for device to restart...
    adb wait-for-device
    echo Device restarted.
    exit /b 0
)

if "%1"=="kill" (
    echo Killing all Java processes...
    taskkill /F /IM java.exe
    echo Java processes terminated.
    exit /b 0
)

if "%1"=="logs" (
    echo Showing logs for com.example.sapier...
    echo Press Ctrl+C to stop monitoring.
    adb logcat -v time | findstr com.example.sapier
    exit /b 0
)

if "%1"=="help" (
    echo Usage: dev [command]
    echo.
    echo Available commands:
    echo   run       - Install and run the app
    echo   build     - Build the app with minimal resource processing
    echo   clean     - Force clean the build directory
    echo   restart   - Restart the connected device
    echo   kill      - Kill all Java processes
    echo   logs      - Show app logs
    echo   help      - Show this help message
    exit /b 0
) else (
    echo Unknown command: %1
    echo Run 'dev help' for usage information.
    exit /b 1
)