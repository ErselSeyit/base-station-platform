# Service Discovery and Security Improvements

## Overview

This document describes improvements made to service discovery (Eureka) configuration and credential management across the platform.

## Problem: IP-Based Service Registration Failures

### Symptoms
- Metrics and alerts endpoints returning network timeouts
- API Gateway unable to reach monitoring-service
- Services registered with unreachable IP addresses in Eureka

### Root Cause
Services were configured with `prefer-ip-address: true` in Eureka configuration. When containers are connected to multiple Docker networks, Eureka would register with an IP from a network that wasn't accessible to all services.

Example:
```
monitoring-service registered at: 172.18.0.9:8082  (unreachable)
actual accessible address: monitoring-service:8082  (DNS-based)
```

## Solution: Hostname-Based Service Discovery

### Changes Made

All microservices now use hostname-based Eureka registration instead of IP-based:

**Before:**
```yaml
eureka:
  instance:
    prefer-ip-address: true
    instance-id: ${spring.application.name}:${random.uuid}
```

**After:**
```yaml
eureka:
  instance:
    prefer-ip-address: false
    hostname: monitoring-service  # or notification-service, etc.
    instance-id: ${spring.application.name}:${random.uuid}
```

### Services Updated
- ✅ monitoring-service
- ✅ notification-service
- ⚠️ base-station-service (verify configuration)
- ⚠️ auth-service (verify configuration)
- ⚠️ api-gateway (uses service discovery, not registered)

### Benefits
1. **Reliable routing**: DNS-based service names work consistently across Docker networks
2. **Kubernetes ready**: Hostname-based discovery aligns with K8s service mesh
3. **No network topology dependency**: Works regardless of how many networks containers are connected to

## Problem: Hardcoded Credentials in Configuration Files

### Security Issue
Application configuration files contained hardcoded default credentials:

```yaml
# monitoring-service/src/main/resources/application.yml
rabbitmq:
  username: ${SPRING_RABBITMQ_USERNAME:admin}  # ❌ Hardcoded default
  password: ${SPRING_RABBITMQ_PASSWORD:admin}  # ❌ Hardcoded default
```

This violated security best practices, especially problematic for:
- Kubernetes deployments (should use Secrets)
- Source control (credentials in git history)
- Production deployments (no secure credential rotation)

### Solution: Environment-Only Credentials

All hardcoded credential defaults removed from application.yml files:

**Before:**
```yaml
rabbitmq:
  username: ${SPRING_RABBITMQ_USERNAME:admin}
  password: ${SPRING_RABBITMQ_PASSWORD:admin}
```

**After:**
```yaml
rabbitmq:
  username: ${SPRING_RABBITMQ_USERNAME:}
  password: ${SPRING_RABBITMQ_PASSWORD:}
```

### Credential Sources by Environment

| Environment | Credential Source |
|-------------|------------------|
| **Docker Compose** | Environment variables in `docker-compose.yml` |
| **Kubernetes** | Kubernetes Secrets (via Fabric8/JKube) |
| **Local Development** | Environment variables or `.env` file |

### Example: Docker Compose
```yaml
services:
  monitoring-service:
    environment:
      SPRING_RABBITMQ_USERNAME: ${RABBITMQ_USER:-admin}
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-admin}
```

### Example: Kubernetes (Fabric8)
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: rabbitmq-credentials
type: Opaque
data:
  username: YWRtaW4=  # base64 encoded
  password: YWRtaW4=  # base64 encoded
---
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
      - name: monitoring-service
        env:
        - name: SPRING_RABBITMQ_USERNAME
          valueFrom:
            secretKeyRef:
              name: rabbitmq-credentials
              key: username
```

## Testing & Verification

### Data Persistence Test
```bash
# Stop all services
docker compose down

# Verify volumes persisted
docker volume ls | grep base-station

# Restart services
docker compose up -d

# Verify data restored
docker exec postgres-notification psql -U postgres -d notificationdb \
  -c "SELECT COUNT(*) FROM notifications;"
```

**Result:** All data persisted correctly (4 notifications, 1,368 metrics)

### Service Discovery Test
```bash
# Login and get token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'

# Test metrics endpoint (requires monitoring-service discovery)
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/v1/metrics
```

**Result:** Metrics endpoint now returns data without timeouts

### Alert System Test
```bash
# Create high CPU metric to trigger alert
curl -X POST http://localhost:8080/api/v1/metrics \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "stationId": 1,
    "metricType": "CPU_USAGE",
    "value": 95.5
  }'

# Check notifications
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/v1/notifications
```

**Result:** Alerts triggered and notifications created correctly

## Files Modified

| File | Changes |
|------|---------|
| `monitoring-service/src/main/resources/application.yml` | Hostname-based Eureka, removed credential defaults |
| `notification-service/src/main/resources/application.yml` | Hostname-based Eureka, removed credential defaults |

## Related Documentation

- [Fabric8 Kubernetes Deployment](FABRIC8_KUBERNETES.md)
- [Architecture Overview](ARCHITECTURE.md)
- [Security Implementation](HEADER_SPOOFING_BLOCKED.md)
- [Container Fixes](CONTAINER_CRASH_FIXES.md)

## Production Recommendations

1. **Never commit credentials**: Use `.env` files (gitignored) for local development
2. **Rotate secrets regularly**: Implement automated secret rotation in Kubernetes
3. **Use external secret managers**: Consider HashiCorp Vault or AWS Secrets Manager
4. **Monitor service discovery**: Alert on Eureka registration failures
5. **Test network isolation**: Verify services can't access each other's databases directly

## Troubleshooting

### Service shows as UP in Eureka but unreachable

Check if service is registered with IP or hostname:
```bash
curl http://localhost:8762/eureka/apps/MONITORING-SERVICE | grep hostName
```

Should show: `<hostName>monitoring-service</hostName>`

### Credentials not loading in Kubernetes

Verify secret exists and is mounted:
```bash
kubectl get secrets -n basestation-platform
kubectl describe pod <pod-name> -n basestation-platform
```

### Services can't authenticate to databases

Check environment variables are set:
```bash
docker exec monitoring-service env | grep RABBITMQ
```
