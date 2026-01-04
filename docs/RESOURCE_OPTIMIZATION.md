# Resource Optimization for 12-Thread CPU

This document explains the resource allocation strategy for running the Base Station Platform on a 12-thread CPU system.

## System Specifications

- **CPU**: 12 physical cores (likely 24 logical cores with Hyper-Threading)
- **Target**: Prevent OOM crashes and CPU contention during startup
- **Containers**: 17 services total

## Resource Distribution Strategy

###  Total Allocation (Assuming 16GB RAM)

| Category | Services | CPU Allocation | RAM Allocation |
|----------|----------|----------------|----------------|
| **Databases** | PostgreSQL (×3), MongoDB, Redis, RabbitMQ | 3-4 cores | 2.5 GB |
| **Java Services** | 6 microservices + Eureka + Gateway | 6-7 cores | 4.0 GB |
| **Infrastructure** | Grafana, Prometheus, Zipkin, Keycloak, Frontend | 2-3 cores | 1.5 GB |
| **OS Reserve** | System processes | 1-2 cores | 1.0 GB |

## Per-Service Resource Limits

### Java Microservices

| Service | Heap (Xms/Xmx) | CPU Limit | Memory Limit | CPU Reserve | Memory Reserve |
|---------|----------------|-----------|--------------|-------------|----------------|
| **eureka-server** | 128m / 256m | 0.5 | 384M | 0.25 | 192M |
| **api-gateway** | 512m / 1024m | 1.5 | 1280M | 0.75 | 640M |
| **auth-service** | 256m / 512m | 1.0 | 640M | 0.5 | 320M |
| **base-station-service** | 256m / 512m | 1.0 | 640M | 0.5 | 320M |
| **monitoring-service** | 512m / 768m | 1.0 | 960M | 0.5 | 480M |
| **notification-service** | 256m / 512m | 1.0 | 640M | 0.5 | 320M |

**Rationale**:
- API Gateway needs more resources (handles all traffic, rate limiting, JWT validation)
- Monitoring Service processes high-volume metrics data
- Others are standard CRUD services with moderate load

### Databases

| Service | CPU Limit | Memory Limit | CPU Reserve | Memory Reserve |
|---------|-----------|--------------|-------------|----------------|
| **postgres-basestation** | 1.0 | 512M | 0.5 | 256M |
| **postgres-auth** | 0.5 | 256M | 0.25 | 128M |
| **postgres-notification** | 0.5 | 256M | 0.25 | 128M |
| **mongodb** | 1.5 | 1024M | 0.75 | 512M |
| **redis** | 0.5 | 256M | 0.25 | 128M |
| **rabbitmq** | 1.0 | 512M | 0.5 | 256M |

**Rationale**:
- MongoDB handles metrics (high volume, needs more resources)
- Main PostgreSQL (base stations) has more data than auth/notifications
- Redis and RabbitMQ are lightweight in-memory stores

### Infrastructure Services

| Service | CPU Limit | Memory Limit | CPU Reserve | Memory Reserve |
|---------|-----------|--------------|-------------|----------------|
| **prometheus** | 0.5 | 512M | 0.25 | 256M |
| **grafana** | 0.5 | 256M | 0.25 | 128M |
| **zipkin** | 0.5 | 512M | 0.25 | 256M |
| **keycloak** | 1.0 | 768M | 0.5 | 384M |
| **frontend** | 0.25 | 128M | 0.1 | 64M |

## JVM Tuning Flags

All Java services use these optimized JVM flags:

```bash
JAVA_OPTS="-Xms<min>m -Xmx<max>m \
           -XX:MaxMetaspaceSize=128m \
           -XX:+UseG1GC \
           -XX:MaxGCPauseMillis=200 \
           -XX:+UseStringDeduplication \
           -XX:+UseCompressedOops \
           -XX:+OptimizeStringConcat"
```

### Flag Explanations:

- **-Xms / -Xmx**: Initial and maximum heap size
- **-XX:MaxMetaspaceSize=128m**: Limit class metadata memory
- **-XX:+UseG1GC**: Use G1 garbage collector (better for low-latency)
- **-XX:MaxGCPauseMillis=200**: Target max GC pause time
- **-XX:+UseStringDeduplication**: Reduce memory for duplicate strings
- **-XX:+UseCompressedOops**: Use 32-bit pointers (saves memory)
- **-XX:+OptimizeStringConcat**: Optimize string concatenation

## Startup Optimization

### Staged Startup Sequence

To prevent all services from starting simultaneously and overwhelming the CPU:

**Stage 1: Databases (Wait for health)**
```yaml
depends_on:
  postgres-*:
    condition: service_healthy
  mongodb:
    condition: service_healthy
  redis:
    condition: service_healthy
  rabbitmq:
    condition: service_healthy
```

**Stage 2: Infrastructure (After databases)**
```yaml
eureka-server:
  depends_on: []  # No dependencies, starts immediately

keycloak:
  depends_on:
    postgres-auth:
      condition: service_healthy
```

**Stage 3: Microservices (After Eureka + DBs)**
```yaml
base-station-service:
  depends_on:
    postgres-basestation:
      condition: service_healthy
    eureka-server:
      condition: service_started  # Don't wait for health, just started
```

