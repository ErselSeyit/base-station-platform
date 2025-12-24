#!/bin/bash

echo "========================================"
echo "Base Station Platform - Stop Script"
echo "========================================"
echo ""

echo "[INFO] Stopping all services..."
docker-compose down

if [ $? -ne 0 ]; then
    echo "[ERROR] Failed to stop services."
    exit 1
fi

echo ""
echo "[SUCCESS] All services stopped."
echo ""

