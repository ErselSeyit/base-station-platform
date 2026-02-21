#!/bin/bash

###############################################################################
# Zombie Container Cleanup Script
# Prevents and removes zombie containers and port conflicts
###############################################################################

set -e

echo "🧹 Starting zombie container cleanup..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to kill processes using specific ports
cleanup_ports() {
    echo ""
    echo "📌 Checking for port conflicts..."

    # Complete list of ALL ports from docker-compose.yml
    PORTS=(3000 3001 5434 5435 5436 5672 6379 8080 8084 8085 8086 8087 8090 8762 9090 9411 15672 27018)

    for PORT in "${PORTS[@]}"; do
        PID=$(lsof -ti:$PORT 2>/dev/null || true)
        if [ ! -z "$PID" ]; then
            echo -e "${YELLOW}⚠️  Port $PORT is in use by PID $PID${NC}"
            echo "   Killing process..."
            kill -9 $PID 2>/dev/null || true
            echo -e "${GREEN}✅ Freed port $PORT${NC}"
        fi
    done
}

# Function to remove zombie containers
cleanup_containers() {
    echo ""
    echo "🐳 Cleaning up zombie containers..."

    # Stop all containers from this project
    docker compose down --remove-orphans 2>/dev/null || true

    # Remove any stopped containers
    STOPPED=$(docker ps -aq -f status=exited 2>/dev/null || true)
    if [ ! -z "$STOPPED" ]; then
        echo "   Removing stopped containers..."
        docker rm -f $STOPPED 2>/dev/null || true
        echo -e "${GREEN}✅ Removed stopped containers${NC}"
    fi

    # Remove dangling containers
    DANGLING=$(docker ps -aq -f status=dead -f status=created 2>/dev/null || true)
    if [ ! -z "$DANGLING" ]; then
        echo "   Removing dangling containers..."
        docker rm -f $DANGLING 2>/dev/null || true
        echo -e "${GREEN}✅ Removed dangling containers${NC}"
    fi
}

# Function to cleanup Docker networks
cleanup_networks() {
    echo ""
    echo "🌐 Cleaning up unused networks..."

    docker network prune -f 2>/dev/null || true
    echo -e "${GREEN}✅ Cleaned up networks${NC}"
}

# Function to cleanup volumes (optional, commented out for safety)
cleanup_volumes_optional() {
    echo ""
    read -p "⚠️  Do you want to remove unused volumes? This will DELETE DATA! (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker volume prune -f
        echo -e "${GREEN}✅ Cleaned up volumes${NC}"
    else
        echo "   Skipped volume cleanup"
    fi
}

# Main execution
main() {
    echo "================================================"
    echo "  Docker Zombie Cleanup Script"
    echo "================================================"

    # Kill processes using our ports
    cleanup_ports

    # Clean up containers
    cleanup_containers

    # Clean up networks
    cleanup_networks

    # Optional: Clean up volumes (commented out for safety)
    # cleanup_volumes_optional

    echo ""
    echo "================================================"
    echo -e "${GREEN}✨ Cleanup complete!${NC}"
    echo "================================================"
    echo ""
    echo "You can now start your containers with:"
    echo "  docker compose up -d"
    echo ""
}

# Run main function
main
