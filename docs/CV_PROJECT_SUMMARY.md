# Base Station Operations & Maintenance Platform

**Software Developer, Independent Systems Engineer**

Designed and solo-built a production-grade microservices platform for 5G base station monitoring, replacing manual operations with AI-driven diagnostics and self-healing automation across 104K lines of code in 5 languages.

**Scale:** 8 microservices, 14 containers, 60+ REST endpoints, 100% backend test coverage (73 test suites)

---

### What I Built

- **Architected an 8-service backend** in Java 21 / Spring Boot 3.4 with API gateway, JWT/RBAC authentication, HMAC service-to-service auth, and per-endpoint rate limiting across 9 route groups
- **Developed a 20-module AI diagnostic engine** in Python: LSTM traffic forecasting, Isolation Forest anomaly detection, SON network optimization (MLB/MRO/CCO/Energy Saving), predictive maintenance, root cause analysis, and self-healing with approval workflows
- **Built an edge-to-cloud data pipeline** spanning 3 languages: C binary protocol library for MIPS/ARM embedded devices (CRC-16, serial/TCP), Go bridge with SNMP/MQTT/NETCONF adapters and offline buffering, Python AI layer for cloud processing
- **Implemented real-time monitoring dashboards** in React 18 / TypeScript with WebSocket streaming, interactive Leaflet geospatial maps, and Recharts data visualization for 100 metric types
- **Containerized and orchestrated** with Docker Compose (14 services) and Kubernetes via Fabric8/JKube with Helm charts, automated secret generation, and Sealed Secrets support

### How I Solved Key Problems

| Problem | Solution | Result |
|---------|----------|--------|
| Service discovery overhead | Replaced Eureka with Docker DNS | Eliminated infrastructure service; works in Compose and K8s identically |
| 16 manual K8s YAML files drifting from app config | Adopted Fabric8/JKube manifest generation | Single source of truth in application.yml; auto-generated health probes |
| WebSocket scalability bottleneck | Migrated to Java 21 virtual threads | High-concurrency streaming without thread pool tuning |
| 3 separate PostgreSQL instances | Consolidated to single instance with schemas | Reduced container count, simplified backup/restore |

### Tech Stack

| Area | Technologies |
|------|-------------|
| Backend | Java 21, Spring Boot 3.4, Spring Cloud Gateway, Resilience4j, JPA |
| AI/ML | Python 3.12, Flask, scikit-learn, NumPy, OpenTelemetry |
| Frontend | React 18, TypeScript 5, Material-UI, Recharts, Leaflet, Framer Motion |
| Edge | Go 1.23 (SNMP/MQTT/NETCONF), C11 (binary protocol, MIPS cross-compile) |
| Data | PostgreSQL 18, MongoDB 8, Redis 7, RabbitMQ 4 |
| DevOps | Docker Compose, Kubernetes, Helm, Fabric8, GitHub Actions CI/CD |
| Observability | Prometheus, Grafana, Zipkin, structured logging |
| Security | JWT + HttpOnly cookies, HMAC-SHA256, rate limiting, automated secret rotation |
