#!/bin/bash
# ===========================================================================
# Kubernetes Database Initialization Script
# ===========================================================================
# This script initializes ALL databases for the Base Station Platform in K8s:
#   - PostgreSQL Auth Database (authdb)
#   - PostgreSQL Base Station Database (basestationdb)
#   - PostgreSQL Notification Database (notificationdb)
#   - MongoDB Metrics Database (monitoringdb)
#
# Prerequisites:
#   - Kubernetes cluster running
#   - basestation-platform namespace created
#   - All database pods running and ready
#   - kubectl configured to access the cluster
#
# Usage:
#   ./init-db/k8s-init-all-databases.sh
#
# ===========================================================================

set -e  # Exit on error

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

NAMESPACE="basestation-platform"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}Base Station Platform - Database Init${NC}"
echo -e "${BLUE}=========================================${NC}"
echo ""

# Function to check if a pod is ready
check_pod_ready() {
    local pod_name=$1
    echo -e "${YELLOW}Checking if $pod_name is ready...${NC}"

    if ! kubectl get deployment -n $NAMESPACE $pod_name &> /dev/null; then
        echo -e "${RED}❌ Deployment $pod_name not found!${NC}"
        return 1
    fi

    local ready=$(kubectl get deployment -n $NAMESPACE $pod_name -o jsonpath='{.status.readyReplicas}')
    if [ "$ready" == "1" ]; then
        echo -e "${GREEN}✅ $pod_name is ready${NC}"
        return 0
    else
        echo -e "${RED}❌ $pod_name is not ready (ready replicas: ${ready:-0})${NC}"
        return 1
    fi
}

# Function to get MongoDB password from secret
get_mongodb_password() {
    kubectl get secret -n $NAMESPACE mongodb-secret -o jsonpath='{.data.password}' | base64 -d
}

echo -e "${BLUE}Step 1: Verifying all database pods are ready...${NC}"
echo ""

PODS_READY=true
check_pod_ready "postgres-auth" || PODS_READY=false
check_pod_ready "postgres-basestation" || PODS_READY=false
check_pod_ready "postgres-notification" || PODS_READY=false
check_pod_ready "mongodb" || PODS_READY=false

if [ "$PODS_READY" = false ]; then
    echo ""
    echo -e "${RED}❌ Some database pods are not ready. Please wait for all pods to be running.${NC}"
    echo -e "${YELLOW}Check pod status with: kubectl get pods -n $NAMESPACE${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}✅ All database pods are ready!${NC}"
echo ""

# ===========================================================================
# PostgreSQL Databases Initialization
# ===========================================================================

echo -e "${BLUE}Step 2: Initializing PostgreSQL Databases...${NC}"
echo ""

# Initialize Auth Database
echo -e "${YELLOW}Initializing auth database (authdb)...${NC}"
if kubectl exec -i -n $NAMESPACE deployment/postgres-auth -- \
    psql -U postgres -d authdb < "$SCRIPT_DIR/postgres-unified-seed.sql" 2>&1 | grep -E "total_users|Created"; then
    echo -e "${GREEN}✅ Auth database initialized${NC}"
else
    echo -e "${RED}❌ Failed to initialize auth database${NC}"
    exit 1
fi
echo ""

# Initialize Base Station Database
echo -e "${YELLOW}Initializing base station database (basestationdb)...${NC}"
if kubectl exec -i -n $NAMESPACE deployment/postgres-basestation -- \
    psql -U postgres -d basestationdb < "$SCRIPT_DIR/postgres-unified-seed.sql" 2>&1 | grep -E "total_stations|Created"; then
    echo -e "${GREEN}✅ Base station database initialized${NC}"
else
    echo -e "${RED}❌ Failed to initialize base station database${NC}"
    exit 1
fi
echo ""

# Initialize Notification Database
echo -e "${YELLOW}Initializing notification database (notificationdb)...${NC}"
if kubectl exec -i -n $NAMESPACE deployment/postgres-notification -- \
    psql -U postgres -d notificationdb < "$SCRIPT_DIR/postgres-unified-seed.sql" 2>&1 | grep -E "total_notifications|Created"; then
    echo -e "${GREEN}✅ Notification database initialized${NC}"
else
    echo -e "${RED}❌ Failed to initialize notification database${NC}"
    exit 1
fi
echo ""

# ===========================================================================
# MongoDB Initialization
# ===========================================================================

echo -e "${BLUE}Step 3: Initializing MongoDB Database...${NC}"
echo ""

echo -e "${YELLOW}Getting MongoDB credentials from K8s secret...${NC}"
MONGODB_PASSWORD=$(get_mongodb_password)

