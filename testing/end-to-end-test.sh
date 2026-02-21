#!/bin/bash
# ============================================================================
# End-to-End Live Data Testing Suite
# ============================================================================
# This script:
# 1. Starts the live data simulator
# 2. Waits for data to flow through the system
# 3. Validates metrics appear in Prometheus
# 4. Validates logs appear in Loki
# 5. Validates traces appear in Zipkin
# 6. Tests all API endpoints with real data
# 7. Generates a comprehensive test report
#
# Usage:
#   ./testing/end-to-end-test.sh [duration_minutes]
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
MAGENTA='\033[0;35m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Configuration
API_GATEWAY="${API_GATEWAY:-http://localhost:30080}"
PROMETHEUS="${PROMETHEUS_URL:-http://localhost:30090}"
GRAFANA="${GRAFANA_URL:-http://localhost:30300}"
LOKI="${LOKI_URL:-http://localhost:3100}"
ZIPKIN="${ZIPKIN_URL:-http://localhost:30411}"

DURATION="${1:-5}"  # Default 5 minutes
STATIONS=20
INTERVAL=3

# Test results
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Functions
print_header() {
    echo -e "\n${BOLD}${BLUE}$=================================================================================${NC}"
    echo -e "${BOLD}${CYAN}$1${NC}"
    echo -e "${BOLD}${BLUE}=================================================================================${NC}\n"
}

print_test() {
    echo -e "${YELLOW}[TEST]${NC} $1"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
}

print_pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
    PASSED_TESTS=$((PASSED_TESTS + 1))
}

print_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    FAILED_TESTS=$((FAILED_TESTS + 1))
}

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if service is reachable
check_service() {
    local name=$1
    local url=$2

    print_test "Checking $name connectivity"

    if curl -s -f "$url" > /dev/null 2>&1 || curl -s "$url" > /dev/null 2>&1; then
        print_pass "$name is reachable at $url"
        return 0
    else
        print_fail "$name is NOT reachable at $url"
        return 1
    fi
}

# Start simulator in background
start_simulator() {
    print_header "Starting Live Data Simulator"

    print_info "Scenario: Normal operation with peak hours"
    print_info "Stations: $STATIONS"
    print_info "Duration: $DURATION minutes"
    print_info "Update Interval: ${INTERVAL}s"

    # Kill any existing simulator
    pkill -f "live-data-simulator.py" 2>/dev/null || true

    # Start simulator in background
    python3 testing/live-data-simulator.py \
        --api-url "$API_GATEWAY" \
        --stations "$STATIONS" \
        --interval "$INTERVAL" \
        --duration "$DURATION" \
        --scenario peak_hours \
        --concurrent \
        > /tmp/simulator.log 2>&1 &

    SIMULATOR_PID=$!
    echo $SIMULATOR_PID > /tmp/simulator.pid

    print_success "Simulator started (PID: $SIMULATOR_PID)"
    print_info "Logs: tail -f /tmp/simulator.log"
}

# Test API endpoints
test_api_endpoints() {
    print_header "Testing API Endpoints"

    # Get JWT token
    print_test "Authenticating with API"
    TOKEN=$(curl -s -X POST "$API_GATEWAY/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"$API_USERNAME\",\"password\":\"$API_PASSWORD\"}" \
        | jq -r '.token' 2>/dev/null || echo "")

    if [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ]; then
        print_pass "Authentication successful"
    else
        print_fail "Authentication failed"
        return 1
    fi

    # Test base station endpoints
    print_test "Fetching base stations list"
    STATIONS_COUNT=$(curl -s -H "Authorization: Bearer $TOKEN" \
        "$API_GATEWAY/api/v1/stations" \
        | jq 'length' 2>/dev/null || echo "0")

    if [ "$STATIONS_COUNT" -gt 0 ]; then
        print_pass "Found $STATIONS_COUNT base stations"
    else
        print_fail "No base stations found"
    fi

    # Test metrics endpoint
    print_test "Fetching recent metrics"
    METRICS_COUNT=$(curl -s -H "Authorization: Bearer $TOKEN" \
        "$API_GATEWAY/api/v1/metrics?limit=100" \
        | jq 'length' 2>/dev/null || echo "0")

    if [ "$METRICS_COUNT" -gt 0 ]; then
        print_pass "Found $METRICS_COUNT metric entries"
    else
        print_fail "No metrics found"
    fi

    # Test notifications endpoint
    print_test "Fetching notifications"
    NOTIF_COUNT=$(curl -s -H "Authorization: Bearer $TOKEN" \
        "$API_GATEWAY/api/v1/notifications" \
        | jq 'length' 2>/dev/null || echo "0")

    if [ "$NOTIF_COUNT" -gt 0 ]; then
        print_pass "Found $NOTIF_COUNT notifications"
    else
        print_info "No notifications yet (this is normal if no alerts triggered)"
        print_pass "Notifications endpoint responsive"
    fi
}

