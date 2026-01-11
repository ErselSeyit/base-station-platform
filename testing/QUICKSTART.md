# Quick Start Guide - Live Data Testing

## Prerequisites Check

Before running the simulator, your platform must be deployed and running. Here's how to check:

### 1. Check if Services are Running

**For Docker Compose:**
```bash
cd /home/siyu/base-station-platform
docker-compose ps
```

**For Kubernetes:**
```bash
kubectl get pods -n basestation-platform
```

All pods should show `Running` status.

### 2. Verify API Gateway is Accessible

```bash
# Test if API Gateway responds
curl http://localhost:30080/actuator/health

# Should return: {"status":"UP"}
```

### 3. Test Authentication

```bash
# Try to login
curl -X POST http://localhost:30080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# Should return a JWT token
```

## If Services Are NOT Running

### Option 1: Start with Docker Compose (Quickest)

```bash
cd /home/siyu/base-station-platform

# Start all services
docker-compose up -d

# Wait for services to be healthy (2-3 minutes)
docker-compose ps

# Check logs if any service fails
docker-compose logs -f api-gateway
```

### Option 2: Deploy to Kubernetes

```bash
cd /home/siyu/base-station-platform

# 1. Create namespace
kubectl create namespace basestation-platform

# 2. Generate and apply secrets
./k8s/generate-secrets.sh
kubectl apply -f k8s/secrets.yaml

# 3. Apply PVCs
kubectl apply -f k8s/persistent-volumes.yaml

# 4. Apply init ConfigMaps
kubectl apply -f k8s/init-configmaps.yaml

# 5. Deploy databases (will auto-initialize)
kubectl apply -f k8s/databases.yaml

# 6. Wait for databases to be ready (2-3 minutes)
kubectl get pods -n basestation-platform -w

# 7. Deploy applications
kubectl apply -f k8s/app-services.yaml

# 8. Deploy monitoring stack
kubectl apply -f k8s/monitoring-stack.yaml

# 9. Wait for all pods to be Running
kubectl get pods -n basestation-platform

# 10. Verify API Gateway is accessible
curl http://localhost:30080/actuator/health
```

## Now Run the Simulator!

Once services are confirmed running:

### Quick Test (5 minutes)
```bash
python3 testing/live-data-simulator.py \
  --stations 10 \
  --duration 5 \
  --scenario normal
```

### Long Running Test
```bash
python3 testing/live-data-simulator.py \
  --stations 20 \
  --scenario peak_hours \
  --interval 3
```

### Load Test
```bash
python3 testing/live-data-simulator.py \
  --stations 100 \
  --interval 2 \
  --concurrent \
  --duration 10
```

## Troubleshooting

### "Login failed: 401"

This means the auth-service is not running or not accessible.

**Check:**
```bash
# Docker Compose
docker-compose logs auth-service

# Kubernetes
kubectl logs -f deployment/auth-service -n basestation-platform
```

**Fix:**
```bash
# Docker Compose - restart auth-service
docker-compose restart auth-service

# Kubernetes - restart pod
kubectl rollout restart deployment/auth-service -n basestation-platform
```

### "Connection refused"

API Gateway is not running.

**Check:**
```bash
# Docker Compose
docker-compose ps | grep api-gateway

# Kubernetes
kubectl get pod -n basestation-platform -l app=api-gateway
```

### Database Not Initialized

If auth fails with "user not found":

**Docker Compose:**
```bash
# Re-run database initialization
docker-compose exec postgres-auth psql -U postgres -d authdb -f /docker-entrypoint-initdb.d/auth-seed.sql
```

**Kubernetes:**
```bash
# Database should auto-initialize via init containers
# If it didn't, manually run:
make k8s_init_db
```

## Verify Everything is Working

Run this comprehensive check:

```bash
#!/bin/bash
echo "Checking Base Station Platform..."

# 1. API Gateway
echo -n "API Gateway: "
curl -sf http://localhost:30080/actuator/health > /dev/null && echo "✓ UP" || echo "✗ DOWN"

# 2. Authentication
echo -n "Auth Service: "
TOKEN=$(curl -sf -X POST http://localhost:30080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | jq -r '.token' 2>/dev/null)

if [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ]; then
  echo "✓ UP (token: ${TOKEN:0:20}...)"
else
  echo "✗ DOWN"
fi

# 3. Prometheus
echo -n "Prometheus: "
curl -sf http://localhost:30090/-/healthy > /dev/null && echo "✓ UP" || echo "✗ DOWN"

# 4. Grafana
echo -n "Grafana: "
curl -sf http://localhost:30300/api/health > /dev/null && echo "✓ UP" || echo "✗ DOWN"

# 5. Zipkin
echo -n "Zipkin: "
curl -sf http://localhost:30411/health > /dev/null && echo "✓ UP" || echo "✗ DOWN"

echo ""
echo "If all show ✓ UP, you're ready to run the simulator!"
```

Save this as `check-services.sh`, make it executable, and run it:

```bash
chmod +x check-services.sh
./check-services.sh
```

## Once Everything is UP

### Terminal 1: Start Simulator
```bash
python3 testing/live-data-simulator.py --stations 20 --scenario peak_hours
```

### Terminal 2: Watch Logs
```bash
tail -f /tmp/simulator.log
```

### Browser 1: Grafana
```
http://localhost:30300
Login: admin/admin

Create a dashboard with these queries:
- signal_strength_dbm
- temperature_celsius
- throughput_mbps
- connected_devices
```

### Browser 2: Prometheus
```
http://localhost:30090

Try these queries:
- avg(signal_strength_dbm)
- sum(throughput_mbps)
- count(temperature_celsius > 80)
```

### Browser 3: Zipkin
```
http://localhost:30411

Watch distributed traces appear in real-time
```

## Success Indicators

You'll know it's working when:

1. ✅ Simulator shows `[AUTH] Successfully authenticated`
2. ✅ You see `[SUCCESS] successful_updates` incrementing
3. ✅ Grafana graphs are moving (if you created panels)
4. ✅ Prometheus shows your custom metrics
5. ✅ Logs appear in Loki (Grafana → Explore → Loki)
6. ✅ Traces appear in Zipkin

## Sample Output (Success)

```
[INIT] Creating 20 base stations...
[INIT] 20 stations ready
[AUTH] Successfully authenticated
[START] Simulation started at 14:32:15

================================================================================
[SUMMARY] Scenario: PEAK_HOURS | Updates: 200 | Success: 198 | Failed: 2 | Alerts: 3
================================================================================
  ONLINE: 18 | DEGRADED: 1 | OFFLINE: 1
  Avg Signal: -67.3 dBm | Avg Temp: 52.1°C | Avg Throughput: 234.5 Mbps | Total Devices: 980

[ALERT] Simulated Station 5: High temperature: 85.2°C (threshold: 80°C)
[FAILURE] Simulated Station 12 went offline!
[RECOVERY] Simulated Station 12 back online
```

Happy Testing! 🚀
