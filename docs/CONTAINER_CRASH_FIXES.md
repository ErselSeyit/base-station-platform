# Container Crash Fixes - Docker Compose Network Architecture

## Issues Fixed

### 1. **Network Architecture Problem**
**Problem:** All services were on a single `basestation-network`, causing:
- No network isolation between frontend, services, and databases
- Services unable to communicate across proper network boundaries
- Security vulnerabilities with databases exposed to all services

**Solution:** Implemented multi-network architecture:
```yaml
networks:
  frontend-network:    # Frontend ↔ API Gateway only
  app-network:         # Application services communication
  database-network:    # Database isolation
  monitoring-network:  # Observability stack
```

### 2. **Missing Database Seed Script**
**Problem:** `postgres-auth` was missing the auth-seed.sql mount
- Users table not initialized
- Login failures due to missing admin/user accounts

**Solution:** Added volume mount:
```yaml
postgres-auth:
  volumes:
    - postgres-auth-data:/var/lib/postgresql/data
    - ./init-db/auth-seed.sql:/docker-entrypoint-initdb.d/seed.sql
```

### 3. **Service Discovery Failures**
**Problem:** auth-service had `prefer-ip-address: true`
- Eureka registered wrong IP addresses from multiple networks
- Services couldn't find each other
- HTTP 502 Bad Gateway errors

**Solution:** Changed to hostname-based registration:
```yaml
eureka:
  instance:
    prefer-ip-address: false
    hostname: ${EUREKA_INSTANCE_HOSTNAME:auth-service}
```

### 4. **Dependency Chain Issues**
**Problem:** Services had weak dependency definitions
- Containers started before dependencies were ready
- Race conditions during startup
- Services crashing due to missing databases/Eureka

**Solution:** Proper healthcheck-based dependencies:
```yaml
base-station-service:
  depends_on:
    postgres-basestation:
      condition: service_healthy  # Wait for DB to be ready
    eureka-server:
      condition: service_started  # Wait for Eureka
```

## Network Topology

```
┌─────────────────────────────────────────────────────────────┐
│                     frontend-network                         │
│                                                               │
│  ┌──────────┐              ┌─────────────┐                  │
│  │ Frontend │◄────────────►│ API Gateway │                  │
│  └──────────┘              └─────────────┘                  │
│                                    │                          │
└────────────────────────────────────┼──────────────────────────┘
                                     │
┌────────────────────────────────────┼──────────────────────────┐
│                     app-network    │                          │
│                                    ▼                          │
│  ┌──────────────┐  ┌──────────────────┐  ┌──────────────┐   │
│  │ Eureka       │  │ Base Station Svc │  │ Monitoring   │   │
│  │ Server       │  │ Auth Service     │  │ Service      │   │
│  └──────────────┘  │ Notification Svc │  │ Notification │   │
│                    └──────────────────┘  │ Service      │   │
│  ┌──────────────┐                        └──────────────┘   │
│  │ Redis        │                                            │
│  │ RabbitMQ     │                                            │
│  └──────────────┘                                            │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────┼──────────────────────────────┐
│              database-network│                              │
│                              ▼                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │ PostgreSQL  │  │ PostgreSQL  │  │ PostgreSQL  │        │
│  │ (basestation│  │ (notification│ │ (auth)      │        │
│  └─────────────┘  └─────────────┘  └─────────────┘        │
│                                                              │
│  ┌─────────────┐                                            │
│  │ MongoDB     │                                            │
│  └─────────────┘                                            │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────┼──────────────────────────────┐
│           monitoring-network │                              │
│                              ▼                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │ Zipkin      │  │ Prometheus  │  │ Grafana     │        │
│  └─────────────┘  └─────────────┘  └─────────────┘        │
└─────────────────────────────────────────────────────────────┘
```

## Service Network Assignments

| Service | Networks | Reason |
|---------|----------|--------|
| **frontend** | frontend-network | Only needs gateway access |
| **api-gateway** | frontend-network, app-network, monitoring-network | Bridges frontend to services |
| **base-station-service** | app-network, database-network, monitoring-network | Needs DB + Eureka + metrics |
| **monitoring-service** | app-network, database-network, monitoring-network | Needs MongoDB + RabbitMQ |
| **notification-service** | app-network, database-network, monitoring-network | Needs PostgreSQL + RabbitMQ |
| **auth-service** | app-network, database-network, monitoring-network | Needs PostgreSQL + Eureka |
| **eureka-server** | app-network | Service registry |
| **postgres-*** | database-network | Isolated data layer |
| **mongodb** | database-network | Isolated data layer |
| **redis** | app-network | Session/cache for services |
| **rabbitmq** | app-network | Messaging for services |
| **zipkin** | monitoring-network, app-network | Needs service traces |
| **prometheus** | monitoring-network, app-network | Scrapes service metrics |
| **grafana** | monitoring-network | Visualization only |
| **keycloak** | app-network, database-network, frontend-network | OAuth2 IdP |

