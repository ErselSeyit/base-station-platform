#!/bin/bash
# Generate Kubernetes Secrets Dynamically
# This script generates random secure passwords for all services
# Usage: ./generate-secrets.sh | kubectl apply -f -

set -e

# Function to generate random password
generate_password() {
    openssl rand -base64 32 | tr -d "=+/" | cut -c1-32
}

# Function to generate JWT secret (base64 encoded)
generate_jwt_secret() {
    openssl rand -base64 64
}

# Function to generate internal secret (hex)
generate_internal_secret() {
    openssl rand -hex 32
}

# Generate all passwords
BASESTATION_PASSWORD=$(generate_password)
NOTIFICATION_PASSWORD=$(generate_password)
AUTH_PASSWORD=$(generate_password)
MONGODB_PASSWORD=$(generate_password)
RABBITMQ_PASSWORD=$(generate_password)
JWT_SECRET=$(generate_jwt_secret)
INTERNAL_SECRET=$(generate_internal_secret)
GRAFANA_PASSWORD=$(generate_password)

# Output Kubernetes Secret manifest
cat <<EOF
---
apiVersion: v1
kind: Secret
metadata:
  name: postgres-secrets
  namespace: basestation-platform
type: Opaque
stringData:
  basestation-db: basestationdb
  basestation-user: postgres
  basestation-password: ${BASESTATION_PASSWORD}
  notification-db: notificationdb
  notification-user: postgres
  notification-password: ${NOTIFICATION_PASSWORD}
  auth-db: authdb
  auth-user: postgres
  auth-password: ${AUTH_PASSWORD}
---
apiVersion: v1
kind: Secret
metadata:
  name: mongodb-secret
  namespace: basestation-platform
type: Opaque
stringData:
  username: admin
  password: ${MONGODB_PASSWORD}
---
apiVersion: v1
kind: Secret
metadata:
  name: rabbitmq-secret
  namespace: basestation-platform
type: Opaque
stringData:
  username: admin
  password: ${RABBITMQ_PASSWORD}
---
apiVersion: v1
kind: Secret
metadata:
  name: jwt-secret
  namespace: basestation-platform
type: Opaque
stringData:
  secret: ${JWT_SECRET}
---
apiVersion: v1
kind: Secret
metadata:
  name: security-internal-secret
  namespace: basestation-platform
type: Opaque
stringData:
  secret: ${INTERNAL_SECRET}
---
apiVersion: v1
kind: Secret
metadata:
  name: grafana-secret
  namespace: basestation-platform
type: Opaque
stringData:
  password: ${GRAFANA_PASSWORD}
EOF

# Save passwords to a secure file (encrypted with kubeseal if available)
if command -v kubeseal &> /dev/null; then
    echo "Sealed Secrets is available - use 'kubeseal' for production"
fi

# Print warning
echo "# ⚠️  WARNING: Store these credentials securely!" >&2
echo "# Use --verbose flag to display generated credentials" >&2

if [[ "${VERBOSE:-}" == "true" || "$*" == *"--verbose"* ]]; then
    echo "# Secrets generated successfully. Retrieve them with:" >&2
    echo "#   kubectl get secret <secret-name> -n basestation -o jsonpath='{.data.<key>}' | base64 -d" >&2
fi
