# Quick Start Guide - Docker Desktop

> **Note**: This project uses Docker Desktop with Compose V2. All commands use `docker compose` (without hyphen).

## One-Click Options

### Using Makefile (Recommended)

```bash
# Docker Compose (local development)
make docker_start        # Start everything
make docker_init_db      # Initialize databases
make docker_stop         # Stop everything

# Kubernetes (production)
make k8s_deploy          # Deploy to K8s with Fabric8
make k8s_status          # Check deployment status
make k8s_undeploy        # Remove from K8s
```

See all commands: `make help`

### Using Shell Scripts

```bash
# Kubernetes deployment
./deploy-k8s.sh          # One-click K8s deployment
./undeploy-k8s.sh        # One-click removal
```

## Prerequisites

- Docker Desktop installed and running
- 12-thread CPU (or adjust resource limits accordingly)
- 16GB RAM minimum
- Ports available: 3000, 8080, 8762, 5432-5436, 27017-27018, 6379, 5672, 9090-9411
- **For Kubernetes**: kubectl and Maven installed

## First Time Setup

### 1. Create Environment File

```bash
cat > .env << 'EOF'
# Security Secrets
SECURITY_INTERNAL_SECRET=a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2
JWT_SECRET=VGhpc0lzQVZlcnlTZWN1cmVKV1RTZWNyZXRLZXlGb3JEZXZlbG9wbWVudA==

# Database Credentials (development only)
POSTGRES_BASESTATION_DB=basestationdb
POSTGRES_BASESTATION_USER=postgres
POSTGRES_BASESTATION_PASSWORD=postgres

POSTGRES_NOTIFICATION_DB=notificationdb
POSTGRES_NOTIFICATION_USER=postgres
POSTGRES_NOTIFICATION_PASSWORD=postgres

POSTGRES_AUTH_DB=authdb
POSTGRES_AUTH_USER=postgres
POSTGRES_AUTH_PASSWORD=postgres

MONGODB_USER=admin
MONGODB_PASSWORD=admin

RABBITMQ_USER=admin
RABBITMQ_PASSWORD=admin

KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=admin

GRAFANA_PASSWORD=admin
EOF
```

### 2. Apply Resource Limits (Optional but Recommended)

See `docker-compose.optimized.yml` for reference and apply resource limits to your `docker-compose.yml`:

```yaml
# Example for each Java service
environment:
  JAVA_OPTS: "-Xms256m -Xmx512m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC"
deploy:
  resources:
    limits:
      cpus: '1.0'
      memory: 640M
    reservations:
      cpus: '0.5'
      memory: 320M
```

See [docs/RESOURCE_OPTIMIZATION.md](docs/RESOURCE_OPTIMIZATION.md) for full details.

### 3. Start All Services

```bash
# Start all containers in detached mode
docker compose up -d

# Monitor startup in Docker Desktop:
# 1. Open Docker Desktop
# 2. Go to "Containers" tab
# 3. Watch for all containers to show "Running" or "Healthy"
```

**Expected startup time**: 2-3 minutes (with resource limits) or 5-10 minutes (without)

### 4. Wait for Services to Initialize

Check that all services are healthy:

```bash
# Check Eureka dashboard
curl -s http://localhost:8762/eureka/apps | grep -E "app>|status>"

# Expected output: All services showing <status>UP</status>
```

### 5. Initialize Databases (Manual - One Time Only)

**PostgreSQL (Auth)**:
```bash
docker exec -i postgres-auth psql -U postgres -d authdb < init-db/auth-seed.sql
```

**PostgreSQL (Base Stations)** - Optional, for demo data:
```bash
docker exec -i postgres-basestation psql -U postgres -d basestationdb < init-db/basestation-seed.sql
```

**MongoDB (Metrics)** - Optional, for demo data:
```bash
docker cp init-db/mongodb-seed.js mongodb:/tmp/
docker exec mongodb mongosh --username admin --password admin --authenticationDatabase admin --file /tmp/mongodb-seed.js
```

### 6. Verify System is Running

```bash
# Test authentication
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'

# Should return: {"token":"...", "username":"admin", "role":"ROLE_ADMIN"}
```

### 7. Access the Application

