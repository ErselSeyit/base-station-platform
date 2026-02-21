#!/bin/bash

###############################################################################
# Safe Restart Script - ZERO TOLERANCE FOR ZOMBIES
# Properly stops, cleans up, validates, and restarts all services
###############################################################################

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Complete list of ALL ports from docker-compose.yml
PORTS=(3000 3001 5434 5435 5436 5672 6379 8080 8084 8085 8086 8087 8090 8762 9090 9411 15672 27018)

echo "🔄 Starting ZERO-TOLERANCE safe restart process..."
echo ""

# Step 1: Stop all services gracefully with multiple attempts
echo -e "${BLUE}Step 1/6:${NC} Stopping all services..."
docker compose down --remove-orphans 2>/dev/null || true
sleep 2
docker compose down --remove-orphans -v 2>/dev/null || true
echo -e "${GREEN}✅ Services stopped${NC}"
echo ""

# Step 2: Kill ALL docker-proxy processes (root cause of zombie ports)
echo -e "${BLUE}Step 2/6:${NC} Killing docker-proxy processes..."
sudo pkill -9 docker-proxy 2>/dev/null || true
sleep 1
echo -e "${GREEN}✅ Docker proxies killed${NC}"
echo ""

# Step 3: Kill any processes using our ports with EXTREME PREJUDICE
echo -e "${BLUE}Step 3/6:${NC} Freeing up ALL ports..."
for PORT in "${PORTS[@]}"; do
    PID=$(lsof -ti:$PORT 2>/dev/null || true)
    if [ ! -z "$PID" ]; then
        echo "   Killing process on port $PORT (PID: $PID)"
        kill -9 $PID 2>/dev/null || true
        sudo kill -9 $PID 2>/dev/null || true
    fi
done
echo -e "${GREEN}✅ All ports freed${NC}"
echo ""

# Step 4: Aggressive Docker cleanup
echo -e "${BLUE}Step 4/6:${NC} Aggressive Docker cleanup..."
docker container prune -f 2>/dev/null || true
docker network prune -f 2>/dev/null || true
docker system prune -f 2>/dev/null || true
echo -e "${GREEN}✅ Cleanup complete${NC}"
echo ""

# Step 5: VALIDATION - Ensure NO zombies exist
echo -e "${BLUE}Step 5/6:${NC} Validating NO zombie processes exist..."
ZOMBIE_FOUND=0
for PORT in "${PORTS[@]}"; do
    if lsof -ti:$PORT >/dev/null 2>&1; then
        echo -e "${RED}❌ CRITICAL: Port $PORT is still in use!${NC}"
        ZOMBIE_FOUND=1
    fi
done

if [ $ZOMBIE_FOUND -eq 1 ]; then
    echo -e "${RED}❌ VALIDATION FAILED: Zombie processes detected!${NC}"
    echo -e "${YELLOW}Please restart Docker daemon manually: sudo systemctl restart docker${NC}"
    exit 1
fi
echo -e "${GREEN}✅ VALIDATION PASSED: No zombies detected${NC}"
echo ""

# Step 6: Start services
echo -e "${BLUE}Step 6/6:${NC} Starting all services..."
docker compose up -d

# Wait for services to initialize
echo ""
echo "⏳ Waiting for services to initialize..."
sleep 10

# Check service status
echo ""
echo "📊 Service Status:"
docker compose ps
echo ""

# Final validation
echo "🔍 Final validation..."
FAILED_SERVICES=$(docker compose ps --format json | jq -r 'select(.State != "running") | .Service' 2>/dev/null || true)
if [ ! -z "$FAILED_SERVICES" ]; then
    echo -e "${RED}⚠️  Some services failed to start:${NC}"
    echo "$FAILED_SERVICES"
    exit 1
fi

echo -e "${GREEN}✨ RESTART COMPLETE - ALL SERVICES HEALTHY!${NC}"
echo ""
echo "Frontend: http://localhost:3000"
echo "API Gateway: http://localhost:8080"
echo "Eureka: http://localhost:8762"
echo "Grafana: http://localhost:9090"
echo ""