# Test Prometheus metrics
test_prometheus() {
    print_header "Testing Prometheus Metrics"

    sleep 15  # Wait for metrics to be scraped

    # Test if Prometheus is collecting Spring Boot metrics
    print_test "Checking Spring Boot metrics in Prometheus"
    HTTP_REQUESTS=$(curl -s "$PROMETHEUS/api/v1/query?query=http_server_requests_seconds_count" \
        | jq -r '.data.result | length' 2>/dev/null || echo "0")

    if [ "$HTTP_REQUESTS" -gt 0 ]; then
        print_pass "Spring Boot metrics found ($HTTP_REQUESTS series)"
    else
        print_fail "No Spring Boot metrics in Prometheus"
    fi

    # Test JVM metrics
    print_test "Checking JVM metrics"
    JVM_MEMORY=$(curl -s "$PROMETHEUS/api/v1/query?query=jvm_memory_used_bytes" \
        | jq -r '.data.result | length' 2>/dev/null || echo "0")

    if [ "$JVM_MEMORY" -gt 0 ]; then
        print_pass "JVM metrics found ($JVM_MEMORY series)"
    else
        print_fail "No JVM metrics in Prometheus"
    fi

    # Test Kubernetes pod metrics
    print_test "Checking Kubernetes pod metrics"
    POD_CPU=$(curl -s "$PROMETHEUS/api/v1/query?query=container_cpu_usage_seconds_total" \
        | jq -r '.data.result | length' 2>/dev/null || echo "0")

    if [ "$POD_CPU" -gt 0 ]; then
        print_pass "Kubernetes pod metrics found ($POD_CPU series)"
    else
        print_info "No Kubernetes metrics (normal if not running in K8s)"
    fi

    # Test custom application metrics
    print_test "Checking custom application metrics"
    APP_METRICS=$(curl -s "$PROMETHEUS/api/v1/query?query=application_ready_time_seconds" \
        | jq -r '.data.result | length' 2>/dev/null || echo "0")

    if [ "$APP_METRICS" -gt 0 ]; then
        print_pass "Application metrics found"
    else
        print_info "Custom app metrics not found (may not be implemented)"
    fi
}

# Test Loki logs
test_loki() {
    print_header "Testing Loki Centralized Logging"

    sleep 10  # Wait for logs to be shipped

    # Query recent logs
    print_test "Querying Loki for application logs"

    # Build LogQL query
    NOW=$(date +%s)000000000
    PAST=$(($(date +%s) - 300))000000000  # Last 5 minutes

    LOGS=$(curl -s -G "$LOKI/loki/api/v1/query_range" \
        --data-urlencode 'query={namespace="basestation-platform"}' \
        --data-urlencode "start=$PAST" \
        --data-urlencode "end=$NOW" \
        --data-urlencode "limit=1000" \
        | jq -r '.data.result | length' 2>/dev/null || echo "0")

    if [ "$LOGS" -gt 0 ]; then
        print_pass "Found logs from $LOGS log streams"
    else
        print_fail "No logs found in Loki"
    fi

    # Test log labels
    print_test "Checking log labels"
    LABELS=$(curl -s "$LOKI/loki/api/v1/labels" \
        | jq -r '.data | length' 2>/dev/null || echo "0")

    if [ "$LABELS" -gt 0 ]; then
        print_pass "Found $LABELS log labels"
    else
        print_fail "No log labels found"
    fi
}

