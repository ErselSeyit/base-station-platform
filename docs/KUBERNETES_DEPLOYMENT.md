# Kubernetes Deployment Guide

Complete guide for deploying the Base Station Platform to Kubernetes with manual manifests and database initialization.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Detailed Steps](#detailed-steps)
- [Database Initialization](#database-initialization)
- [Service Configuration](#service-configuration)
- [Troubleshooting](#troubleshooting)
- [Verification](#verification)

---

## Prerequisites

- **Kubernetes cluster** running (Docker Desktop, Minikube, kind, etc.)
- **kubectl** installed and configured
- **Maven** (for building Docker images)
- **Docker** (for building images)
- Ports available: 30000 (frontend), 30080 (API gateway)

### Check Prerequisites

```bash
# Verify kubectl
kubectl version --client

# Verify cluster connection
kubectl cluster-info

# Verify Docker
docker --version
```

---

## Quick Start

```bash
# 1. Generate secrets
./k8s/generate-secrets.sh

# 2. Build Docker images
mvn clean package -DskipTests
docker compose build

# 3. Deploy to Kubernetes
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/databases.yaml
kubectl apply -f k8s/app-services.yaml

# 4. Initialize databases
make k8s_init_db

# 5. Access application
# Frontend: http://localhost:30000
# API Gateway: http://localhost:30080
# Login: admin/admin
```

---

## Detailed Steps

### 1. Create Namespace and Secrets

```bash
# Create namespace
kubectl apply -f k8s/namespace.yaml

# Generate secrets (if not already done)
./k8s/generate-secrets.sh

# Apply secrets
kubectl apply -f k8s/secrets.yaml

# Verify secrets
kubectl get secrets -n basestation-platform
```

**Expected output:**
```
NAME                       TYPE     DATA   AGE
grafana-secret             Opaque   1      1m
jwt-secret                 Opaque   1      1m
mongodb-secret             Opaque   2      1m
postgres-secrets           Opaque   6      1m
rabbitmq-secret            Opaque   2      1m
security-internal-secret   Opaque   1      1m
```

### 2. Build Docker Images

The Kubernetes deployment uses `imagePullPolicy: Never`, which means images must be available locally.

```bash
# Build application JARs
mvn clean package -DskipTests

# Build Docker images
docker compose build
```

**Images built:**
- `base-station-platform-frontend:latest`
- `base-station-platform-auth-service:latest`
- `base-station-platform-base-station-service:latest`
- `base-station-platform-monitoring-service:latest`
- `base-station-platform-notification-service:latest`
- `base-station-platform-api-gateway:latest`
- `base-station-platform-eureka-server:latest`

**Verify images:**
```bash
docker images | grep base-station-platform
```

### 3. Deploy Databases

Deploy PostgreSQL, MongoDB, Redis, and RabbitMQ:

```bash
kubectl apply -f k8s/databases.yaml

# Watch deployment progress
kubectl get pods -n basestation-platform -w
```

**Expected pods:**
- `postgres-auth-*`
- `postgres-basestation-*`
- `postgres-notification-*`
- `mongodb-*`
- `redis-*`
- `rabbitmq-*`

### 4. Deploy Application Services

```bash
kubectl apply -f k8s/app-services.yaml

# Check deployment status
kubectl get deployments -n basestation-platform
```

**Expected deployments:**
- `eureka-server`
- `auth-service`
- `base-station-service`
- `monitoring-service`
- `notification-service`
- `api-gateway`
- `frontend`

### 5. Verify All Pods are Running

```bash
kubectl get pods -n basestation-platform

# All pods should show "Running" status
```

---

## Database Initialization

**CRITICAL:** Kubernetes deployments start with empty databases. You must seed them manually.

### Option 1: Using Makefile (Recommended)

```bash
make k8s_init_db
```

### Option 2: Manual Initialization

#### PostgreSQL - Auth Database (Required)

```bash
kubectl exec -i -n basestation-platform deployment/postgres-auth -- \
  psql -U postgres -d authdb < init-db/auth-seed.sql
```

Creates users:
- **admin/admin** (ROLE_ADMIN)
- **user/user** (ROLE_USER)

#### PostgreSQL - Base Station Database (Optional - Demo Data)

```bash
kubectl exec -i -n basestation-platform deployment/postgres-basestation -- \
  psql -U postgres -d basestationdb < init-db/basestation-seed.sql
```

Creates 26 base stations across major Chinese cities.

#### MongoDB - Metrics Database (Required for Metrics Page)

```bash
# Copy seed script to pod
kubectl cp init-db/mongodb-seed.js \
  basestation-platform/$(kubectl get pod -n basestation-platform -l app=mongodb -o jsonpath='{.items[0].metadata.name}'):/tmp/mongodb-seed.js

# Execute seed script
kubectl exec -n basestation-platform deployment/mongodb -- \
  mongosh -u admin -p NiatPcrSdcvGk2ubuWg3NIuFTWRmSLLy \
  --authenticationDatabase admin /tmp/mongodb-seed.js
```

Creates 1,365 metric records for 8 stations over 24 hours.

#### PostgreSQL - Notification Database (Optional - Demo Data)

```bash
kubectl exec -i -n basestation-platform deployment/postgres-notification -- \
  psql -U postgres -d notificationdb < init-db/notification-seed.sql
```

Creates 18 sample notifications.

### Verify Database Initialization

```bash
# Check auth users
kubectl exec -n basestation-platform deployment/postgres-auth -- \
  psql -U postgres -d authdb -c "SELECT username, role FROM users;"

# Check base stations count
kubectl exec -n basestation-platform deployment/postgres-basestation -- \
  psql -U postgres -d basestationdb -c "SELECT COUNT(*) FROM base_station;"

# Check metrics count
kubectl exec -n basestation-platform deployment/mongodb -- \
  mongosh -u admin -p NiatPcrSdcvGk2ubuWg3NIuFTWRmSLLy \
  --authenticationDatabase admin --eval "db.getSiblingDB('monitoringdb').metric_data.countDocuments({})"
```

---

## Service Configuration

### Service Discovery

**Eureka is disabled** in Kubernetes. Services use Kubernetes DNS:

- `auth-service:8084`
- `base-station-service:8081`
- `monitoring-service:8082`
- `notification-service:8083`

### API Gateway Routes

The API Gateway uses environment variable-based routing:

```yaml
SPRING_CLOUD_GATEWAY_ROUTES_0_URI: http://auth-service:8084
SPRING_CLOUD_GATEWAY_ROUTES_1_URI: http://base-station-service:8081
SPRING_CLOUD_GATEWAY_ROUTES_2_URI: http://monitoring-service:8082
SPRING_CLOUD_GATEWAY_ROUTES_3_URI: http://notification-service:8083
```

### CORS Configuration

Frontend access is allowed from:
- `http://localhost:3000`
- `http://localhost:80`
- `http://localhost`
- `http://localhost:30000` (Kubernetes NodePort)

### Database Names

**IMPORTANT:** Database names differ from Docker Compose:

| Service | Database Name |
|---------|--------------|
| Auth Service | `authdb` |
| Base Station Service | `basestationdb` |
| Notification Service | `notificationdb` |
| Monitoring Service | **`monitoringdb`** (not `metrics`) |

---

## Troubleshooting

### Pod Not Starting

```bash
# Check pod status
kubectl get pods -n basestation-platform

# View pod logs
kubectl logs -n basestation-platform deployment/<service-name>

# Describe pod for events
kubectl describe pod -n basestation-platform <pod-name>
```

### Image Pull Errors

If you see `ImagePullBackOff`:

```bash
# Ensure images are built locally
docker images | grep base-station-platform

# Rebuild if needed
docker compose build
```

### Database Connection Issues

```bash
# Check database pod logs
kubectl logs -n basestation-platform deployment/postgres-auth

# Test database connectivity from service
kubectl exec -n basestation-platform deployment/auth-service -- \
  nc -zv postgres-auth 5432
```

### Login Returns 503 Service Unavailable

**Cause:** Service port mismatch or database not initialized.

**Fix:**
1. Verify services are using correct ports (see Service Configuration above)
2. Initialize auth database:
```bash
kubectl exec -i -n basestation-platform deployment/postgres-auth -- \
  psql -U postgres -d authdb < init-db/auth-seed.sql
```

### Metrics Page Shows "No metrics data available"

**Cause:** MongoDB not seeded or wrong database name.

**Fix:**
1. Verify monitoring service uses `monitoringdb`:
```bash
kubectl get deployment -n basestation-platform monitoring-service -o yaml | grep MONGODB_DATABASE
```

2. Seed MongoDB:
```bash
kubectl cp init-db/mongodb-seed.js basestation-platform/$(kubectl get pod -n basestation-platform -l app=mongodb -o jsonpath='{.items[0].metadata.name}'):/tmp/mongodb-seed.js
kubectl exec -n basestation-platform deployment/mongodb -- mongosh -u admin -p NiatPcrSdcvGk2ubuWg3NIuFTWRmSLLy --authenticationDatabase admin /tmp/mongodb-seed.js
```

### CORS Errors from Frontend

**Symptom:** 403 Forbidden errors in browser console.

**Cause:** Frontend origin not in CORS allowed origins.

**Fix:** Verify CORS configuration includes `http://localhost:30000`:
```bash
kubectl get deployment -n basestation-platform api-gateway -o yaml | grep CORS_ALLOWED_ORIGINS
```

---

## Verification

### Test Authentication

```bash
# From inside cluster
kubectl exec -n basestation-platform deployment/frontend -- \
  wget -q -O- --post-data='{"username":"admin","password":"admin"}' \
  --header="Content-Type: application/json" \
  http://api-gateway:8080/api/v1/auth/login

# Expected: {"token":"...","username":"admin","role":"ROLE_ADMIN"}
```

### Test Metrics API

```bash
# Get token first, then test metrics
TOKEN=$(kubectl exec -i -n basestation-platform deployment/frontend -- \
  wget -q -O- --post-data='{"username":"admin","password":"admin"}' \
  --header="Content-Type: application/json" \
  http://api-gateway:8080/api/v1/auth/login | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

kubectl exec -i -n basestation-platform deployment/frontend -- \
  wget -q -O- --header="Authorization: Bearer $TOKEN" \
  "http://api-gateway:8080/api/v1/metrics" | head -c 200

# Should return JSON array of metrics
```

### Access Frontend

Open browser to: **http://localhost:30000**

Login with:
- Username: `admin`
- Password: `admin`

---

## Useful Commands

### View All Resources

```bash
kubectl get all -n basestation-platform
```

### Check Service Endpoints

```bash
kubectl get svc -n basestation-platform
```

### View Logs

```bash
# All logs from a service
kubectl logs -n basestation-platform deployment/api-gateway

# Follow logs
kubectl logs -f -n basestation-platform deployment/auth-service

# Last 50 lines
kubectl logs --tail=50 -n basestation-platform deployment/monitoring-service
```

### Restart a Service

```bash
kubectl rollout restart deployment/<service-name> -n basestation-platform
```

### Scale a Service

```bash
kubectl scale deployment/<service-name> -n basestation-platform --replicas=2
```

### Port Forward (for debugging)

```bash
# Access API gateway locally on port 8080
kubectl port-forward -n basestation-platform svc/api-gateway 8080:8080

# Access MongoDB locally
kubectl port-forward -n basestation-platform svc/mongodb 27017:27017
```

### Clean Up

```bash
# Delete all resources
kubectl delete namespace basestation-platform

# Or use individual files
kubectl delete -f k8s/app-services.yaml
kubectl delete -f k8s/databases.yaml
kubectl delete -f k8s/secrets.yaml
kubectl delete -f k8s/namespace.yaml
```

---

## Next Steps

- Monitor resource usage: `kubectl top pods -n basestation-platform`
- Check application logs for errors
- Test all features through the frontend
- Review [TECH_DEBT.md](TECH_DEBT.md) for known issues and improvements

---

## Related Documentation

- [README.md](README.md) - Project overview
- [QUICK_START.md](QUICK_START.md) - Docker Compose quickstart
- [TECH_DEBT.md](TECH_DEBT.md) - Known issues and technical debt
- [Makefile](Makefile) - Automation commands
