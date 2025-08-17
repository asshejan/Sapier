@echo off
title Sapier - Photo Sender Launch
echo.
echo ████████████████████████████████████████████████████████████████████████████████
echo ██                                                                            ██
echo ██                    🚀 SAPIER PHOTO SENDER APP 🚀                          ██
echo ██                                                                            ██
echo ████████████████████████████████████████████████████████████████████████████████
echo.
echo 📁 Project Directory: %CD%
echo 🕐 Launch Time: %DATE% %TIME%
echo.
echo ═══════════════════════════════════════════════════════════════════════════════
echo   Step 1: Testing Telegram Connection...
echo ═══════════════════════════════════════════════════════════════════════════════
echo.

cd /d "%~dp0"

rem Test connection first
python test_connection.py

if %errorlevel% neq 0 (
    echo.
    echo ❌ CONNECTION TEST FAILED!
    echo 🔧 Please check your .env file and try again.
    echo.
    echo Common issues:
    echo - Missing .env file in Sapier directory
    echo - Wrong TELEGRAM_BOT_TOKEN
    echo - Wrong TELEGRAM_ADMIN_USER_ID
    echo.
    echo Press any key to exit...
    pause >nul
    exit /b 1
)

echo.
echo ✅ Connection test passed!
echo.
echo ═══════════════════════════════════════════════════════════════════════════════
echo   Step 2: Starting Photo Sender Web App...
echo ═══════════════════════════════════════════════════════════════════════════════
echo.
echo 🌐 Web interface will open at: http://localhost:5000
echo 📱 Photos will be sent to your Telegram bot
echo.
echo 🔥 Keep this window open while using the app
echo ❌ Close this window to stop the app
echo.
echo ═══════════════════════════════════════════════════════════════════════════════
echo.
timeout /t 3 >nul

python photo_sender_app.py

echo.
echo ████████████████████████████████████████████████████████████████████████████████
echo ██                                                                            ██
echo ██                    📱 SAPIER PHOTO SENDER STOPPED                         ██
echo ██                                                                            ██
echo ████████████████████████████████████████████████████████████████████████████████
echo.
echo Press any key to close...
pause >nul