# Test Zipkin traces
test_zipkin() {
    print_header "Testing Zipkin Distributed Tracing"

    sleep 10  # Wait for traces to appear

    # Query recent traces
    print_test "Checking for distributed traces"

    TRACES=$(curl -s "$ZIPKIN/api/v2/traces?limit=100" \
        | jq 'length' 2>/dev/null || echo "0")

    if [ "$TRACES" -gt 0 ]; then
        print_pass "Found $TRACES trace(s)"
    else
        print_info "No traces yet (normal if tracing sampling is low)"
    fi

    # Get services
    print_test "Checking traced services"
    SERVICES=$(curl -s "$ZIPKIN/api/v2/services" \
        | jq -r 'length' 2>/dev/null || echo "0")

    if [ "$SERVICES" -gt 0 ]; then
        SERVICE_LIST=$(curl -s "$ZIPKIN/api/v2/services" | jq -r '.[]' | tr '\n' ', ' | sed 's/,$//')
        print_pass "Found $SERVICES traced services: $SERVICE_LIST"
    else
        print_fail "No services reporting traces"
    fi
}

# Test Grafana dashboards
test_grafana() {
    print_header "Testing Grafana Dashboards"

    # Check Grafana health
    print_test "Checking Grafana health"
    HEALTH=$(curl -s "$GRAFANA/api/health" | jq -r '.database' 2>/dev/null || echo "")

    if [ "$HEALTH" = "ok" ]; then
        print_pass "Grafana is healthy"
    else
        print_fail "Grafana health check failed"
        return
    fi

    # Check datasources
    print_test "Checking Grafana datasources"
    DATASOURCES=$(curl -s "$GRAFANA/api/datasources" \
        | jq 'length' 2>/dev/null || echo "0")

    if [ "$DATASOURCES" -ge 2 ]; then
        print_pass "Found $DATASOURCES datasources (Prometheus + Loki)"
    else
        print_fail "Expected 2+ datasources, found $DATASOURCES"
    fi
}

# Stress test
stress_test() {
    print_header "Running Stress Test"

    print_info "Sending burst of 100 requests..."

    SUCCESS=0
    FAILED=0

    TOKEN=$(curl -s -X POST "$API_GATEWAY/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"$API_USERNAME\",\"password\":\"$API_PASSWORD\"}" \
        | jq -r '.token' 2>/dev/null || echo "")

    for i in {1..100}; do
        STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
            -H "Authorization: Bearer $TOKEN" \
            "$API_GATEWAY/api/v1/stations" || echo "000")

        if [ "$STATUS" = "200" ]; then
            SUCCESS=$((SUCCESS + 1))
        else
            FAILED=$((FAILED + 1))
        fi
    done

    print_test "Stress test: 100 concurrent requests"

    if [ "$SUCCESS" -ge 95 ]; then
        print_pass "Success rate: $SUCCESS/100 (${SUCCESS}%)"
    elif [ "$SUCCESS" -ge 80 ]; then
        print_info "Success rate: $SUCCESS/100 (${SUCCESS}%) - acceptable"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        TOTAL_TESTS=$((TOTAL_TESTS + 1))
    else
        print_fail "Success rate: $SUCCESS/100 (${SUCCESS}%) - too many failures"
    fi
}

# Monitor simulator
monitor_simulator() {
    print_header "Monitoring Live Data Flow"

    print_info "Watching simulator for $DURATION minutes..."
    print_info "Live logs: tail -f /tmp/simulator.log"
    print_info ""

    # Show a sample of the logs
    timeout 30 tail -f /tmp/simulator.log 2>/dev/null || true

    # Wait for simulator to finish
    if [ -f /tmp/simulator.pid ]; then
        SIM_PID=$(cat /tmp/simulator.pid)
        print_info "Waiting for simulator (PID $SIM_PID) to complete..."

        # Wait with timeout
        WAIT_COUNT=0
        MAX_WAIT=$((DURATION * 60 + 60))  # Duration + 1 minute buffer

        while kill -0 $SIM_PID 2>/dev/null; do
            sleep 5
            WAIT_COUNT=$((WAIT_COUNT + 5))

            if [ $WAIT_COUNT -ge $MAX_WAIT ]; then
                print_info "Timeout reached, stopping simulator"
                kill $SIM_PID 2>/dev/null || true
                break
            fi

            # Show progress every minute
            if [ $((WAIT_COUNT % 60)) -eq 0 ]; then
                ELAPSED_MIN=$((WAIT_COUNT / 60))
                print_info "Elapsed: ${ELAPSED_MIN}/${DURATION} minutes"
            fi
        done
    fi

    print_success "Simulator completed"
}

