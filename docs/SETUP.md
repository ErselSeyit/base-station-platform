# Setup Guide

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker 20.10+
- Minikube with NGINX Ingress addon
- `kubectl` CLI
- Helm 3.x
- Node.js 18+ (for frontend development)

## Quick Start (Kubernetes)

Start minikube and deploy:
```bash
minikube start --memory=8192 --cpus=4
minikube addons enable ingress
helm install basestation helm/basestation-platform -n basestation-platform --create-namespace
```

Add the ingress host to `/etc/hosts`:
```bash
echo "$(minikube ip)  basestation.local" | sudo tee -a /etc/hosts
```

Access the application:
- **Dashboard**: `http://basestation.local:{ingress-port}` (check `kubectl get svc -n ingress-nginx`)
- **API**: Via NGINX Ingress at `/api`

Stop services:
```bash
helm uninstall basestation -n basestation-platform
minikube stop
```

## Local Development

### Build All Services
```bash
# Build without tests
mvn clean install -DskipTests

# Build with tests
mvn clean install
```

### Run Individual Services
```bash
cd base-station-service
mvn spring-boot:run
```

### Seed Data
Initialize databases with demo data:
```bash
make k8s_init_db
```

### View Logs
```bash
# Specific service
kubectl logs -f deployment/monitoring-service -n basestation-platform

# Follow all pods for a service
kubectl logs -f -l app=base-station-service -n basestation-platform
```

### Rebuild and Redeploy Service
```bash
# Rebuild the image inside minikube's Docker daemon
eval $(minikube docker-env)
docker build -t monitoring-service:latest monitoring-service/

# Restart the deployment to pick up the new image
kubectl rollout restart deployment/monitoring-service -n basestation-platform
```

## Security Configuration

All credentials are provided via Kubernetes Secrets - no hardcoded defaults.

### Required Secrets

| Secret Key | Description |
|------------|-------------|
| `POSTGRES_USER` | PostgreSQL username |
| `POSTGRES_PASSWORD` | PostgreSQL password |
| `MONGODB_USER` | MongoDB username |
| `MONGODB_PASSWORD` | MongoDB password |
| `RABBITMQ_USER` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | RabbitMQ password |
| `JWT_SECRET` | JWT signing secret (min 32 chars) |
| `AUTH_ADMIN_PASSWORD` | Admin user password (min 12 chars) |
| `SECURITY_INTERNAL_SECRET` | HMAC secret for service auth |
| `GRAFANA_PASSWORD` | Grafana admin password |

Generate secrets:
```bash
./k8s/generate-secrets.sh | kubectl apply -f -
```

### Optional Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | validate | JPA schema mode (use 'update' for initial setup) |
| `CORS_ALLOWED_ORIGINS` | http://localhost:3000 | API Gateway CORS |
| `WEBSOCKET_ALLOWED_ORIGINS` | * | WebSocket CORS |
| `TRACING_SAMPLE_PROBABILITY` | 0.1 | Tracing sample rate (0-1) |
| `DIAGNOSTIC_REQUIRE_AUTH` | false | Enforce HMAC on AI diagnostic |

## Service Discovery

The platform uses **Kubernetes DNS** for service discovery. Services reference each other by service name within the cluster:
- `http://auth-service:8084`
- `http://base-station-service:8081`
- `http://monitoring-service:8082`
- `http://notification-service:8083`
- `http://tmf-api:8086`
- `http://ai-diagnostic:9091`

External access is handled by **NGINX Ingress**, which routes:
- `/` → `frontend:80` (React dashboard)
- `/api` → `api-gateway:8080` (REST API)
- `/ws` → `monitoring-service:8082` (WebSocket)

## Database Architecture

Single PostgreSQL instance with multiple databases:
- `authdb` - User authentication
- `basestationdb` - Base station data
- `notificationdb` - Notifications

MongoDB for metrics:
- `monitoringdb` - Time-series metrics

## Troubleshooting

### Services won't start
```bash
kubectl get pods -n basestation-platform
kubectl describe pod <pod-name> -n basestation-platform
kubectl logs <pod-name> -n basestation-platform
```

### Port conflicts
```bash
# Check what's using a port
lsof -i :8080
```

### Restart all services
```bash
kubectl rollout restart deployment -n basestation-platform
```

### Database connection issues
```bash
# Check PostgreSQL
kubectl exec deployment/postgres -n basestation-platform -- psql -U postgres -c '\l'

# Check MongoDB
kubectl exec deployment/mongodb -n basestation-platform -- mongosh --quiet --eval "show dbs"
```
