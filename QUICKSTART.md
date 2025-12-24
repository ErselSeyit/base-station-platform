# Quick Start Guide

## Prerequisites Check

Before starting, ensure you have:
- ✅ Java 21 installed (`java -version`)
- ✅ Maven 3.9+ installed (`mvn -version`)
- ✅ Docker and Docker Compose installed (`docker --version`, `docker-compose --version`)

## Quick Start with Docker Compose

### 1. Start All Services
```bash
docker-compose up -d
```

### 2. Wait for Services to Start
Wait approximately 30-60 seconds for all services to initialize. Check status:
```bash
docker-compose ps
```

### 3. Verify Services
- **Eureka Dashboard**: http://localhost:8761
- **API Gateway**: http://localhost:8080
- **Base Station Service**: http://localhost:8081/actuator/health
- **Monitoring Service**: http://localhost:8082/actuator/health
- **Notification Service**: http://localhost:8083/actuator/health

### 4. Test the API

#### Create a Base Station
```bash
curl -X POST http://localhost:8080/api/v1/stations \
  -H "Content-Type: application/json" \
  -d '{
    "stationName": "BS-001",
    "location": "Downtown",
    "latitude": 40.7128,
    "longitude": -74.0060,
    "stationType": "MACRO_CELL",
    "status": "ACTIVE",
    "powerConsumption": 1500.0
  }'
```

#### Get All Stations
```bash
curl http://localhost:8080/api/v1/stations
```

#### Record a Metric
```bash
curl -X POST http://localhost:8080/api/v1/metrics \
  -H "Content-Type: application/json" \
  -d '{
    "stationId": 1,
    "stationName": "BS-001",
    "metricType": "CPU_USAGE",
    "value": 75.5,
    "unit": "%"
  }'
```

#### Create a Notification
```bash
curl -X POST "http://localhost:8080/api/v1/notifications?stationId=1&message=High%20CPU%20usage&type=ALERT"
```

## View Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f base-station-service
```

## Stop Services

```bash
docker-compose down
```

## Clean Up (Remove Volumes)

```bash
docker-compose down -v
```

## Troubleshooting

### Port Already in Use
If you get port conflicts, check what's using the ports:
```bash
# Windows
netstat -ano | findstr :8080

# Linux/Mac
lsof -i :8080
```

### Services Not Starting
1. Check Docker logs: `docker-compose logs`
2. Ensure Docker has enough resources (memory, CPU)
3. Verify all required ports are available

### Database Connection Issues
- Wait longer for databases to initialize
- Check database health: `docker-compose ps`
- View database logs: `docker-compose logs postgres-basestation`

## Development Mode

For local development without Docker:

1. Start databases only:
   ```bash
   docker-compose up -d postgres-basestation postgres-notification mongodb
   ```

2. Start Eureka Server:
   ```bash
   cd eureka-server && mvn spring-boot:run
   ```

3. Start services in separate terminals:
   ```bash
   # Terminal 1
   cd base-station-service && mvn spring-boot:run
   
   # Terminal 2
   cd monitoring-service && mvn spring-boot:run
   
   # Terminal 3
   cd notification-service && mvn spring-boot:run
   
   # Terminal 4
   cd api-gateway && mvn spring-boot:run
   ```

## Next Steps

- Read the full [README.md](README.md) for detailed documentation
- Explore the API endpoints
- Review the test cases to understand the codebase
- Check out the CI/CD pipeline in `.github/workflows/ci-cd.yml`