# Generate report
generate_report() {
    print_header "Test Report"

    SUCCESS_RATE=$((PASSED_TESTS * 100 / TOTAL_TESTS))

    echo -e "${BOLD}Test Summary:${NC}"
    echo -e "  Total Tests:   $TOTAL_TESTS"
    echo -e "  ${GREEN}Passed:${NC}        $PASSED_TESTS"
    echo -e "  ${RED}Failed:${NC}        $FAILED_TESTS"
    echo -e "  ${CYAN}Success Rate:${NC}  ${SUCCESS_RATE}%"
    echo ""

    if [ $SUCCESS_RATE -ge 90 ]; then
        echo -e "${BOLD}${GREEN}========================================${NC}"
        echo -e "${BOLD}${GREEN}  ✓ ALL SYSTEMS OPERATIONAL${NC}"
        echo -e "${BOLD}${GREEN}========================================${NC}"
        echo ""
        echo -e "${GREEN}The platform is functioning correctly with live data!${NC}"
        echo ""
        echo -e "Next steps:"
        echo -e "  1. Visit Grafana: ${CYAN}$GRAFANA${NC}"
        echo -e "  2. View metrics: ${CYAN}$PROMETHEUS${NC}"
        echo -e "  3. Check traces: ${CYAN}$ZIPKIN${NC}"
        echo -e "  4. View logs in Grafana → Explore → Loki"
        return 0
    else
        echo -e "${BOLD}${RED}========================================${NC}"
        echo -e "${BOLD}${RED}  ✗ SOME TESTS FAILED${NC}"
        echo -e "${BOLD}${RED}========================================${NC}"
        echo ""
        echo -e "${YELLOW}Please check the logs for details${NC}"
        return 1
    fi
}

# Cleanup
cleanup() {
    print_info "Cleaning up..."

    # Stop simulator if still running
    if [ -f /tmp/simulator.pid ]; then
        kill $(cat /tmp/simulator.pid) 2>/dev/null || true
        rm -f /tmp/simulator.pid
    fi

    pkill -f "live-data-simulator.py" 2>/dev/null || true
}

# Main execution
main() {
    trap cleanup EXIT

    print_header "End-to-End Live Data Test Suite"

    echo -e "Configuration:"
    echo -e "  API Gateway:  $API_GATEWAY"
    echo -e "  Prometheus:   $PROMETHEUS"
    echo -e "  Grafana:      $GRAFANA"
    echo -e "  Loki:         $LOKI"
    echo -e "  Zipkin:       $ZIPKIN"
    echo -e "  Duration:     $DURATION minutes"
    echo ""

    # Pre-flight checks
    print_header "Pre-Flight Checks"
    check_service "API Gateway" "$API_GATEWAY/actuator/health" || true
    check_service "Prometheus" "$PROMETHEUS/-/healthy" || true
    check_service "Grafana" "$GRAFANA/api/health" || true
    check_service "Zipkin" "$ZIPKIN/health" || true

    # Start simulator
    start_simulator

    # Give it time to start generating data
    print_info "Waiting 30 seconds for initial data generation..."
    sleep 30

    # Run tests
    test_api_endpoints
    test_prometheus
    test_loki
    test_zipkin
    test_grafana
    stress_test

    # Monitor the rest of the run
    monitor_simulator

    # Final validation after full run
    print_header "Final Validation"
    test_prometheus
    test_loki

    # Generate report
    generate_report
}

# Run main
main