if [ -z "$MONGODB_PASSWORD" ]; then
    echo -e "${RED}❌ Failed to retrieve MongoDB password from secret${NC}"
    exit 1
fi
echo -e "${GREEN}✅ MongoDB credentials retrieved${NC}"
echo ""

# Get MongoDB pod name
MONGODB_POD=$(kubectl get pod -n $NAMESPACE -l app=mongodb -o jsonpath='{.items[0].metadata.name}')
if [ -z "$MONGODB_POD" ]; then
    echo -e "${RED}❌ MongoDB pod not found${NC}"
    exit 1
fi
echo -e "${YELLOW}MongoDB pod: $MONGODB_POD${NC}"

# Copy seed script to MongoDB pod
echo -e "${YELLOW}Copying mongodb-seed.js to MongoDB pod...${NC}"
if kubectl cp "$SCRIPT_DIR/mongodb-seed.js" "$NAMESPACE/$MONGODB_POD:/tmp/mongodb-seed.js"; then
    echo -e "${GREEN}✅ Seed script copied${NC}"
else
    echo -e "${RED}❌ Failed to copy seed script${NC}"
    exit 1
fi

# Execute seed script
echo -e "${YELLOW}Executing MongoDB seed script...${NC}"
if kubectl exec -n $NAMESPACE $MONGODB_POD -- \
    mongosh -u admin -p "$MONGODB_PASSWORD" \
    --authenticationDatabase admin /tmp/mongodb-seed.js 2>&1 | grep -E "Inserted|complete"; then
    echo -e "${GREEN}✅ MongoDB database initialized${NC}"
else
    echo -e "${RED}❌ Failed to initialize MongoDB database${NC}"
    exit 1
fi
echo ""

# ===========================================================================
# Verification
# ===========================================================================

echo -e "${BLUE}Step 4: Verifying database initialization...${NC}"
echo ""

# Verify auth database
echo -e "${YELLOW}Verifying auth database...${NC}"
AUTH_COUNT=$(kubectl exec -n $NAMESPACE deployment/postgres-auth -- \
    psql -U postgres -d authdb -t -c "SELECT COUNT(*) FROM users;" 2>/dev/null | tr -d ' ')
if [ "$AUTH_COUNT" -ge "2" ]; then
    echo -e "${GREEN}✅ Auth database: $AUTH_COUNT users found${NC}"
else
    echo -e "${RED}❌ Auth database verification failed (users: $AUTH_COUNT)${NC}"
fi

# Verify base station database
echo -e "${YELLOW}Verifying base station database...${NC}"
STATION_COUNT=$(kubectl exec -n $NAMESPACE deployment/postgres-basestation -- \
    psql -U postgres -d basestationdb -t -c "SELECT COUNT(*) FROM base_stations;" 2>/dev/null | tr -d ' ')
if [ "$STATION_COUNT" -ge "26" ]; then
    echo -e "${GREEN}✅ Base station database: $STATION_COUNT stations found${NC}"
else
    echo -e "${RED}❌ Base station database verification failed (stations: $STATION_COUNT)${NC}"
fi

# Verify notification database
echo -e "${YELLOW}Verifying notification database...${NC}"
NOTIF_COUNT=$(kubectl exec -n $NAMESPACE deployment/postgres-notification -- \
    psql -U postgres -d notificationdb -t -c "SELECT COUNT(*) FROM notifications;" 2>/dev/null | tr -d ' ')
if [ "$NOTIF_COUNT" -ge "18" ]; then
    echo -e "${GREEN}✅ Notification database: $NOTIF_COUNT notifications found${NC}"
else
    echo -e "${RED}❌ Notification database verification failed (notifications: $NOTIF_COUNT)${NC}"
fi

# Verify MongoDB
echo -e "${YELLOW}Verifying MongoDB database...${NC}"
METRICS_COUNT=$(kubectl exec -n $NAMESPACE $MONGODB_POD -- \
    mongosh -u admin -p "$MONGODB_PASSWORD" --authenticationDatabase admin \
    --quiet --eval "db.getSiblingDB('monitoringdb').metric_data.countDocuments({})" 2>/dev/null)
if [ "$METRICS_COUNT" -ge "1000" ]; then
    echo -e "${GREEN}✅ MongoDB database: $METRICS_COUNT metrics found${NC}"
else
    echo -e "${RED}❌ MongoDB verification failed (metrics: $METRICS_COUNT)${NC}"
fi

echo ""
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}✅ Database Initialization Complete!${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
echo -e "${BLUE}Next steps:${NC}"
echo -e "  1. Test login with credentials from your secrets configuration"
echo -e "  2. Access frontend: ${YELLOW}http://localhost:30000${NC}"
echo -e "  3. Access API Gateway: ${YELLOW}http://localhost:30080${NC}"
echo ""
