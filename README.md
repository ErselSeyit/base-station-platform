# Base Station Operations & Maintenance Platform

```mermaid
graph TB
    User([Browser]) --> Ingress

    subgraph Ingress Layer
        Ingress[NGINX Ingress<br/>basestation.local]
    end

    Ingress -->|"/ "| FE
    Ingress -->|"/api"| FE
    Ingress -->|"/ws"| MS

    subgraph Cloud Platform
        FE[Frontend<br/>React · nginx · :3000] -->|"/api proxy"| GW[API Gateway · :8080]

        GW --> AS[Auth Service · :8084]
        GW --> BS[Base Station · :8081]
        GW --> MS[Monitoring · :8082]
        GW --> NS[Notification · :8083]

        MS -->|diagnose| AI[AI Diagnostic<br/>Python · :9091]
        MS -.->|publish| RMQ
        RMQ -.->|subscribe| NS
    end

    subgraph Edge Layer
        EB[Edge Bridge · Go] --> GW
        DS[Device Simulator · :9999] --> EB
        FS[Fault Orchestrator · :8099] --> DS
        FS -->|validate| AI
    end

    subgraph Data Layer
        AS & BS & NS --> PG[(PostgreSQL · :5432)]
        MS --> MDB[(MongoDB · :27017)]
        GW --> REDIS[(Redis · :6379)]
        RMQ[(RabbitMQ · :5672)]
    end

    subgraph Observability
        PROM[Prometheus · :9090] -.->|scrape| GW & AS & BS & MS & NS
        GRAF[Grafana · :3001] -.-> PROM
        GRAF -.-> LOKI[Loki · :3100]
        ZIP[Zipkin · :9411] -.->|traces| MS
    end

    style Ingress fill:#e65100,color:#fff,stroke:#e65100
    style FE fill:#4caf50,color:#fff,stroke:#4caf50
    style GW fill:#1976d2,color:#fff,stroke:#1976d2
    style AS fill:#ff9800,color:#fff,stroke:#ff9800
    style BS fill:#7b1fa2,color:#fff,stroke:#7b1fa2
    style MS fill:#d32f2f,color:#fff,stroke:#d32f2f
    style NS fill:#00838f,color:#fff,stroke:#00838f
    style AI fill:#667eea,color:#fff,stroke:#667eea
    style EB fill:#ef6c00,color:#fff,stroke:#ef6c00
    style DS fill:#5d4037,color:#fff,stroke:#5d4037
    style FS fill:#5d4037,color:#fff,stroke:#5d4037
    style PG fill:#336791,color:#fff,stroke:#336791
    style MDB fill:#47a248,color:#fff,stroke:#47a248
    style REDIS fill:#dc382d,color:#fff,stroke:#dc382d
    style RMQ fill:#ff6f00,color:#fff,stroke:#ff6f00
    style PROM fill:#e6522c,color:#fff,stroke:#e6522c
    style GRAF fill:#f46800,color:#fff,stroke:#f46800
    style LOKI fill:#2c3e50,color:#fff,stroke:#2c3e50
    style ZIP fill:#4a90d9,color:#fff,stroke:#4a90d9
```

<div align="center">

<p>
  <a href="https://github.com/ErselSeyit/base-station-platform/actions/workflows/ci.yml"><img src="https://github.com/ErselSeyit/base-station-platform/actions/workflows/ci.yml/badge.svg" alt="CI/CD"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License"></a>
  <a href="https://goreportcard.com/report/github.com/ErselSeyit/base-station-platform"><img src="https://goreportcard.com/badge/github.com/ErselSeyit/base-station-platform" alt="Go Report Card"></a>
</p>

<p>
  <img src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white" alt="Java">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Go-1.23-00ADD8?logo=go&logoColor=white" alt="Go">
  <img src="https://img.shields.io/badge/C-11-A8B9CC?logo=c&logoColor=white" alt="C">
  <img src="https://img.shields.io/badge/Python-3.12-3776AB?logo=python&logoColor=white" alt="Python">
  <img src="https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black" alt="React">
  <img src="https://img.shields.io/badge/TypeScript-5.x-3178C6?logo=typescript&logoColor=white" alt="TypeScript">
</p>

<p>
  <img src="https://img.shields.io/badge/PostgreSQL-18-316192?logo=postgresql&logoColor=white" alt="PostgreSQL">
  <img src="https://img.shields.io/badge/MongoDB-8-47A248?logo=mongodb&logoColor=white" alt="MongoDB">
  <img src="https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white" alt="Redis">
  <img src="https://img.shields.io/badge/Kubernetes-Minikube-326CE5?logo=kubernetes&logoColor=white" alt="Kubernetes">
  <img src="https://img.shields.io/badge/Helm-3.x-0F1689?logo=helm&logoColor=white" alt="Helm">
</p>

</div>

A production-ready microservices platform for 5G base station operations with AI-powered diagnostics, self-healing automation, and real-time monitoring.

---

## Quick Start

```bash
minikube start --memory=8192 --cpus=4
minikube addons enable ingress
helm install basestation helm/basestation-platform -n basestation-platform --create-namespace
# Dashboard: http://basestation.local:{ingress-port} (admin / password from K8s secret)
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
| [Secrets](docs/SECRET_MANAGEMENT.md) | Production secrets |
| [Kubernetes](k8s/README.md) | K8s deployment |

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
