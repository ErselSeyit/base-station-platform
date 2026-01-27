# Setup Guide

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker 20.10+
- Docker Compose 2.0+
- Node.js 18+ (for frontend development)

## Quick Start

Start all services:
```bash
docker compose up -d
```

Access the application:
- **Dashboard**: http://localhost:3000
- **API Gateway**: http://localhost:8080
- **AI Diagnostics**: http://localhost:9091

Stop services:
```bash
docker compose down
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
make docker_init_db
```

### View Logs
```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f monitoring-service
```

### Rebuild Service
```bash
docker compose build monitoring-service --no-cache
docker compose up -d monitoring-service
```

## Security Configuration

All credentials are provided via environment variables - no hardcoded defaults.

### Required Environment Variables

| Variable | Description |
|----------|-------------|
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

### Optional Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | validate | JPA schema mode (use 'update' for dev) |
| `CORS_ALLOWED_ORIGINS` | http://frontend:80 | API Gateway CORS |
| `WEBSOCKET_ALLOWED_ORIGINS` | * | WebSocket CORS |
| `TRACING_SAMPLE_PROBABILITY` | 0.1 | Tracing sample rate (0-1) |
| `DIAGNOSTIC_REQUIRE_AUTH` | false | Enforce HMAC on AI diagnostic |

## Service Discovery

The platform uses **Docker DNS** for service discovery. Services reference each other by container name:
- `http://auth-service:8084`
- `http://base-station-service:8081`
- `http://monitoring-service:8082`
- `http://notification-service:8083`
- `http://ai-diagnostic:9091`

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
docker compose ps
docker compose logs -f
docker compose restart
```

### Port conflicts
```bash
# Linux/Mac
lsof -i :8080

# Windows
netstat -ano | findstr :8080
```

### Clear and restart
```bash
docker compose down -v
docker compose up -d
make docker_init_db
```

### Database connection issues
```bash
# Check PostgreSQL
docker compose exec postgres psql -U postgres -c '\l'

# Check MongoDB
docker compose exec mongodb mongosh -u admin -p admin --eval "show dbs"
```