**Stage 4: Gateway (After all services)**
```yaml
api-gateway:
  depends_on:
    - base-station-service
    - monitoring-service
    - notification-service
    - auth-service
```

**Stage 5: Frontend (After gateway)**
```yaml
frontend:
  depends_on:
    - api-gateway
```

### Why Staged Startup?

❌ **Without Staging**: All 17 services start at once
- 12 cores × 100% utilization = CPU thrashing
- All JVMs allocating heap simultaneously = OOM
- Services fail to connect (race conditions)

✅ **With Staging**: Sequential startup
- Databases first (low CPU, just file I/O)
- Then Java services (one-by-one Eureka registration)
- Then gateway (after backends ready)
- Smooth CPU usage curve, no spikes

## Docker Compose Resource Enforcement

Docker Compose uses cgroups to enforce limits:

```yaml
deploy:
  resources:
    limits:
      cpus: '1.0'        # Hard limit: never exceed 1 CPU core
      memory: 640M       # Hard limit: OOM kill if exceeded
    reservations:
      cpus: '0.5'        # Guaranteed minimum allocation
      memory: 320M       # Guaranteed minimum allocation
```

**Notes**:
- `limits` = hard cap (service killed if exceeded)
- `reservations` = guaranteed resources (Docker schedules accordingly)
- CPU values are fractional (0.5 = half a core, 1.5 = 1.5 cores)

## Monitoring Resource Usage with Docker Desktop

Docker Desktop provides built-in resource monitoring in the GUI:

1. **Open Docker Desktop Dashboard**
2. **Go to Containers tab**
3. **View real-time CPU/Memory usage per container**

Or use CLI:

```bash
# Real-time resource monitoring (use Docker Desktop GUI or CLI)
docker stats

# Per-service CPU/Memory
docker stats --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}"

# Memory pressure check
docker inspect <container> | jq '.[0].State.OOMKilled'

# JVM heap usage (inside container)
docker exec <java-container> jcmd 1 VM.native_memory summary
```

**Note**: Docker Desktop Dashboard shows resource usage graphs - easier than CLI!

## Troubleshooting OOM

If services still crash with OOM:

### 1. Reduce JVM Heap

Lower `-Xmx` by 20%:
```yaml
# Before
JAVA_OPTS: "-Xms256m -Xmx512m"

# After
JAVA_OPTS: "-Xms256m -Xmx410m"
```

### 2. Increase Docker Memory Limit

Raise container memory limit by 25%:
```yaml
# Before
memory: 640M

# After
memory: 800M
```

### 3. Enable Swap (Last Resort)

If physical RAM is insufficient:
```yaml
deploy:
  resources:
    limits:
      memory: 640M
    reservations:
      memory: 320M
  # Allow up to 256M swap
  mem_swappiness: 60
```

**Warning**: Swap degrades performance significantly.

## Preventing Cascading Failures

### Circuit Breaker Configuration

Already configured in services, but verify timeouts match resource limits:

```yaml
# base-station-service/application.yml
resilience4j:
  circuitbreaker:
    instances:
      monitoringService:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s  # Match slow startup time
```

### Health Check Tuning

Increase startup grace period for slow systems:

```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
  interval: 30s
  timeout: 10s
  retries: 5
  start_period: 120s  # Increased from 60s to 120s
```

## Optimization Checklist

- [ ] All Java services have `JAVA_OPTS` with heap limits
- [ ] All services have `deploy.resources` constraints
- [ ] Databases have health checks with appropriate `start_period`
- [ ] Services use `depends_on` with health conditions
- [ ] No service allocates more than 1.5 CPUs (leave room for others)
- [ ] Total memory allocation < 14GB (leave 2GB for OS)
- [ ] Tested with `docker compose up -d && docker stats`

## Expected Behavior

✅ **Healthy Startup**:
- All 17 containers start within 2-3 minutes
- No OOM kills (`docker inspect | grep OOMKilled` shows false)
- CPU usage peaks at ~80-90%, then stabilizes at ~30-40%
- All services register with Eureka
- API Gateway accessible at :8080
- Frontend loads without errors

❌ **Unhealthy Startup**:
- Containers repeatedly restart
- `docker stats` shows 100% CPU sustained
- Memory usage hits limits (shows `/ <limit>M`)
- `docker logs` shows `OutOfMemoryError`
- Services fail Eureka registration

## Production Recommendations

For production deployment:

1. **Increase Node Size**: 16GB+ RAM, 16+ cores
2. **Use Kubernetes**: Better resource scheduling than Docker Compose
3. **Horizontal Scaling**: Run multiple replicas behind load balancer
4. **Separate Databases**: Don't co-locate with application services
5. **Use Managed Services**: RDS, ElastiCache, managed Kafka instead of Docker

## References

- [Docker Resource Constraints](https://docs.docker.com/config/containers/resource_constraints/)
- [G1 GC Tuning Guide](https://www.oracle.com/technical-resources/articles/java/g1gc.html)
- [Spring Boot Memory](https://spring.io/blog/2015/12/10/spring-boot-memory-performance)
