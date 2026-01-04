# Base Station Operations & Maintenance Platform

<div align="center">

![Architecture](docs/diagram.svg)

</div>

Educational microservices platform demonstrating distributed systems trade-offs with measured performance data. Current scale: 26 base stations, 1,365 synthetic metrics - intentionally over-engineered to quantify costs. Benchmarks show **6.7× slower startup, 3× more memory** vs modular monolith.

[![CI/CD](https://github.com/ErselSeyit/base-station-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/ErselSeyit/base-station-platform/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?logo=springboot&logoColor=white)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2024.0.0-6DB33F?logo=spring&logoColor=white)
![React](https://img.shields.io/badge/React-18.2-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-5.x-3178C6?logo=typescript&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-316192?logo=postgresql&logoColor=white)
![MongoDB](https://img.shields.io/badge/MongoDB-8.2-47A248?logo=mongodb&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-8-DC382D?logo=redis&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.13-FF6600?logo=rabbitmq&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)

---

## Quick Start

```bash
docker compose up -d
# Dashboard: http://localhost:3000 (admin/admin)
# API Gateway: http://localhost:8080
# Eureka: http://localhost:8762
# Grafana: http://localhost:3001
```

**Architecture:** 6 services (API Gateway, Auth, Base Station, Monitoring, Notification, Eureka) • 5 databases (PostgreSQL×3, MongoDB, Redis) • RabbitMQ messaging

**Features:**
- Database-backed JWT authentication with RBAC + HMAC-SHA256 service auth
- **NEW:** Keycloak OAuth2/OIDC integration available ([setup guide](docs/KEYCLOAK_INTEGRATION.md))
- Real-time metrics via WebSocket
- Geospatial search (Haversine formula)
- Event-driven alerting (RabbitMQ)
- Redis-backed rate limiting (tested with 6,500+ requests)
- **FIXED:** Multi-network Docker architecture with proper isolation

**Production Gaps:**
- Keycloak IdP available but not enabled by default • Demo credentials in seed scripts • Haversine doesn't scale beyond 10k rows • Multiple database types where one suffices • Eureka in containers (redundant with Kubernetes)

📖 [Architecture details](docs/ARCHITECTURE.md) • [Setup guide](docs/SETUP.md) • [Production requirements](docs/IMPROVEMENTS.md)

---

## Performance Analysis

### Microservices vs Monolith Costs

<table>
<thead>
<tr>
<th>Metric</th>
<th align="center">Microservices</th>
<th align="center">Monolith</th>
<th align="center">Overhead</th>
</tr>
</thead>
<tbody>
<tr>
<td><b>Startup time</b></td>
<td align="center">~120s</td>
<td align="center">~18s</td>
<td align="center"><b>6.7× slower</b></td>
</tr>
<tr>
<td><b>Memory usage</b></td>
<td align="center">~2.3 GB</td>
<td align="center">~768 MB</td>
<td align="center"><b>3× more</b></td>
</tr>
<tr>
<td><b>Cross-service call</b></td>
<td align="center">Network HTTP</td>
<td align="center">In-process</td>
<td align="center"><b>~50× slower (estimated)</b></td>
</tr>
</tbody>
</table>

**Recommendation:** Start with modular monolith, split only when monitoring shows bottlenecks unsolvable by vertical scaling.

**Scale threshold:** 10,000+ stations, 500,000+ metrics/day, 4+ teams, heterogeneous traffic patterns requiring independent scaling.

📊 [Full comparison](docs/MONOLITH_VS_MICROSERVICES.md) • [Kubernetes migration](docs/K8S_MIGRATION.md)

---

### Geospatial Search Performance

<table>
<thead>
<tr>
<th>Implementation</th>
<th align="center">Query Time (100k rows)</th>
<th align="center">Method</th>
<th align="center">Scalability</th>
</tr>
</thead>
<tbody>
<tr>
<td><b>Current:</b> Haversine</td>
<td align="center">8,000ms</td>
<td align="center">Full table scan</td>
<td align="center">❌ Does not scale</td>
</tr>
<tr>
<td><b>Alternative:</b> PostGIS</td>
<td align="center">4.6ms</td>
<td align="center">GIST spatial index</td>
<td align="center">✅ Production-ready</td>
</tr>
</tbody>
</table>

PostGIS with GIST indexes: **1,750× faster** at 100k rows. [Migration guide](docs/POSTGIS_MIGRATION.md)

---

### Rate Limiting Validation

Redis-backed `RequestRateLimiter` tested with comprehensive load scenarios.

<table>
<thead>
<tr>
<th>Test Scenario</th>
<th align="center">Load Profile</th>
<th align="center">Result</th>
</tr>
</thead>
<tbody>
<tr>
<td><b>Burst Attack</b></td>
<td align="center">500 concurrent requests</td>
<td align="center">~96% blocked (429)</td>
</tr>
<tr>
<td><b>Sustained Load</b></td>
<td align="center">6,000 req over 60s (100 req/s)</td>
<td align="center">~90% blocked, stable</td>
</tr>
<tr>
<td><b>Gateway Health</b></td>
<td align="center">All scenarios</td>
<td align="center">✅ No crashes or degradation</td>
</tr>
</tbody>
</table>

Configuration: 10 req/s limit, 20-token burst capacity, IP-based. [Test results](docs/STRESS_TEST_RESULTS.md)

---

### Security Implementation

Original vulnerability: Services trusted `X-User-Role` headers, allowing gateway bypass.

<table>
<thead>
<tr>
<th>Attack Vector</th>
<th align="center">Before HMAC</th>
<th align="center">After HMAC</th>
</tr>
</thead>
<tbody>
<tr>
<td><b>Forged admin headers</b></td>
<td align="center">❌ Succeeds</td>
<td align="center">✅ Blocked (403)</td>
</tr>
<tr>
<td><b>Gateway bypass</b></td>
<td align="center">❌ Full access</td>
<td align="center">✅ Blocked (403)</td>
</tr>
<tr>
<td><b>Replay attacks</b></td>
<td align="center">❌ Possible</td>
<td align="center">✅ Blocked (30s TTL)</td>
</tr>
</tbody>
</table>

Mitigation: HMAC-SHA256 signatures with timestamp validation. Remaining gap: No mutual TLS. [Security details](docs/HEADER_SPOOFING_BLOCKED.md)

---

## Testing

<table>
<thead>
<tr>
<th>Layer</th>
<th align="center">Coverage</th>
<th>Implementation</th>
</tr>
</thead>
<tbody>
<tr>
<td><b>Backend unit</b></td>
<td align="center">100%</td>
<td>19 test classes, service layer</td>
</tr>
<tr>
<td><b>Frontend unit</b></td>
<td align="center">~87%</td>
<td>103 tests, Vitest + React Testing Library</td>
</tr>
<tr>
<td><b>Integration</b></td>
<td align="center">✅ Yes</td>
<td>Testcontainers (PostgreSQL, MongoDB, Redis)</td>
</tr>
<tr>
<td><b>Contract</b></td>
<td align="center">✅ Yes</td>
<td>Service boundary verification</td>
</tr>
<tr>
<td><b>E2E</b></td>
<td align="center">Partial</td>
<td>Playwright, critical flows</td>
</tr>
</tbody>
</table>

```bash
mvn test                  # Backend
cd frontend && npm test   # Frontend
```

CI: GitHub Actions, matrix builds, Testcontainers. [Testing strategy](docs/TESTING.md)

---

## Technology Stack


## Project Structure

```
base-station-platform/
├── api-gateway/           # Spring Cloud Gateway + rate limiting
├── auth-service/          # JWT authentication
├── base-station-service/  # Station CRUD + geospatial search
├── monitoring-service/    # Metrics collection + WebSocket
├── notification-service/  # Alert delivery via RabbitMQ
├── eureka-server/         # Service discovery
├── common/                # Shared utilities + HMAC security
├── frontend/              # React dashboard
├── init-db/               # Database seed scripts
└── docs/                  # Architecture + performance docs
```

---

## Documentation

**Getting Started:**
- [Installation & Setup](docs/SETUP.md)
- [API Reference](docs/API.md)
- [Deployment Guide](docs/DEPLOYMENT.md)

**Architecture:**
- [Design Overview](docs/ARCHITECTURE.md)
- [Authentication](docs/AUTH_IMPLEMENTATION.md)
- [Monolith vs Microservices](docs/MONOLITH_VS_MICROSERVICES.md)

**Performance & Security:**
- [Geospatial Optimization](docs/POSTGIS_MIGRATION.md)
- [Load Testing Results](docs/STRESS_TEST_RESULTS.md)
- [Security Implementation](docs/HEADER_SPOOFING_BLOCKED.md)
- [Production Gaps](docs/IMPROVEMENTS.md)
- [Kubernetes Migration](docs/K8S_MIGRATION.md)
- [Testing Strategy](docs/TESTING.md)

---

## License

MIT License - [LICENSE](LICENSE)

**Author:** Ersel Seyit • [GitHub](https://github.com/ErselSeyit) • [erselseyit@gmail.com](mailto:erselseyit@gmail.com)