- **Frontend**: http://localhost:3000
- **API Gateway**: http://localhost:8080
- **Eureka Dashboard**: http://localhost:8762
- **Grafana**: http://localhost:3001 (admin/admin)
- **Prometheus**: http://localhost:9090
- **RabbitMQ Management**: http://localhost:15672 (admin/admin)
- **Keycloak**: http://localhost:8090 (admin/admin)

## Daily Workflow

### Start System

```bash
docker compose up -d
```

Or use Docker Desktop GUI:
1. Open Docker Desktop
2. Go to "Containers"
3. Find `base-station-platform` project
4. Click "Start"

### Stop System

```bash
docker compose down
```

Or use Docker Desktop GUI: Click "Stop"

### View Logs

**Docker Desktop GUI** (Recommended):
1. Open Docker Desktop
2. Go to "Containers"
3. Click on any container
4. View logs in real-time

**CLI**:
```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f base-station-service

# Last 100 lines
docker compose logs --tail=100 base-station-service
```

### Monitor Resources

**Docker Desktop GUI** (Recommended):
1. Open Docker Desktop Dashboard
2. Go to "Containers" tab
3. View real-time CPU/Memory graphs

**CLI**:
```bash
docker stats
```

### Restart a Single Service

```bash
docker compose restart base-station-service
```

Or use Docker Desktop: Click container → "Restart"

## Troubleshooting

### Problem: Container Crashes with OOM

**Check**:
```bash
docker inspect base-station-service | jq '.[0].State.OOMKilled'
# If true, the container was killed due to out of memory
```

**Fix**: Increase memory limit in `docker-compose.yml`:
```yaml
deploy:
  resources:
    limits:
      memory: 768M  # Increased from 640M
```

### Problem: Service Won't Start

**Check logs**:
```bash
docker compose logs base-station-service
```

**Common causes**:
- Database not ready: Wait for health check
- Port conflict: Check if port is already in use
- Missing environment variable: Check `.env` file

**Fix**: Restart with clean state:
```bash
docker compose down
docker compose up -d
```

### Problem: Can't Connect to Database

**Verify database is running**:
```bash
docker compose ps postgres-basestation
# Should show "healthy"
```

**Check network connectivity**:
```bash
docker compose exec base-station-service ping postgres-basestation
```

### Problem: High CPU Usage

**Monitor in Docker Desktop**:
- Check which containers use most CPU
- Verify resource limits are applied

**Quick fix**: Restart overloaded service
```bash
docker compose restart <service-name>
```

**Long-term fix**: Apply resource limits from `docker-compose.optimized.yml`

## Clean Reset (Nuclear Option)

If everything is broken:

```bash
# Stop and remove all containers, networks, volumes
docker compose down -v

# Remove all images (optional)
docker compose down --rmi all

# Rebuild everything from scratch
docker compose build --no-cache
docker compose up -d

# Re-initialize databases (see step 5 above)
```

## Resource Monitoring Best Practices

1. **Use Docker Desktop Dashboard** - Visual graphs are easier than CLI
2. **Check CPU/Memory trends** - Spikes are normal, sustained high usage is not
3. **Monitor during startup** - Should peak then stabilize within 5 minutes
4. **Set up alerts** - Docker Desktop can notify on container failures

## Useful Commands

```bash
# Check all container status
docker compose ps

# View resource usage
docker stats --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}"

# Execute command in container
docker compose exec base-station-service bash

# View network configuration
docker network inspect base-station-platform_app-network

# Prune unused resources
docker system prune -f
```

## Next Steps

- [ ] Explore API docs: http://localhost:8080/swagger-ui.html
- [ ] Create test base stations via Frontend
- [ ] Check metrics dashboard in Grafana
- [ ] Review resource usage in Docker Desktop
- [ ] Read full docs in `docs/` folder

## Documentation

- **Resource Optimization**: [docs/RESOURCE_OPTIMIZATION.md](docs/RESOURCE_OPTIMIZATION.md)
- **Kubernetes Deployment**: [docs/FABRIC8_KUBERNETES.md](docs/FABRIC8_KUBERNETES.md)
- **Container Troubleshooting**: [docs/CONTAINER_CRASH_FIXES.md](docs/CONTAINER_CRASH_FIXES.md)
- **Architecture**: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

## Support

- Check Docker Desktop Dashboard for container health
- View logs in Docker Desktop or via `docker compose logs`
- Monitor resource usage to prevent OOM
- Consult docs in `docs/` folder for detailed guides
