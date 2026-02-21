#!/bin/bash
# ===========================================================================
# Manual Kubernetes Database Backup Script
# ===========================================================================
# This script performs an immediate backup of all databases.
# Useful for pre-deployment backups or on-demand backups.
#
# Usage:
#   ./scripts/k8s-backup-manual.sh
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
DATE=$(date +%Y%m%d_%H%M%S)
LOCAL_BACKUP_DIR="./backups/${DATE}"

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}Manual Database Backup${NC}"
echo -e "${BLUE}=========================================${NC}"
echo ""

# Create local backup directory
mkdir -p "$LOCAL_BACKUP_DIR"
echo -e "${GREEN}✓${NC} Created local backup directory: $LOCAL_BACKUP_DIR"
echo ""

# Backup PostgreSQL databases
echo -e "${YELLOW}Backing up PostgreSQL databases...${NC}"

echo -e "  Backing up authdb..."
kubectl exec -n $NAMESPACE deployment/postgres-auth -- \
  pg_dump -U postgres authdb | gzip > "$LOCAL_BACKUP_DIR/authdb_${DATE}.sql.gz"
echo -e "${GREEN}  ✓ authdb backed up${NC}"

echo -e "  Backing up basestationdb..."
kubectl exec -n $NAMESPACE deployment/postgres-basestation -- \
  pg_dump -U postgres basestationdb | gzip > "$LOCAL_BACKUP_DIR/basestationdb_${DATE}.sql.gz"
echo -e "${GREEN}  ✓ basestationdb backed up${NC}"

echo -e "  Backing up notificationdb..."
kubectl exec -n $NAMESPACE deployment/postgres-notification -- \
  pg_dump -U postgres notificationdb | gzip > "$LOCAL_BACKUP_DIR/notificationdb_${DATE}.sql.gz"
echo -e "${GREEN}  ✓ notificationdb backed up${NC}"

echo ""

# Backup MongoDB
echo -e "${YELLOW}Backing up MongoDB...${NC}"

# Get MongoDB password
MONGODB_PASSWORD=$(kubectl get secret -n $NAMESPACE mongodb-secret -o jsonpath='{.data.password}' | base64 -d)
MONGODB_POD=$(kubectl get pod -n $NAMESPACE -l app=mongodb -o jsonpath='{.items[0].metadata.name}')

kubectl exec -n $NAMESPACE $MONGODB_POD -- \
  mongodump --username=admin --password="$MONGODB_PASSWORD" \
  --authenticationDatabase=admin \
  --db=monitoringdb \
  --archive | gzip > "$LOCAL_BACKUP_DIR/monitoringdb_${DATE}.archive.gz"
echo -e "${GREEN}  ✓ monitoringdb backed up${NC}"

echo ""

# Summary
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}✓ Backup Complete!${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
echo -e "Backup location: ${YELLOW}$LOCAL_BACKUP_DIR${NC}"
echo ""
ls -lh "$LOCAL_BACKUP_DIR"
echo ""

# Calculate total size
TOTAL_SIZE=$(du -sh "$LOCAL_BACKUP_DIR" | cut -f1)
echo -e "Total backup size: ${YELLOW}$TOTAL_SIZE${NC}"
echo ""
echo -e "${BLUE}Backup files can be restored using ./scripts/k8s-restore.sh${NC}"
