@echo off
echo ========================================
echo Base Station Platform - Startup Script
echo ========================================
echo.

REM Check if Docker is running
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker is not running. Please start Docker Desktop.
    pause
    exit /b 1
)

echo [INFO] Docker is running...
echo.

REM Check if docker-compose is available
docker-compose --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] docker-compose is not installed or not in PATH.
    pause
    exit /b 1
)

echo [INFO] Starting all services...
echo.

REM Start services
docker-compose up -d

if %errorlevel% neq 0 (
    echo [ERROR] Failed to start services.
    pause
    exit /b 1
)

echo.
echo [SUCCESS] Services are starting...
echo.
echo Waiting for services to initialize (this may take 30-60 seconds)...
timeout /t 10 /nobreak >nul

echo.
echo ========================================
echo Service URLs:
echo ========================================
echo Eureka Dashboard:    http://localhost:8761
echo API Gateway:         http://localhost:8080
echo Base Station API:    http://localhost:8081
echo Monitoring API:      http://localhost:8082
echo Notification API:    http://localhost:8083
echo ========================================
echo.
echo To view logs: docker-compose logs -f
echo To stop services: docker-compose down
echo.
echo Press any key to open Eureka Dashboard in browser...
pause >nul

start http://localhost:8761

echo.
echo Services are running! Check the Eureka Dashboard to see registered services.
echo.
pause

