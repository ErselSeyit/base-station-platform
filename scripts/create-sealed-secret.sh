#!/bin/bash
# ============================================================================
# Create Sealed Secret Helper Script
# ============================================================================
# This script helps create sealed secrets that can be safely stored in Git
#
# Usage:
#   ./scripts/create-sealed-secret.sh <secret-name> <namespace> <type>
#
# Examples:
#   ./scripts/create-sealed-secret.sh jwt-secret basestation-platform generic
#   ./scripts/create-sealed-secret.sh postgres-secrets basestation-platform generic
#   ./scripts/create-sealed-secret.sh grafana-secret basestation-platform generic
#
# Prerequisites:
#   - kubeseal CLI installed (brew install kubeseal or download from GitHub)
#   - Sealed Secrets controller deployed (kubectl apply -f k8s/sealed-secrets.yaml)
# ============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SECRET_NAME="${1}"
NAMESPACE="${2:-basestation-platform}"
SECRET_TYPE="${3:-generic}"

# Functions
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

show_usage() {
    cat << EOF
Usage: $0 <secret-name> <namespace> <type>

Arguments:
  secret-name    Name of the secret to create
  namespace      Kubernetes namespace (default: basestation-platform)
  type           Secret type: generic, tls, docker-registry (default: generic)

Examples:
  # Create JWT secret
  $0 jwt-secret basestation-platform generic

  # Create database password secret
  $0 postgres-secrets basestation-platform generic

  # Create Grafana admin password
  $0 grafana-secret basestation-platform generic

EOF
}

# Validate inputs
if [ -z "$SECRET_NAME" ]; then
    print_error "Secret name is required"
    show_usage
    exit 1
fi

# Check if kubeseal is installed
if ! command -v kubeseal &> /dev/null; then
    print_error "kubeseal CLI is not installed"
    echo ""
    echo "Install kubeseal:"
    echo "  macOS: brew install kubeseal"
    echo "  Linux: wget https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.24.5/kubeseal-0.24.5-linux-amd64.tar.gz"
    exit 1
fi

# Check if sealed-secrets controller is running
if ! kubectl get deployment sealed-secrets-controller -n sealed-secrets &> /dev/null; then
    print_error "Sealed Secrets controller is not deployed"
    echo ""
    echo "Deploy the controller first:"
    echo "  kubectl apply -f k8s/sealed-secrets.yaml"
    exit 1
fi

print_info "Creating sealed secret: $SECRET_NAME in namespace: $NAMESPACE"
echo ""

# Interactive prompt for secret data
declare -A SECRET_DATA

case "$SECRET_NAME" in
    "jwt-secret")
        print_info "Generating JWT secret (64-byte random string)..."
        JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
        SECRET_DATA["secret"]="$JWT_SECRET"
        ;;

    "postgres-secrets")
        print_info "Enter PostgreSQL passwords (or press Enter to auto-generate):"

        read -p "Auth DB password: " -s AUTH_PASS
        echo ""
        if [ -z "$AUTH_PASS" ]; then
            AUTH_PASS=$(openssl rand -base64 32 | tr -d '\n')
            print_info "Auto-generated auth password"
        fi

        read -p "Base Station DB password: " -s BASESTATION_PASS
        echo ""
        if [ -z "$BASESTATION_PASS" ]; then
            BASESTATION_PASS=$(openssl rand -base64 32 | tr -d '\n')
            print_info "Auto-generated basestation password"
        fi

        read -p "Notification DB password: " -s NOTIFICATION_PASS
        echo ""
        if [ -z "$NOTIFICATION_PASS" ]; then
            NOTIFICATION_PASS=$(openssl rand -base64 32 | tr -d '\n')
            print_info "Auto-generated notification password"
        fi

        SECRET_DATA["auth-password"]="$AUTH_PASS"
        SECRET_DATA["basestation-password"]="$BASESTATION_PASS"
        SECRET_DATA["notification-password"]="$NOTIFICATION_PASS"
        ;;

    "grafana-secret")
        read -p "Grafana admin password: " -s GRAFANA_PASS
        echo ""
        if [ -z "$GRAFANA_PASS" ]; then
            GRAFANA_PASS=$(openssl rand -base64 16 | tr -d '\n')
            print_info "Auto-generated Grafana password: $GRAFANA_PASS"
        fi
        SECRET_DATA["password"]="$GRAFANA_PASS"
        ;;

    *)
        # Generic secret - prompt for key-value pairs
        print_info "Enter secret data (key=value pairs, empty line to finish):"
        while true; do
            read -p "Key (or Enter to finish): " KEY
            if [ -z "$KEY" ]; then
                break
            fi
            read -p "Value for $KEY: " -s VALUE
            echo ""
            SECRET_DATA["$KEY"]="$VALUE"
        done
        ;;
esac

# Build kubectl create secret command
CMD="kubectl create secret generic $SECRET_NAME --namespace=$NAMESPACE"
for KEY in "${!SECRET_DATA[@]}"; do
    CMD="$CMD --from-literal=$KEY=${SECRET_DATA[$KEY]}"
done
CMD="$CMD --dry-run=client -o yaml"

print_info "Creating sealed secret..."

# Create sealed secret
SEALED_SECRET_FILE="k8s/sealed-$SECRET_NAME.yaml"

eval "$CMD" | kubeseal \
    --controller-namespace sealed-secrets \
    --controller-name sealed-secrets-controller \
    --format yaml > "$SEALED_SECRET_FILE"

if [ $? -eq 0 ]; then
    print_success "Sealed secret created: $SEALED_SECRET_FILE"
    echo ""
    print_info "This file is safe to commit to Git!"
    echo ""
    print_info "Apply the sealed secret:"
    echo "  kubectl apply -f $SEALED_SECRET_FILE"
    echo ""
    print_info "The controller will automatically decrypt it and create the actual secret"
else
    print_error "Failed to create sealed secret"
    exit 1
fi

# Show preview
print_info "Preview of sealed secret (encrypted):"
echo "---"
head -20 "$SEALED_SECRET_FILE"
echo "..."
