# Kubernetes Deployment Guide

This directory contains Kubernetes manifests for deploying the Base Station Platform.

## Prerequisites

- Kubernetes cluster (v1.24+)
- kubectl configured
- Docker images built and pushed to registry

## Deployment Steps

1. Create namespace:
```bash
kubectl apply -f namespace.yaml
```

2. Create secrets:
```bash
kubectl create secret generic postgres-secrets \
  --from-literal=password=your-secure-password \
  -n basestation-platform
```

3. Deploy databases:
```bash
kubectl apply -f postgres-basestation.yaml
kubectl apply -f postgres-notification.yaml
kubectl apply -f postgres-auth.yaml
kubectl apply -f mongodb.yaml
kubectl apply -f redis.yaml
kubectl apply -f rabbitmq.yaml
```

4. Deploy infrastructure:
```bash
kubectl apply -f eureka-server.yaml
kubectl apply -f zipkin.yaml
kubectl apply -f prometheus.yaml
kubectl apply -f grafana.yaml
```

5. Deploy services:
```bash
kubectl apply -f base-station-service.yaml
kubectl apply -f monitoring-service.yaml
kubectl apply -f notification-service.yaml
kubectl apply -f auth-service.yaml
kubectl apply -f api-gateway.yaml
```

6. Deploy frontend:
```bash
kubectl apply -f frontend.yaml
```

## Using Helm

```bash
helm install basestation-platform ./helm/base-station-platform \
  --namespace basestation-platform \
  --create-namespace
```

## Access Services

- API Gateway: `kubectl port-forward svc/api-gateway 8080:80 -n basestation-platform`
- Grafana: `kubectl port-forward svc/grafana 3001:3000 -n basestation-platform`
- Prometheus: `kubectl port-forward svc/prometheus 9090:9090 -n basestation-platform`

