# Quick Start Guide - Live Data Testing

## Prerequisites Check

Before running the simulator, your platform must be deployed and running on Kubernetes (minikube).

### 1. Check if Services are Running

```bash
kubectl get pods -n basestation-platform
```

All pods should show `Running` status.

### 2. Access Points

All external traffic routes through NGINX Ingress at `basestation.local`:

| Service | Access Method |
|---------|---------------|
| Dashboard / API | `http://basestation.local:{ingress-port}` (NGINX Ingress) |
| Grafana | NodePort: `kubectl get svc grafana -n basestation-platform` |
| Prometheus | NodePort: `kubectl get svc prometheus -n basestation-platform` |
| Zipkin | NodePort: `kubectl get svc zipkin -n basestation-platform` |

```bash
# Get the ingress NodePort
kubectl get svc -n ingress-nginx ingress-nginx-controller
```

### 3. Verify API is Accessible

```bash
# Set your password first
export API_PASSWORD='<your-password>'

# Get ingress port
INGRESS_PORT=$(kubectl get svc -n ingress-nginx ingress-nginx-controller \
  -o jsonpath='{.spec.ports[?(@.name=="http")].nodePort}')

# Try to login via ingress
curl -X POST http://basestation.local:$INGRESS_PORT/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"$API_PASSWORD\"}"

# Should return a JWT token
```

## If Services Are NOT Running

### Deploy to Kubernetes

```bash
# 1. Start minikube
minikube start --memory=8192 --cpus=4
minikube addons enable ingress

# 2. Add to /etc/hosts
echo "$(minikube ip)  basestation.local" | sudo tee -a /etc/hosts

# 3. Deploy via Helm
helm install basestation helm/basestation-platform \
  -n basestation-platform --create-namespace

# 4. Wait for all pods to be Running (2-3 minutes)
kubectl get pods -n basestation-platform -w

# 5. Get ingress port and verify
kubectl get svc -n ingress-nginx ingress-nginx-controller
curl http://basestation.local:{ingress-port}/api/v1/stations
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

Auth-service is not running or not accessible.

**Check:**
```bash
kubectl logs -f deployment/auth-service -n basestation-platform
```

**Fix:**
```bash
kubectl rollout restart deployment/auth-service -n basestation-platform
```

### "Connection refused"

API Gateway or ingress is not running.

**Check:**
```bash
kubectl get pod -n basestation-platform -l app=api-gateway
kubectl get ingress -n basestation-platform
kubectl get pods -n ingress-nginx
```

### Database Not Initialized

If auth fails with "user not found":

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

INGRESS_PORT=$(kubectl get svc -n ingress-nginx ingress-nginx-controller \
  -o jsonpath='{.spec.ports[?(@.name=="http")].nodePort}')
MINIKUBE_IP=$(minikube ip)

# 1. Frontend via Ingress
echo -n "Frontend (Ingress): "
curl -sf -o /dev/null -w "%{http_code}" http://$MINIKUBE_IP:$INGRESS_PORT/ && echo " UP" || echo " DOWN"

# 2. Authentication
echo -n "Auth Service: "
TOKEN=$(curl -sf -X POST http://$MINIKUBE_IP:$INGRESS_PORT/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"$API_PASSWORD\"}" \
  | jq -r '.token' 2>/dev/null)

if [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ]; then
  echo "UP (token: ${TOKEN:0:20}...)"
else
  echo "DOWN"
fi

# 3. Prometheus
PROM_PORT=$(kubectl get svc prometheus -n basestation-platform \
  -o jsonpath='{.spec.ports[0].nodePort}')
echo -n "Prometheus: "
curl -sf http://$MINIKUBE_IP:$PROM_PORT/-/healthy > /dev/null && echo "UP" || echo "DOWN"

# 4. Grafana
GRAF_PORT=$(kubectl get svc grafana -n basestation-platform \
  -o jsonpath='{.spec.ports[0].nodePort}')
echo -n "Grafana: "
curl -sf http://$MINIKUBE_IP:$GRAF_PORT/api/health > /dev/null && echo "UP" || echo "DOWN"

echo ""
echo "If all show UP, you're ready to run the simulator!"
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

### Browser: Grafana

Open Grafana at its NodePort URL. Create a dashboard with these queries:
- `signal_strength_dbm`
- `temperature_celsius`
- `throughput_mbps`
- `connected_devices`

### Browser: Prometheus

Open Prometheus at its NodePort URL. Try these queries:
- `avg(signal_strength_dbm)`
- `sum(throughput_mbps)`
- `count(temperature_celsius > 80)`

## Success Indicators

You'll know it's working when:

1. Simulator shows `[AUTH] Successfully authenticated`
2. You see `[SUCCESS] successful_updates` incrementing
3. Grafana graphs are moving (if you created panels)
4. Prometheus shows your custom metrics
5. Logs appear in Loki (Grafana > Explore > Loki)
6. Traces appear in Zipkin

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
