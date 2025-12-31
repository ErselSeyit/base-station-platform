# Setup Guide

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker 20.10+
- Docker Compose 2.0+

## Quick Start

Start all services:
```bash
docker compose up -d
```

Access the application:
- **Dashboard**: http://localhost:3000
- **API Gateway**: http://localhost:8080
- **Eureka Dashboard**: http://localhost:8762

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
Generate realistic test data (10 stations, 7 days of metrics):
```bash
python3 scripts/seed_realistic_data.py
```

### View Logs
```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f monitoring-service
```

### Restart Service
```bash
# Rebuild and restart
docker compose build monitoring-service
docker compose restart monitoring-service
```

## Security Configuration

This is a **portfolio demonstration** project. The architecture supports production upgrades:

| Current State | Production Upgrade |
|---------------|-------------------|
| Static JWT secret | Vault/AWS Secrets Manager with rotation |
| Simulated tokens for dev | OAuth2/OIDC with real IdP (Keycloak, Auth0) |
| No token revocation | Redis blacklist or short-lived + refresh tokens |
| Basic gateway validation | JWKS endpoint discovery |

### Development Setup

```yaml
# API Gateway (application.yml)
jwt:
  secret: ${JWT_SECRET:}  # >= 32 chars for HS256
  simulate-validation: ${JWT_SIMULATE_VALIDATION:false}
```

- **Quick start**: Set `JWT_SIMULATE_VALIDATION=true` to bypass tokens
- **Production-like**: Set `JWT_SECRET` to `openssl rand -base64 64` output
- Tokens need `sub` (subject) and `exp` (expiration); expired/invalid tokens are rejected

## Troubleshooting

### Services won't start
```bash
# Check status
docker compose ps

# View logs
docker compose logs -f

# Restart everything
docker compose restart
```

### Port conflicts
```bash
# Linux/Mac
lsof -i :8080

# Windows
netstat -ano | findstr :8080
```

### Clear and reseed data
```bash
# Stop services
docker compose down

# Remove volumes
docker volume rm base-station-platform_mongodb-data
docker volume rm base-station-platform_postgres-basestation-data

# Restart
docker compose up -d

# Wait 30 seconds, then seed
python3 scripts/seed_realistic_data.py
```
