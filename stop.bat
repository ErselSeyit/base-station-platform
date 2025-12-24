@echo off
echo ========================================
echo Base Station Platform - Stop Script
echo ========================================
echo.

echo [INFO] Stopping all services...
docker-compose down

if %errorlevel% neq 0 (
    echo [ERROR] Failed to stop services.
    pause
    exit /b 1
)

echo.
echo [SUCCESS] All services stopped.
echo.
pause

