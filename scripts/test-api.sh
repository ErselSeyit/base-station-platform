#!/bin/bash
# test-api.sh - Helper script for testing API endpoints with proper JSON escaping
#
# Usage:
#   ./scripts/test-api.sh login              # Get auth token
#   ./scripts/test-api.sh get /stations      # GET request
#   ./scripts/test-api.sh post /metrics/batch '{"stationId":"27","metrics":[...]}'
#
# Environment variables:
#   API_BASE_URL  - API gateway URL (default: http://localhost:8080)
#   API_USERNAME  - Username (default: operator)
#   API_PASSWORD  - Password (default: from env or prompt)

set -euo pipefail

# Configuration
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
API_USERNAME="${API_USERNAME:-operator}"
TOKEN_FILE="/tmp/.api-token-$$"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# Clean up token file on exit
cleanup() {
    rm -f "$TOKEN_FILE" 2>/dev/null || true
}
trap cleanup EXIT

# Login and get token - uses printf to avoid shell escaping issues
do_login() {
    local username="${1:-$API_USERNAME}"
    local password="${2:-${API_PASSWORD:-}}"

    if [[ -z "$password" ]]; then
        log_error "Password required. Set API_PASSWORD or pass as argument."
        exit 1
    fi

    log_info "Authenticating as '$username'..."

    # Use printf and jq to properly escape JSON - this avoids shell escaping issues
    local json_body
    json_body=$(jq -n --arg u "$username" --arg p "$password" '{username: $u, password: $p}')

    local response
    response=$(curl -s -w "\n%{http_code}" -X POST "${API_BASE_URL}/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d "$json_body")

    local http_code
    http_code=$(echo "$response" | tail -n1)
    local body
    body=$(echo "$response" | sed '$d')

    if [[ "$http_code" != "200" ]]; then
        log_error "Login failed (HTTP $http_code):"
        echo "$body" | jq . 2>/dev/null || echo "$body"
        exit 1
    fi

    local token
    token=$(echo "$body" | jq -r '.token')

    if [[ -z "$token" || "$token" == "null" ]]; then
        log_error "No token in response:"
        echo "$body" | jq .
        exit 1
    fi

    echo "$token" > "$TOKEN_FILE"
    log_info "Authentication successful. Token saved."
    echo "$token"
}

# Get current token (login if needed)
get_token() {
    if [[ -f "$TOKEN_FILE" ]]; then
        cat "$TOKEN_FILE"
    else
        do_login >/dev/null
        cat "$TOKEN_FILE"
    fi
}

# Make authenticated GET request
do_get() {
    local path="$1"
    local token
    token=$(get_token)

    # Ensure path starts with /api/v1 if not already
    if [[ ! "$path" =~ ^/api ]]; then
        path="/api/v1${path}"
    fi

    curl -s "${API_BASE_URL}${path}" \
        -H "Authorization: Bearer $token" \
        -H "Content-Type: application/json" | jq .
}

# Make authenticated POST request with proper JSON escaping
do_post() {
    local path="$1"
    local data="${2:-}"
    local token
    token=$(get_token)

    # Ensure path starts with /api/v1 if not already
    if [[ ! "$path" =~ ^/api ]]; then
        path="/api/v1${path}"
    fi

    if [[ -z "$data" ]]; then
        curl -s -X POST "${API_BASE_URL}${path}" \
            -H "Authorization: Bearer $token" \
            -H "Content-Type: application/json" | jq .
    else
        # Validate JSON before sending
        if ! echo "$data" | jq . >/dev/null 2>&1; then
            log_error "Invalid JSON data provided"
            exit 1
        fi

        curl -s -X POST "${API_BASE_URL}${path}" \
            -H "Authorization: Bearer $token" \
            -H "Content-Type: application/json" \
            -d "$data" | jq .
    fi
}

# Send metrics with proper escaping
send_metric() {
    local station_id="$1"
    local metric_type="$2"
    local value="$3"

    # Use jq to build properly escaped JSON
    local json_body
    json_body=$(jq -n \
        --arg sid "$station_id" \
        --arg type "$metric_type" \
        --argjson val "$value" \
        '{stationId: $sid, metrics: [{type: $type, value: $val}]}')

    log_info "Sending $metric_type=$value for station $station_id"
    do_post "/metrics/batch" "$json_body"
}

# Show usage
usage() {
    cat << 'EOF'
Usage: test-api.sh <command> [args...]

Commands:
  login [username] [password]  - Authenticate and get token
  get <path>                   - Make GET request (e.g., /stations, /diagnostics)
  post <path> [json]           - Make POST request with JSON body
  metric <station> <type> <value> - Send a metric (e.g., metric 27 CPU_USAGE 95.0)

Environment:
  API_BASE_URL  - Base URL (default: http://localhost:8080)
  API_USERNAME  - Username (default: operator)
  API_PASSWORD  - Password (required for login)

Examples:
  export API_PASSWORD='<your-password>'
  ./test-api.sh login
  ./test-api.sh get /stations
  ./test-api.sh get /diagnostics
  ./test-api.sh metric 27 CPU_USAGE 95.0
  ./test-api.sh post /metrics/batch '{"stationId":"27","metrics":[{"type":"CPU_USAGE","value":50.0}]}'

Note: This script uses jq for proper JSON escaping, avoiding shell interpretation issues
      with special characters like ! @ # $ etc.
EOF
}

# Main
case "${1:-}" in
    login)
        do_login "${2:-}" "${3:-}"
        ;;
    get)
        [[ -z "${2:-}" ]] && { log_error "Path required"; usage; exit 1; }
        do_get "$2"
        ;;
    post)
        [[ -z "${2:-}" ]] && { log_error "Path required"; usage; exit 1; }
        do_post "$2" "${3:-}"
        ;;
    metric)
        [[ -z "${4:-}" ]] && { log_error "station_id, metric_type, value required"; usage; exit 1; }
        send_metric "$2" "$3" "$4"
        ;;
    -h|--help|help)
        usage
        ;;
    *)
        log_error "Unknown command: ${1:-}"
        usage
        exit 1
        ;;
esac
