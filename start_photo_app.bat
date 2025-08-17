@echo off
title Sapier - Photo Sender App
echo 📸 Starting Sapier Photo Sender Web App...
echo =========================================
echo.
echo 🌐 This will open a web interface at:
echo    http://localhost:5000
echo.
echo 📁 Running from: %~dp0
echo 🔥 Keep this window open while using the app
echo ❌ Close this window to stop the app
echo.
echo =========================================
echo.

cd /d "%~dp0"
python photo_sender_app.py

echo.
echo 📱 Sapier Photo Sender App has stopped.
echo Press any key to close...
pause >nul
