#!/bin/bash
# Script to generate secure random secrets for the platform
# Usage: ./scripts/generate-secrets.sh

set -e

echo "==================================================="
echo "  Base Station Platform - Secret Generator"
echo "==================================================="
echo ""
echo "This script generates cryptographically secure random secrets."
echo "These should be used for production deployments."
echo ""

# Create temporary directory for secrets
SECRETS_DIR=$(mktemp -d)
trap "rm -rf $SECRETS_DIR" EXIT

# Generate secrets
echo "📝 Generating secrets..."
echo ""

# JWT Secret (base64, 64 bytes = 512 bits)
JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
echo "✓ JWT_SECRET generated (512 bits)"

# Internal Security Secret (hex, 32 bytes = 256 bits)
INTERNAL_SECRET=$(openssl rand -hex 32)
echo "✓ SECURITY_INTERNAL_SECRET generated (256 bits)"

# Database passwords (base64, 24 bytes = 192 bits each)
POSTGRES_BASESTATION_PASSWORD=$(openssl rand -base64 24 | tr -d '\n')
POSTGRES_NOTIFICATION_PASSWORD=$(openssl rand -base64 24 | tr -d '\n')
POSTGRES_AUTH_PASSWORD=$(openssl rand -base64 24 | tr -d '\n')
echo "✓ PostgreSQL passwords generated (3 databases)"

# MongoDB password
MONGODB_PASSWORD=$(openssl rand -base64 24 | tr -d '\n')
echo "✓ MongoDB password generated"

# RabbitMQ password
RABBITMQ_PASSWORD=$(openssl rand -base64 24 | tr -d '\n')
echo "✓ RabbitMQ password generated"

# Grafana password
GRAFANA_PASSWORD=$(openssl rand -base64 24 | tr -d '\n')
echo "✓ Grafana password generated"

echo ""
echo "==================================================="
echo "  Generated Secrets (SAVE THESE SECURELY!)"
echo "==================================================="
echo ""

# Display secrets
cat <<EOF
# Copy these to your .env file for Docker Compose:

SECURITY_INTERNAL_SECRET=$INTERNAL_SECRET
JWT_SECRET=$JWT_SECRET

# Database Credentials
POSTGRES_BASESTATION_PASSWORD=$POSTGRES_BASESTATION_PASSWORD
POSTGRES_NOTIFICATION_PASSWORD=$POSTGRES_NOTIFICATION_PASSWORD
POSTGRES_AUTH_PASSWORD=$POSTGRES_AUTH_PASSWORD

MONGODB_PASSWORD=$MONGODB_PASSWORD
RABBITMQ_PASSWORD=$RABBITMQ_PASSWORD
GRAFANA_PASSWORD=$GRAFANA_PASSWORD

EOF

echo ""
echo "==================================================="
echo "  Kubernetes Sealed Secrets (Recommended)"
echo "==================================================="
echo ""
echo "To use these secrets in Kubernetes with Sealed Secrets:"
echo ""
echo "1. Ensure Sealed Secrets controller is running:"
echo "   kubectl get pods -n kube-system | grep sealed-secrets"
echo ""
echo "2. Create sealed secrets (one by one):"
echo ""
echo "# JWT Secret"
echo "kubectl create secret generic jwt-secret \\"
echo "  --namespace=basestation-platform \\"
echo "  --dry-run=client \\"
echo "  --from-literal=secret=\"$JWT_SECRET\" \\"
echo "  -o yaml | kubeseal --format=yaml > k8s/sealed-secret-jwt.yaml"
echo ""
echo "# Internal Security Secret"
echo "kubectl create secret generic security-internal-secret \\"
echo "  --namespace=basestation-platform \\"
echo "  --dry-run=client \\"
echo "  --from-literal=secret=\"$INTERNAL_SECRET\" \\"
echo "  -o yaml | kubeseal --format=yaml > k8s/sealed-secret-internal.yaml"
echo ""
echo "# PostgreSQL Secrets"
echo "kubectl create secret generic postgres-secrets \\"
echo "  --namespace=basestation-platform \\"
echo "  --dry-run=client \\"
echo "  --from-literal=basestation-db=basestationdb \\"
echo "  --from-literal=basestation-user=postgres \\"
echo "  --from-literal=basestation-password=\"$POSTGRES_BASESTATION_PASSWORD\" \\"
echo "  --from-literal=notification-db=notificationdb \\"
echo "  --from-literal=notification-user=postgres \\"
echo "  --from-literal=notification-password=\"$POSTGRES_NOTIFICATION_PASSWORD\" \\"
echo "  --from-literal=auth-db=authdb \\"
echo "  --from-literal=auth-user=postgres \\"
echo "  --from-literal=auth-password=\"$POSTGRES_AUTH_PASSWORD\" \\"
echo "  -o yaml | kubeseal --format=yaml > k8s/sealed-secret-postgres.yaml"
echo ""
echo "# MongoDB Secret"
echo "kubectl create secret generic mongodb-secret \\"
echo "  --namespace=basestation-platform \\"
echo "  --dry-run=client \\"
echo "  --from-literal=username=admin \\"
echo "  --from-literal=password=\"$MONGODB_PASSWORD\" \\"
echo "  -o yaml | kubeseal --format=yaml > k8s/sealed-secret-mongodb.yaml"
echo ""
echo "# RabbitMQ Secret"
echo "kubectl create secret generic rabbitmq-secret \\"
echo "  --namespace=basestation-platform \\"
echo "  --dry-run=client \\"
echo "  --from-literal=username=admin \\"
echo "  --from-literal=password=\"$RABBITMQ_PASSWORD\" \\"
echo "  -o yaml | kubeseal --format=yaml > k8s/sealed-secret-rabbitmq.yaml"
echo ""
echo "# Grafana Secret"
echo "kubectl create secret generic grafana-secret \\"
echo "  --namespace=basestation-platform \\"
echo "  --dry-run=client \\"
echo "  --from-literal=password=\"$GRAFANA_PASSWORD\" \\"
echo "  -o yaml | kubeseal --format=yaml > k8s/sealed-secret-grafana.yaml"
echo ""
echo "3. Apply sealed secrets to cluster:"
echo "   kubectl apply -f k8s/sealed-secret-*.yaml"
echo ""
echo "==================================================="
echo ""
echo "⚠️  IMPORTANT SECURITY NOTES:"
echo ""
echo "1. Save these secrets in a secure password manager"
echo "2. Never commit the plaintext secrets to Git"
echo "3. The sealed secrets (sealed-secret-*.yaml) are safe to commit"
echo "4. Rotate these secrets periodically (every 90 days recommended)"
echo ""
echo "==================================================="
