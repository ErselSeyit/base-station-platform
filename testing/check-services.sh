#!/bin/bash
# ============================================================================
# Service Health Checker
# ============================================================================
# Verifies that all required services are running before starting the simulator
# ============================================================================

set -e

# Credentials from environment
API_USERNAME="${API_USERNAME:-admin}"
API_PASSWORD="${API_PASSWORD:?ERROR: Set API_PASSWORD environment variable}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Configuration
API_GATEWAY="${API_GATEWAY:-http://localhost:30080}"
PROMETHEUS="${PROMETHEUS_URL:-http://localhost:30090}"
GRAFANA="${GRAFANA_URL:-http://localhost:30300}"
ZIPKIN="${ZIPKIN_URL:-http://localhost:30411}"

ALL_GOOD=true

echo -e "${BOLD}${BLUE}================================================${NC}"
echo -e "${BOLD}${CYAN}   Base Station Platform - Service Health Check${NC}"
echo -e "${BOLD}${BLUE}================================================${NC}\n"

# Function to check service
check_service() {
    local name=$1
    local url=$2
    local required=$3

    echo -n "Checking ${BOLD}$name${NC}... "

    if curl -sf "$url" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ UP${NC}"
        return 0
    else
        if [ "$required" = "true" ]; then
            echo -e "${RED}✗ DOWN (REQUIRED)${NC}"
            ALL_GOOD=false
        else
            echo -e "${YELLOW}✗ DOWN (optional)${NC}"
        fi
        return 1
    fi
}

# Check API Gateway
check_service "API Gateway" "$API_GATEWAY/actuator/health" "true"

# Check Authentication
echo -n "Checking ${BOLD}Authentication${NC}... "
TOKEN=$(curl -sf -X POST "$API_GATEWAY/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$API_USERNAME\",\"password\":\"$API_PASSWORD\"}" \
  | jq -r '.token' 2>/dev/null || echo "")

if [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ]; then
    echo -e "${GREEN}✓ Working${NC} (token: ${TOKEN:0:30}...)"
else
    echo -e "${RED}✗ Failed (cannot login)${NC}"
    ALL_GOOD=false
fi

# Check Base Station Service
if [ -n "$TOKEN" ]; then
    echo -n "Checking ${BOLD}Base Station Service${NC}... "
    STATIONS=$(curl -sf -H "Authorization: Bearer $TOKEN" \
        "$API_GATEWAY/api/v1/stations" \
        | jq 'length' 2>/dev/null || echo "0")

    if [ "$STATIONS" -ge 0 ]; then
        echo -e "${GREEN}✓ UP${NC} ($STATIONS stations)"
    else
        echo -e "${RED}✗ DOWN${NC}"
        ALL_GOOD=false
    fi
fi

# Check Monitoring Services (optional)
echo ""
echo -e "${BOLD}Monitoring Stack:${NC}"
check_service "  Prometheus" "$PROMETHEUS/-/healthy" "false"
check_service "  Grafana" "$GRAFANA/api/health" "false"
check_service "  Zipkin" "$ZIPKIN/health" "false"

# Check if running in Docker Compose or Kubernetes
echo ""
echo -e "${BOLD}Deployment Environment:${NC}"

if command -v docker-compose &> /dev/null; then
    echo -n "  Docker Compose: "
    if docker-compose ps > /dev/null 2>&1; then
        RUNNING=$(docker-compose ps --filter "status=running" -q | wc -l)
        TOTAL=$(docker-compose ps -q | wc -l)
        if [ "$RUNNING" -eq "$TOTAL" ] && [ "$TOTAL" -gt 0 ]; then
            echo -e "${GREEN}✓ Running ($RUNNING/$TOTAL services)${NC}"
        else
            echo -e "${YELLOW}⚠ Partial ($RUNNING/$TOTAL services running)${NC}"
        fi
    else
        echo -e "${YELLOW}○ Not detected${NC}"
    fi
fi

if command -v kubectl &> /dev/null; then
    echo -n "  Kubernetes: "
    if kubectl get namespace basestation-platform > /dev/null 2>&1; then
        RUNNING=$(kubectl get pods -n basestation-platform --field-selector=status.phase=Running -o name 2>/dev/null | wc -l)
        TOTAL=$(kubectl get pods -n basestation-platform -o name 2>/dev/null | wc -l)
        if [ "$TOTAL" -gt 0 ]; then
            echo -e "${GREEN}✓ Deployed ($RUNNING/$TOTAL pods running)${NC}"
        else
            echo -e "${YELLOW}○ Namespace exists but no pods${NC}"
        fi
    else
        echo -e "${YELLOW}○ Not detected${NC}"
    fi
fi

# Summary
echo ""
echo -e "${BOLD}${BLUE}================================================${NC}"

if $ALL_GOOD; then
    echo -e "${BOLD}${GREEN}   ✓ ALL REQUIRED SERVICES ARE UP${NC}"
    echo -e "${BOLD}${BLUE}================================================${NC}\n"

    echo -e "${GREEN}You can now run the simulator:${NC}"
    echo -e "  ${CYAN}python3 testing/live-data-simulator.py${NC}\n"

    echo -e "${GREEN}Or run the full E2E test:${NC}"
    echo -e "  ${CYAN}./testing/end-to-end-test.sh 5${NC}\n"

    echo -e "${GREEN}Access the UIs:${NC}"
    echo -e "  Grafana:    ${CYAN}$GRAFANA${NC}"
    echo -e "  Prometheus: ${CYAN}$PROMETHEUS${NC}"
    echo -e "  Zipkin:     ${CYAN}$ZIPKIN${NC}"

    exit 0
else
    echo -e "${BOLD}${RED}   ✗ SOME SERVICES ARE DOWN${NC}"
    echo -e "${BOLD}${BLUE}================================================${NC}\n"

    echo -e "${YELLOW}The platform is not fully operational.${NC}\n"

    echo -e "${BOLD}To start the platform:${NC}\n"

    echo -e "${BOLD}Option 1: Docker Compose${NC}"
    echo -e "  ${CYAN}cd /home/siyu/basestation-platform${NC}"
    echo -e "  ${CYAN}docker-compose up -d${NC}"
    echo -e "  ${CYAN}docker-compose ps${NC}  # Check status\n"

    echo -e "${BOLD}Option 2: Kubernetes${NC}"
    echo -e "  ${CYAN}kubectl create namespace basestation-platform${NC}"
    echo -e "  ${CYAN}kubectl apply -f k8s/secrets.yaml${NC}"
    echo -e "  ${CYAN}kubectl apply -f k8s/persistent-volumes.yaml${NC}"
    echo -e "  ${CYAN}kubectl apply -f k8s/init-configmaps.yaml${NC}"
    echo -e "  ${CYAN}kubectl apply -f k8s/databases.yaml${NC}"
    echo -e "  ${CYAN}kubectl apply -f k8s/app-services.yaml${NC}"
    echo -e "  ${CYAN}kubectl apply -f k8s/monitoring-stack.yaml${NC}"
    echo -e "  ${CYAN}kubectl get pods -n basestation-platform -w${NC}  # Watch startup\n"

    echo -e "${BOLD}Then run this check again:${NC}"
    echo -e "  ${CYAN}./testing/check-services.sh${NC}\n"

    exit 1
fi
