# Quick Start Guide

## Prerequisites

- Minikube installed with the NGINX Ingress addon enabled
- `kubectl` configured to use the minikube cluster
- 8GB RAM minimum (12GB recommended for minikube)
- Add `basestation.local` to `/etc/hosts` pointing to the minikube IP

```bash
# Get minikube IP and add to hosts
echo "$(minikube ip)  basestation.local" | sudo tee -a /etc/hosts
```

## Deployment

```bash
# Start minikube (if not running)
minikube start --memory=8192 --cpus=4

# Enable ingress addon
minikube addons enable ingress

# Deploy via Helm
helm install basestation helm/basestation-platform -n basestation-platform --create-namespace

# Wait for all pods to be ready
kubectl get pods -n basestation-platform -w
```

## Access Points

All traffic routes through the NGINX Ingress at `basestation.local`. In the default single-domain mode, `/api` routes directly to the API Gateway.

| Service | URL | Credentials |
|---------|-----|-------------|
| **Dashboard** | http://basestation.local:{ingress-port} | admin / (from K8s secret) |
| **Grafana** | NodePort (see `kubectl get svc grafana`) | admin / (from K8s secret) |
| **Prometheus** | NodePort (see `kubectl get svc prometheus`) | - |
| **Zipkin** | NodePort (see `kubectl get svc zipkin`) | - |

```bash
# Get the NGINX Ingress NodePort
kubectl get svc -n ingress-nginx ingress-nginx-controller

# Get monitoring service ports
kubectl get svc -n basestation-platform grafana prometheus zipkin
```

### Ingress Routes

| Path | Backend Service | Description |
|------|----------------|-------------|
| `/` | frontend:80 | React dashboard (SPA) |
| `/api` | api-gateway:8080 | REST API |
| `/ws` | monitoring-service:8082 | WebSocket streaming |

## Secret Management

Secrets are managed via Kubernetes Secrets (not `.env` files):

```bash
# Generate and apply secrets
./k8s/generate-secrets.sh | kubectl apply -f -

# Or for production, use Sealed Secrets
# See docs/SECRET_MANAGEMENT.md
```

## Daily Workflow

### Check Status
```bash
kubectl get pods -n basestation-platform
```

### View Logs
```bash
# Specific service
kubectl logs -f deployment/base-station-service -n basestation-platform

# All pods
kubectl logs -f -l app=monitoring-service -n basestation-platform
```

### Restart Service
```bash
kubectl rollout restart deployment/monitoring-service -n basestation-platform
```

## Troubleshooting

### Services won't start
```bash
kubectl get pods -n basestation-platform
kubectl describe pod <pod-name> -n basestation-platform
kubectl logs <pod-name> -n basestation-platform
```

### Login fails
Check auth-service is running and database is initialized:
```bash
kubectl logs deployment/auth-service -n basestation-platform
```

### Clean restart
```bash
kubectl rollout restart deployment -n basestation-platform
```

## Architecture

The platform uses:
- **Kubernetes DNS** for service discovery (no Eureka)
- **NGINX Ingress** for external routing and path-based proxying
- **Consolidated PostgreSQL** (single instance with separate databases)
- **HMAC authentication** for service-to-service calls

See [ARCHITECTURE.md](ARCHITECTURE.md) for details.

## Next Steps

- Explore the dashboard at `http://basestation.local:{ingress-port}`
- Check metrics in Grafana via its NodePort
- Read API docs at [API.md](API.md)
