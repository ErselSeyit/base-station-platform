#!/bin/bash
# ===========================================================================
# Secret Rotation Script for Base Station Platform
# ===========================================================================
# This script rotates sensitive secrets in the Kubernetes cluster:
#   - JWT secrets
#   - Database passwords
#   - Grafana password
#
# Usage:
#   ./scripts/rotate-secrets.sh [--jwt] [--db] [--grafana] [--all]
#
# Options:
#   --jwt        Rotate JWT secrets only
#   --db         Rotate database passwords only
#   --grafana    Rotate Grafana password only
#   --all        Rotate all secrets (default)
#
# ===========================================================================

set -e

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

NAMESPACE="basestation-platform"

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}Secret Rotation Utility${NC}"
echo -e "${BLUE}=========================================${NC}"
echo ""

# Parse arguments
ROTATE_JWT=false
ROTATE_DB=false
ROTATE_GRAFANA=false

if [ $# -eq 0 ] || [ "$1" == "--all" ]; then
    ROTATE_JWT=true
    ROTATE_DB=true
    ROTATE_GRAFANA=true
else
    while [ $# -gt 0 ]; do
        case "$1" in
            --jwt)
                ROTATE_JWT=true
                ;;
            --db)
                ROTATE_DB=true
                ;;
            --grafana)
                ROTATE_GRAFANA=true
                ;;
            *)
                echo -e "${RED}Unknown option: $1${NC}"
                exit 1
                ;;
        esac
        shift
    done
fi

# Function to generate random base64 string
generate_secret() {
    local length=$1
    openssl rand -base64 $length | tr -d '\n'
}

# ===========================================================================
# JWT Secret Rotation
# ===========================================================================
if [ "$ROTATE_JWT" = true ]; then
    echo -e "${YELLOW}Rotating JWT secrets...${NC}"

    # Generate new JWT secret (64 bytes = 512 bits)
    NEW_JWT_SECRET=$(generate_secret 64)

    # Update K8s secret
    kubectl create secret generic jwt-secret \
        --from-literal=secret="$NEW_JWT_SECRET" \
        --namespace=$NAMESPACE \
        --dry-run=client -o yaml | kubectl apply -f -

    echo -e "${GREEN}✓ JWT secret rotated${NC}"
    echo -e "${YELLOW}  Restarting auth-service and api-gateway...${NC}"

    # Restart services that use JWT
    kubectl rollout restart deployment/auth-service -n $NAMESPACE
    kubectl rollout restart deployment/api-gateway -n $NAMESPACE

    # Wait for rollout to complete
    kubectl rollout status deployment/auth-service -n $NAMESPACE --timeout=300s
    kubectl rollout status deployment/api-gateway -n $NAMESPACE --timeout=300s

    echo -e "${GREEN}✓ Services restarted successfully${NC}"
    echo ""
fi

