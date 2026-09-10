@echo off
title BusGo - Bus Reservation System Server
echo ==================================================
echo Compiling BusGo Java files...
echo ==================================================
javac -d out src/*.java
if %errorlevel% neq 0 (
    echo Compilation failed! Please ensure JDK 17+ is installed.
    pause
    exit /b %errorlevel%
)
echo.
echo Starting BusGo Server...
echo Open your browser at http://localhost:8080/
echo Press Ctrl+C in this window to stop the server.
echo ==================================================
echo.
java -cp out BusReservationSystem
pause
