#!/bin/bash
# ============================================================================
# Create Kubernetes Secrets from .env file
# ============================================================================
# Reads the root .env (docker-compose format) and creates K8s secrets.
# Supports both per-service variables (POSTGRES_USER) and unified
# variables (ADMIN_USERNAME). Per-service takes precedence if set.
# ============================================================================

set -euo pipefail

NAMESPACE="${1:-basestation-platform}"
ENV_FILE="${ENV_FILE:-.env}"

if [[ ! -f "$ENV_FILE" ]]; then
    echo "Error: $ENV_FILE not found!"
    echo "Copy .env.example to .env and fill in your values:"
    echo "  cp .env.example .env"
    exit 1
fi

# Load environment variables
set -a
source "$ENV_FILE"
set +a

# Resolve variables: per-service takes precedence, then unified, then default
PG_USER="${POSTGRES_USER:-${ADMIN_USERNAME:-postgres}}"
PG_PASS="${POSTGRES_PASSWORD:-${ADMIN_PASSWORD:?POSTGRES_PASSWORD or ADMIN_PASSWORD required}}"
MONGO_USER="${MONGODB_USER:-${ADMIN_USERNAME:-admin}}"
MONGO_PASS="${MONGODB_PASSWORD:-${ADMIN_PASSWORD:?MONGODB_PASSWORD or ADMIN_PASSWORD required}}"
RMQ_USER="${RABBITMQ_USER:-${ADMIN_USERNAME:-admin}}"
RMQ_PASS="${RABBITMQ_PASSWORD:-${ADMIN_PASSWORD:?RABBITMQ_PASSWORD or ADMIN_PASSWORD required}}"
GF_PASS="${GRAFANA_PASSWORD:-${ADMIN_PASSWORD:?GRAFANA_PASSWORD or ADMIN_PASSWORD required}}"
ADMIN_USER="${ADMIN_USERNAME:-admin}"
ADMIN_PASS="${AUTH_ADMIN_PASSWORD:-${ADMIN_PASSWORD:?AUTH_ADMIN_PASSWORD or ADMIN_PASSWORD required}}"
OP_USER="${OPERATOR_USERNAME:-${BRIDGE_USERNAME:-operator}}"
OP_PASS="${OPERATOR_PASSWORD:-$ADMIN_PASS}"
INT_SECRET="${INTERNAL_SECRET:-${SECURITY_INTERNAL_SECRET:?INTERNAL_SECRET or SECURITY_INTERNAL_SECRET required}}"
DIAG_SECRET="${DIAGNOSTIC_SECRET:-${INT_SECRET}}"

echo "Creating secrets in namespace: $NAMESPACE"

# Ensure namespace exists
kubectl get namespace "$NAMESPACE" &>/dev/null || kubectl create namespace "$NAMESPACE"

# Auth credentials (for auth-service users)
kubectl create secret generic auth-credentials \
    --namespace="$NAMESPACE" \
    --from-literal=admin-username="${ADMIN_USER}" \
    --from-literal=admin-password="${ADMIN_PASS}" \
    --from-literal=operator-username="${OP_USER}" \
    --from-literal=operator-password="${OP_PASS}" \
    --dry-run=client -o yaml | kubectl apply -f -

# Edge bridge
kubectl create secret generic edge-bridge-secret \
    --namespace="$NAMESPACE" \
    --from-literal=username="${OP_USER}" \
    --from-literal=password="${OP_PASS}" \
    --dry-run=client -o yaml | kubectl apply -f -

# JWT secret
kubectl create secret generic jwt-secret \
    --namespace="$NAMESPACE" \
    --from-literal=secret="${JWT_SECRET:?JWT_SECRET is required}" \
    --dry-run=client -o yaml | kubectl apply -f -

# Internal service secret
kubectl create secret generic security-internal-secret \
    --namespace="$NAMESPACE" \
    --from-literal=secret="${INT_SECRET}" \
    --dry-run=client -o yaml | kubectl apply -f -

# Diagnostic secret
kubectl create secret generic diagnostic-secret \
    --namespace="$NAMESPACE" \
    --from-literal=secret="${DIAG_SECRET}" \
    --dry-run=client -o yaml | kubectl apply -f -

# PostgreSQL
kubectl create secret generic postgres-secrets \
    --namespace="$NAMESPACE" \
    --from-literal=username="${PG_USER}" \
    --from-literal=password="${PG_PASS}" \
    --dry-run=client -o yaml | kubectl apply -f -

# MongoDB
kubectl create secret generic mongodb-secret \
    --namespace="$NAMESPACE" \
    --from-literal=username="${MONGO_USER}" \
    --from-literal=password="${MONGO_PASS}" \
    --dry-run=client -o yaml | kubectl apply -f -

# RabbitMQ
kubectl create secret generic rabbitmq-secret \
    --namespace="$NAMESPACE" \
    --from-literal=username="${RMQ_USER}" \
    --from-literal=password="${RMQ_PASS}" \
    --dry-run=client -o yaml | kubectl apply -f -

# Grafana
kubectl create secret generic grafana-secret \
    --namespace="$NAMESPACE" \
    --from-literal=password="${GF_PASS}" \
    --dry-run=client -o yaml | kubectl apply -f -

echo ""
echo "==========================================="
echo "Secrets created:"
echo "  PostgreSQL:  ${PG_USER}"
echo "  MongoDB:     ${MONGO_USER}"
echo "  RabbitMQ:    ${RMQ_USER}"
echo "  Auth admin:  ${ADMIN_USER}"
echo "  Operator:    ${OP_USER}"
echo "==========================================="
echo ""
echo "Verify with: kubectl get secrets -n $NAMESPACE"
