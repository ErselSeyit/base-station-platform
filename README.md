# Base Station Operations & Maintenance Platform

A production-ready microservices platform for base station operations and maintenance with AI-powered diagnostics, self-healing automation, and enterprise integrations.

[![CI/CD](https://github.com/ErselSeyit/base-station-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/ErselSeyit/base-station-platform/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Stack:** Java 21 / Spring Boot 3.4 / Go 1.23 / C99 / Python 3.12 / React 18 / TypeScript

---

## Quick Start

```bash
# Start all services
docker compose up -d

# Initialize databases
make docker_init_db

# Open dashboard
open http://localhost:3000  # admin / (see .env AUTH_ADMIN_PASSWORD)
```

See [docs/QUICK_START.md](docs/QUICK_START.md) for details.

---

## What It Does

### Real-Time Monitoring
- **100 metric types** across 5G NR, power, environmental, transport, and network slicing
- WebSocket streaming with live dashboards
- Prometheus/Grafana observability with Zipkin tracing

### AI-Powered Diagnostics
- Automated problem detection with 15+ ML models
- Traffic prediction, anomaly detection, root cause analysis
- Predictive maintenance for fans, batteries, fiber, VSWR

### Self-Optimization Network (SON)
- MLB, MRO, CCO, Energy Saving with approval workflows
- Auto-execute low-risk recommendations
- Rollback support for failed optimizations

### Edge Computing
- Go bridge for MIPS devices with SNMP/MQTT/NETCONF/Modbus adapters
- C protocol library for binary device communication
- TLS/mTLS support with automatic reconnection

### Enterprise Features
- Multi-tenancy with organization isolation
- TMF Open APIs (638, 639, 642)
- External integrations (PagerDuty, Slack, webhooks)

---

## Architecture

```
Frontend (React)  →  API Gateway  →  Microservices  →  Databases
     :3000              :8080           :8081-8084       PostgreSQL
                          │                              MongoDB
                    ┌─────┼─────┐                        Redis
                    ▼     ▼     ▼
                  Auth  Base   Monitoring  →  AI Diagnostic
                        Station               (Python :9091)
                          │
                    Edge Bridge (Go)  →  MIPS Devices
```

| Service | Port | Purpose |
|---------|------|---------|
| Frontend | 3000 | React dashboard |
| API Gateway | 8080 | Routing, rate limiting, JWT validation |
| Auth Service | 8084 | JWT auth, RBAC, audit logging |
| Base Station | 8081 | Station CRUD, geospatial search |
| Monitoring | 8082 | Metrics, WebSocket, SON |
| AI Diagnostic | 9091 | ML models, predictions, self-healing |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for details.

---

## Documentation

| Document | Description |
|----------|-------------|
| [Quick Start](docs/QUICK_START.md) | Get running in 5 minutes |
| [Setup Guide](docs/SETUP.md) | Development environment setup |
| [Architecture](docs/ARCHITECTURE.md) | System design and decisions |
| [API Reference](docs/API.md) | REST API documentation |
| [Testing](docs/TESTING.md) | Test coverage and strategies |
| [Secret Management](docs/SECRET_MANAGEMENT.md) | Production secrets handling |
| [Kubernetes Reference](docs/k8s-services-reference.md) | K8s deployment guide |

### Security & Roadmap

| Document | Description |
|----------|-------------|
| [Security Audit](docs/AUTH_AUDIT.md) | All 21 issues resolved |
| [Internal Auth](docs/HEADER_SPOOFING_BLOCKED.md) | HMAC service authentication |
| [Enhancement Roadmap](docs/ENHANCEMENT_ROADMAP.md) | 4-phase feature plan |
| [Implementation Plan](docs/IMPLEMENTATION_PLAN.md) | Detailed technical specs |

---

## Project Structure

```
base-station-platform/
├── api-gateway/           # Spring Cloud Gateway
├── auth-service/          # JWT authentication
├── base-station-service/  # Station management
├── monitoring-service/    # Metrics & SON
├── notification-service/  # Alert delivery
├── ai-diagnostic/         # Python AI service
├── edge-bridge/           # Go edge bridge
├── device-protocol-c/     # C binary protocol
├── tmf-api/               # TMF Open APIs
├── frontend/              # React dashboard
├── k8s/                   # Kubernetes manifests
└── docs/                  # Documentation
```

---

## Development

```bash
# Backend (Java)
mvn clean install

# Frontend
cd frontend && npm install && npm run dev

# Edge Bridge (Go)
cd edge-bridge && make build

# C Protocol Library
cd device-protocol-c && make
```

### Testing

```bash
# Backend tests
mvn test

# Frontend tests
cd frontend && npm test

# Integration tests
mvn verify -P integration-tests
```

---

## Kubernetes Deployment

```bash
# Deploy to cluster
make k8s_deploy

# Check status
make k8s_status

# View logs
make k8s_logs
```

See [docs/k8s-services-reference.md](docs/k8s-services-reference.md) for details.

---

## License

MIT License - see [LICENSE](LICENSE)

**Author:** Ersel Seyit