# ===========================================================================
# Database Password Rotation
# ===========================================================================
if [ "$ROTATE_DB" = true ]; then
    echo -e "${YELLOW}Rotating database passwords...${NC}"
    echo -e "${RED}⚠️  WARNING: This will cause brief service disruption!${NC}"
    read -p "Continue? [y/N]: " CONFIRM

    if [ "$CONFIRM" != "y" ]; then
        echo "Database rotation cancelled"
    else
        # Generate new passwords
        NEW_AUTH_PASSWORD=$(generate_secret 32)
        NEW_BASESTATION_PASSWORD=$(generate_secret 32)
        NEW_NOTIFICATION_PASSWORD=$(generate_secret 32)
        NEW_MONGODB_PASSWORD=$(generate_secret 32)
        NEW_RABBITMQ_PASSWORD=$(generate_secret 32)

        # Update PostgreSQL secrets
        kubectl create secret generic postgres-secrets \
            --from-literal=auth-db=authdb \
            --from-literal=auth-user=postgres \
            --from-literal=auth-password="$NEW_AUTH_PASSWORD" \
            --from-literal=basestation-db=basestationdb \
            --from-literal=basestation-user=postgres \
            --from-literal=basestation-password="$NEW_BASESTATION_PASSWORD" \
            --from-literal=notification-db=notificationdb \
            --from-literal=notification-user=postgres \
            --from-literal=notification-password="$NEW_NOTIFICATION_PASSWORD" \
            --namespace=$NAMESPACE \
            --dry-run=client -o yaml | kubectl apply -f -

        # Update MongoDB secret
        kubectl create secret generic mongodb-secret \
            --from-literal=username=admin \
            --from-literal=password="$NEW_MONGODB_PASSWORD" \
            --namespace=$NAMESPACE \
            --dry-run=client -o yaml | kubectl apply -f -

        # Update RabbitMQ secret
        kubectl create secret generic rabbitmq-secret \
            --from-literal=username=admin \
            --from-literal=password="$NEW_RABBITMQ_PASSWORD" \
            --namespace=$NAMESPACE \
            --dry-run=client -o yaml | kubectl apply -f -

        echo -e "${GREEN}✓ Database secrets updated${NC}"
        echo -e "${YELLOW}  Restarting database pods...${NC}"

        # Restart all databases
        kubectl rollout restart deployment/postgres-auth -n $NAMESPACE
        kubectl rollout restart deployment/postgres-basestation -n $NAMESPACE
        kubectl rollout restart deployment/postgres-notification -n $NAMESPACE
        kubectl rollout restart deployment/mongodb -n $NAMESPACE
        kubectl rollout restart deployment/rabbitmq -n $NAMESPACE

        # Wait for databases to be ready
        echo -e "${YELLOW}  Waiting for databases to be ready...${NC}"
        kubectl rollout status deployment/postgres-auth -n $NAMESPACE --timeout=300s
        kubectl rollout status deployment/postgres-basestation -n $NAMESPACE --timeout=300s
        kubectl rollout status deployment/postgres-notification -n $NAMESPACE --timeout=300s
        kubectl rollout status deployment/mongodb -n $NAMESPACE --timeout=300s
        kubectl rollout status deployment/rabbitmq -n $NAMESPACE --timeout=300s

        echo -e "${GREEN}✓ Databases restarted${NC}"
        echo -e "${YELLOW}  Restarting application services...${NC}"

        # Restart all application services
        kubectl rollout restart deployment/auth-service -n $NAMESPACE
        kubectl rollout restart deployment/base-station-service -n $NAMESPACE
        kubectl rollout restart deployment/monitoring-service -n $NAMESPACE
        kubectl rollout restart deployment/notification-service -n $NAMESPACE

        # Wait for services
        kubectl rollout status deployment/auth-service -n $NAMESPACE --timeout=300s
        kubectl rollout status deployment/base-station-service -n $NAMESPACE --timeout=300s
        kubectl rollout status deployment/monitoring-service -n $NAMESPACE --timeout=300s
        kubectl rollout status deployment/notification-service -n $NAMESPACE --timeout=300s

        echo -e "${GREEN}✓ All services restarted successfully${NC}"
        echo ""
    fi
fi

# ===========================================================================
# Grafana Password Rotation
# ===========================================================================
if [ "$ROTATE_GRAFANA" = true ]; then
    echo -e "${YELLOW}Rotating Grafana password...${NC}"

    # Generate new password
    NEW_GRAFANA_PASSWORD=$(generate_secret 16)

    # Update K8s secret
    kubectl create secret generic grafana-secret \
        --from-literal=password="$NEW_GRAFANA_PASSWORD" \
        --namespace=$NAMESPACE \
        --dry-run=client -o yaml | kubectl apply -f -

    echo -e "${GREEN}✓ Grafana secret rotated${NC}"
    echo -e "${YELLOW}  Restarting Grafana...${NC}"

    # Restart Grafana
    kubectl rollout restart deployment/grafana -n $NAMESPACE
    kubectl rollout status deployment/grafana -n $NAMESPACE --timeout=300s

    echo -e "${GREEN}✓ Grafana restarted${NC}"
    echo -e "${GREEN}  Retrieve new password with:${NC}"
    echo -e "${BLUE}  kubectl get secret grafana-secret -n $NAMESPACE -o jsonpath='{.data.password}' | base64 -d${NC}"
    echo ""
fi

# ===========================================================================
# Summary
# ===========================================================================
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}✓ Secret Rotation Complete!${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
echo -e "${BLUE}Rotated:${NC}"
[ "$ROTATE_JWT" = true ] && echo -e "  ✓ JWT secrets"
[ "$ROTATE_DB" = true ] && echo -e "  ✓ Database passwords"
[ "$ROTATE_GRAFANA" = true ] && echo -e "  ✓ Grafana password"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo -e "  1. Verify all services are running: ${BLUE}kubectl get pods -n $NAMESPACE${NC}"
echo -e "  2. Test authentication with new credentials"
echo -e "  3. Update any external systems that use these credentials"
echo -e "  4. Schedule next rotation (recommended: quarterly)"
echo ""
