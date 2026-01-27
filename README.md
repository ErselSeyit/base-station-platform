# Base Station Operations & Maintenance Platform

<div align="center">

<picture>
  <source media="(max-width: 768px)" srcset="docs/diagram.svg" width="100%">
  <img src="docs/diagram.svg" alt="Architecture Diagram" style="max-width: 100%; height: auto;">
</picture>

</div>

A microservices platform for base station operations and maintenance with AI-powered diagnostics. Built with Spring Boot, React, and Python for real-time monitoring, automated problem detection, and comprehensive reporting.

[![CI/CD](https://github.com/ErselSeyit/base-station-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/ErselSeyit/base-station-platform/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-18.2-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-5.x-3178C6?logo=typescript&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.12-3776AB?logo=python&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-316192?logo=postgresql&logoColor=white)
![MongoDB](https://img.shields.io/badge/MongoDB-8.2-47A248?logo=mongodb&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-8-DC382D?logo=redis&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)

---

## Quick Start

### Docker Compose (Recommended)

```bash
# One-command startup with Makefile
make docker_start

# Or use Docker Compose directly
docker compose up -d

# Initialize databases with seed data
make docker_init_db

# Dashboard: http://localhost:3000 (admin / AUTH_ADMIN_PASSWORD from .env)
# API Gateway: http://localhost:8080
# Grafana: http://localhost:3001
# AI Diagnostics: http://localhost:9091
```

### Kubernetes (Production)

```bash
# Deploy to Kubernetes
make k8s_deploy

# Check status
make k8s_status

# View logs
make k8s_logs
```

---

## Architecture

**Services:** 5 Java microservices + 1 Python AI service + React frontend

| Service | Port | Description |
|---------|------|-------------|
| **Frontend** | 3000 | React dashboard with real-time metrics |
| **API Gateway** | 8080 | Routing, rate limiting, JWT validation |
| **Auth Service** | 8084 | Database-backed JWT authentication |
| **Base Station Service** | 8081 | Station CRUD, geospatial search |
| **Monitoring Service** | 8082 | Real-time metrics, WebSocket streaming |
| **Notification Service** | 8083 | Event-driven alerting via RabbitMQ |
| **AI Diagnostic** | 9091 | Python service for automated problem detection |

**Infrastructure:** PostgreSQL, MongoDB, Redis, RabbitMQ, Prometheus, Grafana, Zipkin

---

## Key Features

### AI-Powered Diagnostics
- Automated problem detection for temperature, CPU, memory, and signal issues
- Real-time remediation suggestions with confidence scores
- Device communication protocol for MIPS-based base stations
- Diagnostic event logging with frontend visualization

### Real-Time Monitoring
- WebSocket streaming for live metrics updates
- Customizable dashboards with Recharts visualization
- Threshold-based alerting with RabbitMQ
- Historical metrics storage in MongoDB

### Security
- Database-backed JWT authentication with RBAC
- HMAC-SHA256 service-to-service authentication
- Rate limiting at API Gateway (Redis-backed)
- Brute-force protection with exponential backoff

### Responsive UI
- Mobile-optimized dashboard with card layouts
- Dark/light theme support
- Interactive map view with Leaflet
- Comprehensive reports with PDF export

---

## Frontend Pages

| Page | Description |
|------|-------------|
| **Dashboard** | Overview with KPIs, charts, and activity feed |
| **Stations** | Station management with CRUD operations |
| **Station Detail** | Individual station metrics and configuration |
| **Map View** | Geographic visualization with Leaflet |
| **Metrics** | Performance charts and historical data |
| **Alerts** | Notification management with status tracking |
| **AI Diagnostics** | AI system performance and diagnostic events |
| **Reports** | Downloadable reports and exports |

---

## Technology Stack

### Backend
- **Java 21** - Virtual threads for high-concurrency
- **Spring Boot 3.4** - Microservices framework
- **Spring Cloud Gateway** - API gateway with rate limiting
- **Spring Data JPA/MongoDB** - Polyglot persistence
- **Resilience4j** - Circuit breakers and retry

### AI Service
- **Python 3.12** - AI diagnostic engine
- **FastAPI** - Async HTTP server
- **OpenTelemetry** - Distributed tracing

### Frontend
- **React 18** - UI framework with hooks
- **TypeScript** - Type-safe development
- **Material-UI** - Component library
- **TanStack Query** - Server state management
- **Recharts** - Data visualization
- **Leaflet** - Interactive maps

### Infrastructure
- **PostgreSQL 18** - Primary database (consolidated)
- **MongoDB 8.2** - Time-series metrics storage
- **Redis 8** - Caching and rate limiting
- **RabbitMQ 3.13** - Message broker
- **Prometheus + Grafana** - Observability
- **Zipkin** - Distributed tracing

---

## Project Structure

```
base-station-platform/
├── api-gateway/           # Spring Cloud Gateway + rate limiting
├── auth-service/          # JWT authentication + user management
├── base-station-service/  # Station CRUD + geospatial search
├── monitoring-service/    # Metrics collection + WebSocket
├── notification-service/  # Alert delivery via RabbitMQ
├── ai-diagnostic/         # Python AI diagnostic service
├── common/                # Shared utilities + HMAC security
├── frontend/              # React TypeScript dashboard
├── init-db/               # Database seed scripts
├── k8s/                   # Kubernetes manifests
├── helm/                  # Helm charts
├── monitoring/            # Prometheus configuration
└── docs/                  # Documentation
```

---

## Documentation

**Getting Started:**
- [Quick Start Guide](docs/QUICK_START.md)
- [Setup Guide](docs/SETUP.md)
- [API Reference](docs/API.md)

**Architecture:**
- [Design Overview](docs/ARCHITECTURE.md)
- [Security (HMAC Auth)](docs/HEADER_SPOOFING_BLOCKED.md)
- [Secret Management](docs/SECRET_MANAGEMENT.md)

**Operations:**
- [Testing Strategy](docs/TESTING.md)

---

## Testing

```bash
# Backend tests
mvn test

# Frontend tests
cd frontend && npm test

# Integration tests (requires Docker)
mvn verify -P integration-tests
```

| Layer | Coverage | Implementation |
|-------|----------|----------------|
| Backend unit | ~85% | JUnit 5, Mockito |
| Frontend unit | ~87% | Vitest, React Testing Library |
| Integration | Yes | Testcontainers |
| Contract | Yes | Spring Cloud Contract |
| E2E | Partial | Playwright |

---

## License

MIT License - [LICENSE](LICENSE)

**Author:** Ersel Seyit
