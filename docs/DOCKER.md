# Docker Documentation

This document provides detailed information about Docker setup and containerization for the Base Station Platform.

## Dockerfiles

Each service has its own Dockerfile located in the service directory:

- `eureka-server/Dockerfile`
- `api-gateway/Dockerfile`
- `base-station-service/Dockerfile`
- `monitoring-service/Dockerfile`
- `notification-service/Dockerfile`

## Docker Compose

The `docker-compose.yml` file orchestrates all services and dependencies.

### Services

1. **Databases**
   - `postgres-basestation`: PostgreSQL for Base Station Service (Port 5432)
   - `postgres-notification`: PostgreSQL for Notification Service (Port 5433)
   - `mongodb`: MongoDB for Monitoring Service (Port 27017)

2. **Application Services**
   - `eureka-server`: Service Discovery (Port 8761)
   - `base-station-service`: Base Station Management (Port 8081)
   - `monitoring-service`: Metrics Collection (Port 8082)
   - `notification-service`: Notification Management (Port 8083)
   - `api-gateway`: API Gateway (Port 8080)

### Networks

All services run on the `basestation-network` bridge network for internal communication.

### Volumes

- `postgres-basestation-data`: Persistent storage for Base Station database
- `postgres-notification-data`: Persistent storage for Notification database
- `mongodb-data`: Persistent storage for MongoDB

## Building Images

### Build All Images
```bash
docker-compose build
```

### Build Specific Service
```bash
docker-compose build base-station-service
```

### Build Without Cache
```bash
docker-compose build --no-cache
```

## Running Services

### Start All Services
```bash
docker-compose up -d
```

### Start Specific Service
```bash
docker-compose up -d eureka-server
```

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f base-station-service
```

### Stop Services
```bash
docker-compose down
```

### Stop and Remove Volumes
```bash
docker-compose down -v
```

## Health Checks

All services include health checks:
- Databases: Check if ready to accept connections
- Application services: Check actuator health endpoint

## Environment Variables

Services can be configured via environment variables in `docker-compose.yml` or `.env` file.

## Troubleshooting

### Container Won't Start
1. Check logs: `docker-compose logs [service-name]`
2. Verify port availability
3. Check Docker resources (memory, CPU)

### Database Connection Issues
1. Ensure database containers are healthy: `docker-compose ps`
2. Wait for databases to fully initialize (30-60 seconds)
3. Check network connectivity: `docker network inspect basestation-network`

### Service Registration Issues
1. Verify Eureka Server is running: http://localhost:8761
2. Check service logs for registration errors
3. Ensure services wait for Eureka to be ready

