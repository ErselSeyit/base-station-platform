#!/bin/bash

###############################################################################
# Pre-Start Validation Script
# ENSURES zero zombies before allowing Docker start
# ZERO TOLERANCE - will exit with error if ANY zombie is found
###############################################################################

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Complete list of ALL ports from docker-compose.yml
PORTS=(3000 3001 5434 5435 5436 5672 6379 8080 8084 8085 8086 8087 8090 8762 9090 9411 15672 27018)

echo "🔍 PRE-START VALIDATION: Checking for zombie processes..."
echo "================================================"

ZOMBIE_FOUND=0
ZOMBIE_PORTS=()

# Check each port
for PORT in "${PORTS[@]}"; do
    PID=$(lsof -ti:$PORT 2>/dev/null || true)
    if [ ! -z "$PID" ]; then
        echo -e "${RED}❌ ZOMBIE DETECTED: Port $PORT is in use by PID $PID${NC}"
        ZOMBIE_FOUND=1
        ZOMBIE_PORTS+=($PORT)
    fi
done

# Check for zombie containers
ZOMBIE_CONTAINERS=$(docker ps -aq -f status=exited -f status=dead -f status=created 2>/dev/null || true)
if [ ! -z "$ZOMBIE_CONTAINERS" ]; then
    echo -e "${RED}❌ ZOMBIE CONTAINERS DETECTED${NC}"
    ZOMBIE_FOUND=1
fi

# Report results
echo "================================================"
if [ $ZOMBIE_FOUND -eq 1 ]; then
    echo -e "${RED}❌ VALIDATION FAILED: ZOMBIE PROCESSES DETECTED!${NC}"
    echo ""
    echo "Zombies must be eliminated before starting services."
    echo "Run the cleanup script:"
    echo "  make docker_cleanup"
    echo ""
    echo "Or run the safe restart:"
    echo "  make docker_safe_restart"
    echo ""
    exit 1
else
    echo -e "${GREEN}✅ VALIDATION PASSED: System is clean, ready to start!${NC}"
    exit 0
fi
