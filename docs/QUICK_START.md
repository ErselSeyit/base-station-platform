# Quick Start Guide

## Prerequisites

- Docker Desktop installed and running
- 8GB RAM minimum (12GB recommended)
- Ports available: 3000, 8080, 9091

## One-Command Startup

```bash
# Start all services
make docker_start

# Or use Docker Compose directly
docker compose up -d

# Initialize databases with seed data
make docker_init_db
```

## Access Points

| Service | URL | Credentials |
|---------|-----|-------------|
| **Dashboard** | http://localhost:3000 | admin / (see .env AUTH_ADMIN_PASSWORD) |
| **API Gateway** | http://localhost:8080 | - |
| **AI Diagnostics** | http://localhost:9091 | - |
| **Grafana** | http://localhost:3001 | admin / (see .env GRAFANA_PASSWORD) |
| **Prometheus** | http://localhost:9090 | - |
| **RabbitMQ** | http://localhost:15672 | (see .env RABBITMQ_USER/PASSWORD) |

## Environment Setup

Create a `.env` file with required variables:

```bash
# Copy example and edit
cp .env.example .env
```

Required variables:
- `POSTGRES_USER`, `POSTGRES_PASSWORD` - PostgreSQL credentials
- `MONGODB_USER`, `MONGODB_PASSWORD` - MongoDB credentials
- `RABBITMQ_USER`, `RABBITMQ_PASSWORD` - RabbitMQ credentials
- `JWT_SECRET` - JWT signing secret (min 32 chars)
- `AUTH_ADMIN_PASSWORD` - Initial admin password (min 12 chars)
- `SECURITY_INTERNAL_SECRET` - HMAC secret for service auth
- `GRAFANA_PASSWORD` - Grafana admin password

## Daily Workflow

### Start
```bash
docker compose up -d
```

### Stop
```bash
docker compose down
```

### View Logs
```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f base-station-service
```

### Restart Service
```bash
docker compose restart monitoring-service
```

## Kubernetes Deployment

```bash
# Deploy to Kubernetes
make k8s_deploy

# Check status
make k8s_status

# Initialize databases
make k8s_init_db
```

## Troubleshooting

### Services won't start
```bash
docker compose ps
docker compose logs <service-name>
```

### Login fails
Ensure databases are initialized:
```bash
make docker_init_db
```

### Clean restart
```bash
docker compose down -v
docker compose up -d
make docker_init_db
```

## Architecture

The platform uses:
- **Docker DNS** for service discovery (no Eureka)
- **Consolidated PostgreSQL** (single instance with schemas)
- **HMAC authentication** for service-to-service calls

See [ARCHITECTURE.md](ARCHITECTURE.md) for details.

## Next Steps

- Explore the dashboard at http://localhost:3000
- Check metrics in Grafana at http://localhost:3001
- Read API docs at [API.md](API.md)
- Check [ENHANCEMENT_ROADMAP.md](ENHANCEMENT_ROADMAP.md) for planned features
