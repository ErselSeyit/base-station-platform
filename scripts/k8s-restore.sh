#!/bin/bash
# ===========================================================================
# Kubernetes Database Restore Script
# ===========================================================================
# This script restores databases from backup files.
#
# Usage:
#   ./scripts/k8s-restore.sh <backup_directory>
#
# Example:
#   ./scripts/k8s-restore.sh ./backups/20260106_140530
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

if [ -z "$1" ]; then
    echo -e "${RED}Error: Backup directory not specified${NC}"
    echo ""
    echo "Usage: $0 <backup_directory>"
    echo ""
    echo "Available backups:"
    ls -1d ./backups/*/ 2>/dev/null || echo "  No backups found"
    exit 1
fi

BACKUP_DIR="$1"

if [ ! -d "$BACKUP_DIR" ]; then
    echo -e "${RED}Error: Backup directory does not exist: $BACKUP_DIR${NC}"
    exit 1
fi

echo -e "${RED}=========================================${NC}"
echo -e "${RED}⚠️  DATABASE RESTORE WARNING ⚠️${NC}"
echo -e "${RED}=========================================${NC}"
echo ""
echo -e "${YELLOW}This will OVERWRITE all current database data!${NC}"
echo -e "${YELLOW}Backup directory: $BACKUP_DIR${NC}"
echo ""
echo "Backup contains:"
ls -lh "$BACKUP_DIR"
echo ""
read -p "Are you sure you want to restore from this backup? [yes/NO]: " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo -e "${YELLOW}Restore cancelled${NC}"
    exit 0
fi

echo ""
echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}Starting Database Restore${NC}"
echo -e "${BLUE}=========================================${NC}"
echo ""

# Find backup files
AUTH_BACKUP=$(ls "$BACKUP_DIR"/authdb_*.sql.gz 2>/dev/null | head -1)
BASESTATION_BACKUP=$(ls "$BACKUP_DIR"/basestationdb_*.sql.gz 2>/dev/null | head -1)
NOTIFICATION_BACKUP=$(ls "$BACKUP_DIR"/notificationdb_*.sql.gz 2>/dev/null | head -1)
MONGODB_BACKUP=$(ls "$BACKUP_DIR"/monitoringdb_*.archive.gz 2>/dev/null | head -1)

# Restore PostgreSQL databases
if [ -n "$AUTH_BACKUP" ]; then
    echo -e "${YELLOW}Restoring authdb...${NC}"
    gunzip -c "$AUTH_BACKUP" | kubectl exec -i -n $NAMESPACE deployment/postgres-auth -- \
        psql -U postgres -d authdb
    echo -e "${GREEN}✓ authdb restored${NC}"
else
    echo -e "${RED}⚠️  authdb backup not found${NC}"
fi

if [ -n "$BASESTATION_BACKUP" ]; then
    echo -e "${YELLOW}Restoring basestationdb...${NC}"
    gunzip -c "$BASESTATION_BACKUP" | kubectl exec -i -n $NAMESPACE deployment/postgres-basestation -- \
        psql -U postgres -d basestationdb
    echo -e "${GREEN}✓ basestationdb restored${NC}"
else
    echo -e "${RED}⚠️  basestationdb backup not found${NC}"
fi

if [ -n "$NOTIFICATION_BACKUP" ]; then
    echo -e "${YELLOW}Restoring notificationdb...${NC}"
    gunzip -c "$NOTIFICATION_BACKUP" | kubectl exec -i -n $NAMESPACE deployment/postgres-notification -- \
        psql -U postgres -d notificationdb
    echo -e "${GREEN}✓ notificationdb restored${NC}"
else
    echo -e "${RED}⚠️  notificationdb backup not found${NC}"
fi

# Restore MongoDB
if [ -n "$MONGODB_BACKUP" ]; then
    echo -e "${YELLOW}Restoring monitoringdb...${NC}"

    # Get MongoDB password and pod
    MONGODB_PASSWORD=$(kubectl get secret -n $NAMESPACE mongodb-secret -o jsonpath='{.data.password}' | base64 -d)
    MONGODB_POD=$(kubectl get pod -n $NAMESPACE -l app=mongodb -o jsonpath='{.items[0].metadata.name}')

    # Copy backup to pod and restore
    kubectl cp "$MONGODB_BACKUP" "$NAMESPACE/$MONGODB_POD:/tmp/restore.archive.gz"
    kubectl exec -n $NAMESPACE $MONGODB_POD -- sh -c "gunzip < /tmp/restore.archive.gz > /tmp/restore.archive"
    kubectl exec -n $NAMESPACE $MONGODB_POD -- \
        mongorestore --username=admin --password="$MONGODB_PASSWORD" \
        --authenticationDatabase=admin \
        --archive=/tmp/restore.archive \
        --drop

    # Cleanup
    kubectl exec -n $NAMESPACE $MONGODB_POD -- rm -f /tmp/restore.archive /tmp/restore.archive.gz

    echo -e "${GREEN}✓ monitoringdb restored${NC}"
else
    echo -e "${RED}⚠️  monitoringdb backup not found${NC}"
fi

echo ""
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}✓ Database Restore Complete!${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo -e "  1. Verify data integrity"
echo -e "  2. Restart application services if needed:"
echo -e "     ${BLUE}kubectl rollout restart deployment -n $NAMESPACE${NC}"
echo ""
