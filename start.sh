#!/bin/bash

echo "========================================"
echo "Base Station Platform - Startup Script"
echo "========================================"
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "[ERROR] Docker is not running. Please start Docker."
    exit 1
fi

echo "[INFO] Docker is running..."
echo ""

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null; then
    echo "[ERROR] docker-compose is not installed or not in PATH."
    exit 1
fi

echo "[INFO] Starting all services..."
echo ""

# Start services
docker-compose up -d

if [ $? -ne 0 ]; then
    echo "[ERROR] Failed to start services."
    exit 1
fi

echo ""
echo "[SUCCESS] Services are starting..."
echo ""
echo "Waiting for services to initialize (this may take 30-60 seconds)..."
sleep 10

echo ""
echo "========================================"
echo "Service URLs:"
echo "========================================"
echo "Eureka Dashboard:    http://localhost:8761"
echo "API Gateway:         http://localhost:8080"
echo "Base Station API:    http://localhost:8081"
echo "Monitoring API:      http://localhost:8082"
echo "Notification API:    http://localhost:8083"
echo "========================================"
echo ""
echo "To view logs: docker-compose logs -f"
echo "To stop services: docker-compose down"
echo ""

# Try to open browser (works on Mac and some Linux distros)
if command -v open &> /dev/null; then
    open http://localhost:8761
elif command -v xdg-open &> /dev/null; then
    xdg-open http://localhost:8761
fi

echo ""
echo "Services are running! Check the Eureka Dashboard to see registered services."
echo ""