## Startup Order

With proper dependencies, containers start in this order:

1. **Databases** (no dependencies)
   - postgres-basestation
   - postgres-notification
   - postgres-auth
   - mongodb
   - redis
   - rabbitmq

2. **Infrastructure** (wait for databases)
   - eureka-server (no DB dependency)
   - keycloak (waits for postgres-auth)
   - zipkin
   - prometheus

3. **Microservices** (wait for Eureka + DB)
   - base-station-service (postgres-basestation + eureka)
   - monitoring-service (mongodb + rabbitmq + eureka)
   - notification-service (postgres-notification + rabbitmq + eureka)
   - auth-service (postgres-auth + eureka)

4. **API Layer** (wait for services)
   - api-gateway (all services + redis)

5. **Frontend** (wait for gateway)
   - frontend

## Testing the Fix

```bash
# Clean up old containers and networks
docker compose down -v
docker system prune -f

# Rebuild and start
docker compose build --no-cache
docker compose up -d

# Check all containers are running
docker compose ps

# Check network connectivity
docker exec base-station-service ping -c 3 postgres-basestation
docker exec base-station-service ping -c 3 eureka-server

# Verify Eureka registration
curl -s http://localhost:8762/eureka/apps/BASE-STATION-SERVICE | grep -E "hostName|status"

# Expected:
# <hostName>base-station-service</hostName>
# <status>UP</status>

# Test authentication
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'

# Test station creation
TOKEN="<token-from-login>"
curl -X POST http://localhost:8080/api/v1/stations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "stationName": "Test Station",
    "location": "Test City",
    "latitude": 40.7128,
    "longitude": -74.0060,
    "stationType": "MACRO",
    "powerConsumption": 1000.0
  }'
```

## Common Errors and Solutions

### Error: "SECURITY_INTERNAL_SECRET environment variable is required"
**Solution:** Create `.env` file:
```bash
SECURITY_INTERNAL_SECRET=$(openssl rand -hex 32)
JWT_SECRET=$(openssl rand -base64 64)
```

### Error: "Cannot connect to postgres-basestation"
**Cause:** Service on wrong network
**Solution:** Ensure service has both `app-network` and `database-network`

### Error: "Eureka shows service as DOWN"
**Cause:** `prefer-ip-address: true` picking wrong IP
**Solution:** Set `prefer-ip-address: false` and `hostname: <service-name>`

### Error: "Login fails with 401 Unauthorized"
**Cause:** Missing auth-seed.sql or database not initialized
**Solution:** Verify mount exists and restart postgres-auth:
```bash
docker compose down postgres-auth
docker volume rm base-station-platform_postgres-auth-data
docker compose up -d postgres-auth
```

## Performance Impact

**Before Fix:**
- Containers crashed repeatedly
- Services couldn't communicate
- 100% failure rate

**After Fix:**
- All containers healthy
- Services register with Eureka correctly
- API endpoints respond with HTTP 200
- End-to-end authentication works

## Security Improvements

1. **Network Isolation:** Databases no longer accessible from frontend
2. **Least Privilege:** Each service only on networks it needs
3. **Defense in Depth:** Multiple network boundaries to cross for attacks
4. **Monitoring Isolation:** Observability stack separate from app traffic

## Future Optimizations

1. **Use network aliases** for service discovery instead of container names
2. **Enable internal: true** for database-network in production
3. **Add network policies** to further restrict inter-service communication
4. **Implement mTLS** between services for encrypted communication
5. **Use Docker Swarm secrets** instead of environment variables for credentials

## References

- [Docker Multi-Network Architecture](https://docs.docker.com/network/bridge/)
- [Spring Cloud Netflix Eureka](https://cloud.spring.io/spring-cloud-netflix/reference/html/)
- [Docker Compose Networking](https://docs.docker.com/compose/networking/)
