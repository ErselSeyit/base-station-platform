# Base Station Operations & Maintenance Platform

<div align="center">

<picture>
  <source media="(max-width: 768px)" srcset="docs/diagram.svg" width="100%">
  <img src="docs/diagram.svg" alt="Architecture Diagram" style="max-width: 100%; height: auto;">
</picture>

<p>
  <a href="https://github.com/ErselSeyit/base-station-platform/actions/workflows/ci.yml"><img src="https://github.com/ErselSeyit/base-station-platform/actions/workflows/ci.yml/badge.svg" alt="CI/CD"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License"></a>
  <a href="https://goreportcard.com/report/github.com/ErselSeyit/base-station-platform"><img src="https://goreportcard.com/badge/github.com/ErselSeyit/base-station-platform" alt="Go Report Card"></a>
</p>

<p>
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white" alt="Java">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Go-1.23-00ADD8?logo=go&logoColor=white" alt="Go">
  <img src="https://img.shields.io/badge/C-99-A8B9CC?logo=c&logoColor=white" alt="C">
  <img src="https://img.shields.io/badge/Python-3.12-3776AB?logo=python&logoColor=white" alt="Python">
  <img src="https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black" alt="React">
  <img src="https://img.shields.io/badge/TypeScript-5.x-3178C6?logo=typescript&logoColor=white" alt="TypeScript">
</p>

<p>
  <img src="https://img.shields.io/badge/PostgreSQL-16-316192?logo=postgresql&logoColor=white" alt="PostgreSQL">
  <img src="https://img.shields.io/badge/MongoDB-7-47A248?logo=mongodb&logoColor=white" alt="MongoDB">
  <img src="https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white" alt="Redis">
  <img src="https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white" alt="Docker">
  <img src="https://img.shields.io/badge/Kubernetes-Ready-326CE5?logo=kubernetes&logoColor=white" alt="Kubernetes">
</p>

</div>

A production-ready microservices platform for 5G base station operations with AI-powered diagnostics, self-healing automation, and real-time monitoring.

---

## Quick Start

```bash
docker compose up -d && make docker_init_db
# Dashboard: http://localhost:3000 (admin / AUTH_ADMIN_PASSWORD from .env)
```

See [docs/QUICK_START.md](docs/QUICK_START.md) for setup details.

---

## Features

| Category | Capabilities |
|----------|--------------|
| **Monitoring** | 100 metric types, WebSocket streaming, Prometheus/Grafana |
| **AI Diagnostics** | 15+ ML models, anomaly detection, predictive maintenance |
| **SON** | MLB, MRO, CCO, Energy Saving with approval workflows |
| **Edge** | Go bridge for MIPS, C protocol library, SNMP/MQTT/NETCONF adapters |
| **Enterprise** | Multi-tenancy, TMF APIs (638/639/642), PagerDuty/Slack integrations |
| **Security** | JWT + RBAC, HMAC service auth, rate limiting, audit logging |

---

## Architecture

| Service | Port | Description |
|---------|------|-------------|
| Frontend | 3000 | React dashboard |
| API Gateway | 8080 | Routing, rate limiting, JWT |
| Auth Service | 8084 | Authentication, RBAC |
| Base Station | 8081 | Station CRUD, geospatial |
| Monitoring | 8082 | Metrics, WebSocket, SON |
| Notification | 8083 | Alerts via RabbitMQ |
| AI Diagnostic | 9091 | Python ML service |
| Edge Bridge | - | Go bridge for MIPS devices |

**Infrastructure:** PostgreSQL, MongoDB, Redis, RabbitMQ, Prometheus, Grafana, Zipkin

---

## Documentation

| Guide | Description |
|-------|-------------|
| [Quick Start](docs/QUICK_START.md) | Get running in 5 minutes |
| [Architecture](docs/ARCHITECTURE.md) | System design |
| [API Reference](docs/API.md) | REST endpoints |
| [Setup Guide](docs/SETUP.md) | Development setup |
| [Testing](docs/TESTING.md) | Test strategies |
| [Security](docs/AUTH_AUDIT.md) | Security audit |
| [Secrets](docs/SECRET_MANAGEMENT.md) | Production secrets |
| [Kubernetes](docs/k8s-services-reference.md) | K8s deployment |
| [Roadmap](docs/ENHANCEMENT_ROADMAP.md) | Future plans |

---

## Development

```bash
mvn clean install              # Backend
cd frontend && npm run dev     # Frontend
cd edge-bridge && make build   # Edge Bridge
cd device-protocol-c && make   # C Library
```

---

## License

MIT - [LICENSE](LICENSE) | **Author:** Ersel Seyit
