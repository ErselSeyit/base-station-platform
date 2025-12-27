# Base Station Operations & Maintenance Platform

[![CI/CD Pipeline](https://github.com/ErselSeyit/base-station-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/ErselSeyit/base-station-platform/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-6DB33F?style=flat&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=flat&logo=docker&logoColor=white)](https://www.docker.com/)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-Helm-326CE5?style=flat&logo=kubernetes&logoColor=white)](https://kubernetes.io/)

**Advanced microservices platform for base station operations and maintenance** - A showcase project demonstrating advanced Java and Spring Boot microservices architecture, service discovery, API gateway patterns, and cloud-native development skills.

A comprehensive microservices-based platform for managing and monitoring base station operations, built with Java 21, Spring Boot 3.2.0, and modern cloud-native technologies.

## 📖 About

The Base Station Operations & Maintenance Platform is an advanced microservices application designed to manage and monitor telecommunications base stations. This platform demonstrates modern software engineering practices including microservices architecture, service discovery, API gateway patterns, and containerization.

### Key Features

- **Microservices Architecture**: Modular design with independent, scalable services
- **Service Discovery**: Automatic service registration and discovery using Eureka
- **API Gateway**: Centralized entry point with JWT authentication
- **Multi-Database Support**: PostgreSQL for relational data, MongoDB for time-series metrics
- **RESTful APIs**: Well-designed REST endpoints with OpenAPI/Swagger documentation
- **Containerization**: Fully containerized with Docker, Compose, and Kubernetes/Helm
- **CI/CD Pipeline**: GitHub Actions for automated testing and Docker image builds
- **Observability**: Prometheus metrics, Grafana dashboards, Zipkin tracing
- **Comprehensive Testing**: Unit, integration tests with Testcontainers

### 🎯 Technical Highlights

| Feature | Description | Why It Matters |
|---------|-------------|----------------|
| **Real-time WebSocket Streaming** | Live metric updates pushed to dashboards | Shows reactive/event-driven architecture skills |
| **Alerting Rules Engine** | Configurable threshold-based alerting with multiple operators | Demonstrates domain modeling and strategy pattern |
| **Geospatial Queries** | Haversine formula for "stations near point" search | Shows advanced SQL and algorithm knowledge |
| **Correlation ID Tracing** | Request tracking across service boundaries via MDC | Production-ready distributed tracing |
| **Immutable Domain Objects** | AlertRule with builder pattern and `withX` methods | Demonstrates clean code and DDD principles |
| **Circuit Breakers** | Resilience4j for fault tolerance | Shows understanding of distributed systems |
| **Structured JSON Logging** | Logstash-format logs for production environments | Ready for log aggregation (ELK/Splunk) |

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         REAL-TIME ARCHITECTURE                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   Metric Ingestion          Alerting Engine         WebSocket Push      │
│   ┌──────────────┐         ┌──────────────┐        ┌──────────────┐     │
│   │   POST       │────────▶│  Evaluate    │───────▶│  Broadcast   │     │
│   │   /metrics   │         │  Rules       │        │  to Clients  │     │
│   └──────────────┘         └──────────────┘        └──────────────┘     │
│          │                        │                        │            │
│          ▼                        ▼                        ▼            │
│   ┌──────────────┐         ┌──────────────┐        ┌──────────────┐     │
│   │   MongoDB    │         │  Trigger     │        │  Dashboard   │     │
│   │   (persist)  │         │  Alerts      │        │  (real-time) │     │
│   └──────────────┘         └──────────────┘        └──────────────┘     │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Use Cases

- **Base Station Management**: Create, update, and manage base station information
- **Real-time Monitoring**: Collect and analyze metrics (CPU, memory, power, temperature)
- **Alert Management**: Generate and manage notifications for critical events
- **Geographic Search**: Find base stations by location with radius-based queries
- **Operational Insights**: Track station status, power consumption, and performance metrics

### Architecture Highlights

- **Spring Boot 3.2.0**: Modern Java framework with auto-configuration
- **Spring Cloud 2023.0.0**: Cloud-native patterns and service mesh capabilities
- **Eureka Server**: Netflix service discovery for microservices
- **Spring Cloud Gateway**: Reactive API gateway with routing and filtering
- **JPA/Hibernate**: Object-relational mapping for PostgreSQL
- **Spring Data MongoDB**: NoSQL database integration
- **Docker Compose**: Multi-container orchestration for local development

This project serves as a demonstration of advanced Java development skills, showcasing best practices in microservices design, API development, database integration, and DevOps tooling.

## 🚀 Quick Start

Start all services with one command:

```bash
docker-compose up -d
```

**That's it!** All services will start automatically. Wait 30-60 seconds for initialization.

To stop all services:
```bash
docker-compose down
```

## 📋 Table of Contents

- [Quick Start](#-quick-start-one-click-run)
- [Architecture](#-architecture)
- [Services](#-services)
- [API Endpoints](#-api-endpoints)
- [Prerequisites](#-prerequisites)
- [Installation](#-installation)
- [Configuration](#-configuration)
- [Development](#-development)
- [Testing](#-testing)
- [Deployment](#-deployment)
- [Troubleshooting](#-troubleshooting)

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              SYSTEM ARCHITECTURE                                    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│                                                                                     │
│   ┌─────────────────────────────────────────────────────────────────────────┐       │ 
│   │                            API Gateway                                  │       │
│   │                          (Port 8080)                                    │       │
│   └───────────────────────────┬─────────────────────────────────────────────┘       │
│                               │                                                           │
│   ┌───────────────────────────┼───────────────────────────┬───────────────────────│ │
│   │                           │                           │                       │ │
│   │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │ │
│   │  │ Base Station │  │   Monitoring │  │  Notification │  │   Auth      │       │ │
│   │  │   Service    │  │    Service   │  │    Service    │  │  Service    │       │ │
│   │  │(Port 8084)   │  │  (Port 8085) │  │  (Port 8086)  │  │  (Port 8087) │      │ │
│   │  └──────┬───────┘  └───────┬──────┘  └──────┬───────┘  └──────┬───────┘       │ │
│   │         │                  │                 │                  │             │ │
│   │         │                  │                 │                  │             │ │
│   │  ┌──────▼───────┐  ┌───────▼────────┐  ┌──────▼───────┐  ┌──────▼───────┐     │ │
│   │  │  PostgreSQL  │  │   MongoDB      │  │  PostgreSQL   │  │  PostgreSQL  │    │ │
│   │  │  (Port 5434) │  │  (Port 27018)  │  │  (Port 5435)  │  │  (Port 5436) │    │ │
│   │  └──────┬───────┘  └───────┬────────┘  └──────┬───────┘  └──────┬───────┘     │ │
│   │         │                  │                   │                  │           │ │
│   │         │                  │                   │                  │           │ │
│   │         └──────────────────┼───────────────────┼──────────────────────────────│ │
│   │                            │                   │                              │ │
│   │                            │                   │                              │ │
│   │                    ┌───────▼───────────────────▼───────┐                      │ │
│   │                    │      Eureka Server               │                      │ │
│   │                    │      (Port 8762)                 │                      │ │
│   │                    └───────────────────────────────────┘                      │ │
│   │                                                                               │ │
│   │                                                                               │ │
│   └───────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                     │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## 🔧 Services

### 1. Eureka Server (Service Discovery)
- **Port**: 8762 (external), 8761 (internal)
- **Dashboard**: http://localhost:8762
- **Purpose**: Service registration and discovery

### 2. API Gateway
- **Port**: 8080
- **Purpose**: Single entry point for all API requests
- **Routes**: `/api/v1/*`

### 3. Base Station Service
- **Port**: 8084 (external), 8081 (internal)
- **Database**: PostgreSQL (Port 5434)
- **Features**: CRUD operations, geographic search, status management

### 4. Monitoring Service
- **Port**: 8085 (external), 8082 (internal)
- **Database**: MongoDB (Port 27018)
- **Features**: Real-time metrics collection, time-range queries

### 5. Notification Service
- **Port**: 8086 (external), 8083 (internal)
- **Database**: PostgreSQL (Port 5435)
- **Features**: Async notifications, alert management

### 6. Auth Service
- **Port**: 8087 (external), 8084 (internal)
- **Database**: PostgreSQL (Port 5436)
- **Features**: JWT token generation and validation

## 📡 API Endpoints

### Base Station Service

#### Create Base Station
```bash
POST http://localhost:8080/api/v1/stations
Content-Type: application/json

{
  "stationName": "BS-001",
  "location": "Downtown",
  "latitude": 40.7128,
  "longitude": -74.0060,
  "stationType": "MACRO_CELL",
  "status": "ACTIVE",
  "powerConsumption": 1500.0
}
```

#### Get All Stations
```bash
GET http://localhost:8080/api/v1/stations
```

#### Get Station by ID
```bash
GET http://localhost:8080/api/v1/stations/{id}
```

#### Update Station
```bash
PUT http://localhost:8080/api/v1/stations/{id}
Content-Type: application/json

{
  "stationName": "BS-001-Updated",
  "status": "MAINTENANCE"
}
```

#### Delete Station
```bash
DELETE http://localhost:8080/api/v1/stations/{id}
```

#### Search by Location
```bash
GET http://localhost:8080/api/v1/stations/search?latitude=40.7128&longitude=-74.0060&radius=10
```

### Monitoring Service

#### Record Metric
```bash
POST http://localhost:8080/api/v1/metrics
Content-Type: application/json

{
  "stationId": 1,
  "stationName": "BS-001",
  "metricType": "CPU_USAGE",
  "value": 75.5,
  "unit": "%"
}
```

#### Get Metrics by Station
```bash
GET http://localhost:8080/api/v1/metrics/station/{stationId}
```

#### Get Metrics by Time Range
```bash
GET http://localhost:8080/api/v1/metrics?startTime=2024-01-01T00:00:00&endTime=2024-01-31T23:59:59
```

### Notification Service

#### Create Notification
```bash
POST http://localhost:8080/api/v1/notifications?stationId=1&message=High%20CPU%20usage&type=ALERT
```

#### Get All Notifications
```bash
GET http://localhost:8080/api/v1/notifications
```

#### Get Notifications by Station
```bash
GET http://localhost:8080/api/v1/notifications/station/{stationId}
```

#### Mark Notification as Read
```bash
PUT http://localhost:8080/api/v1/notifications/{id}/read
```

## 📦 Prerequisites

- **Java**: 21 or higher
- **Maven**: 3.9+ 
- **Docker**: 20.10+
- **Docker Compose**: 2.0+

### Verify Installation
```bash
java -version    # Should show Java 21+
mvn -version     # Should show Maven 3.9+
docker --version # Should show Docker 20.10+
docker-compose --version # Should show Docker Compose 2.0+
```

## 🔨 Installation

### Option 1: Docker Compose (Recommended)

1. **Clone the repository**
   ```bash
   git clone https://github.com/ErselSeyit/base-station-platform.git
   cd base-station-platform
   ```

2. **Start all services**
   ```bash
   docker-compose up -d
   ```

3. **Check service status**
   ```bash
   docker-compose ps
   ```

4. **View logs**
   ```bash
   docker-compose logs -f
   ```

### Option 2: Local Development

1. **Start databases only**
   ```bash
   docker-compose up -d postgres-basestation postgres-notification mongodb
   ```

2. **Build the project**
   ```bash
   mvn clean install -DskipTests
   ```

3. **Start services in order**
   ```bash
   # Terminal 1: Eureka Server
   cd eureka-server && mvn spring-boot:run
   
   # Terminal 2: Base Station Service
   cd base-station-service && mvn spring-boot:run
   
   # Terminal 3: Monitoring Service
   cd monitoring-service && mvn spring-boot:run
   
   # Terminal 4: Notification Service
   cd notification-service && mvn spring-boot:run
   
   # Terminal 5: API Gateway
   cd api-gateway && mvn spring-boot:run
   ```

## ⚙️ Configuration

### Environment Variables

Create a `.env` file (see `.env.example`):

```env
# Database Configuration
POSTGRES_BASESTATION_DB=basestationdb
POSTGRES_BASESTATION_USER=postgres
POSTGRES_BASESTATION_PASSWORD=postgres
POSTGRES_NOTIFICATION_DB=notificationdb
POSTGRES_NOTIFICATION_USER=postgres
POSTGRES_NOTIFICATION_PASSWORD=postgres
MONGO_DB=monitoringdb

# Service Ports
EUREKA_PORT=8761
API_GATEWAY_PORT=8080
BASE_STATION_PORT=8081
MONITORING_PORT=8082
NOTIFICATION_PORT=8083
```

### Application Properties

Each service has its own `application.yml` in `src/main/resources/`. Key configurations:

- **Eureka**: Service discovery settings
- **Database**: Connection strings and JPA settings
- **Server**: Port configurations

## 🧪 Testing

### Run All Tests
```bash
mvn test
```

### Run Tests for Specific Service
```bash
mvn test -pl base-station-service
```

### Integration Tests
```bash
mvn verify
```

### Test Architecture

The project uses a comprehensive testing strategy with test-specific application configurations:

- **Unit Tests**: Fast, isolated tests using mocks
- **Integration Tests**: Full application context with Testcontainers for PostgreSQL
- **Contract Tests**: Spring Cloud Contract for API contract verification
- **Resilience Tests**: Circuit breaker and timeout testing with WireMock

#### Test Application Classes

To avoid conflicts with production configurations, test-specific application classes are used:

- `ContractTestApplication`: For Spring Cloud Contract tests
  - Excludes Redis and Eureka auto-configurations
  - Uses H2 in-memory database
  - Isolated component scanning to prevent conflicts

- `IntegrationTestApplication`: For integration tests
  - Excludes Redis and Eureka auto-configurations
  - Uses Testcontainers PostgreSQL
  - Full application context with all services

- `TestApplication`: For resilience tests
  - Minimal configuration for circuit breaker testing
  - WireMock for external service mocking

#### Test Configuration

Tests automatically configure:
- H2 in-memory database for contract tests
- PostgreSQL via Testcontainers for integration tests
- Disabled Eureka client registration
- Disabled Redis caching
- Mock monitoring service URLs

### Running Specific Test Types

```bash
# Run only unit tests
mvn test -Dtest='*Test' -DfailIfNoTests=false

# Run only integration tests
mvn test -Dtest='*IntegrationTest'

# Run only contract tests
mvn test -Dtest='*Contract*'

# Run resilience tests
mvn test -Dtest='*Resilience*'
```

## 🚢 Deployment

### Build Docker Images
```bash
docker-compose build
```

### Production Deployment
1. Update `docker-compose.yml` with production settings
2. Set environment variables
3. Use Docker Swarm or Kubernetes for orchestration

### CI/CD

The project includes GitHub Actions workflows that:

#### CI/CD Pipeline (`.github/workflows/ci-cd.yml`)
- **Backend Tests**: Runs unit and integration tests with PostgreSQL and MongoDB services
- **Frontend Tests**: Runs linting, unit tests, and E2E tests with Playwright
- **Code Quality**: SonarQube analysis
- **Docker Build**: Builds all service Docker images
- **Artifact Upload**: Saves Docker images as artifacts for deployment

#### CI Pipeline (`.github/workflows/ci.yml`)
- **Build & Test**: Comprehensive test suite execution
- **Security Scan**: Trivy vulnerability scanning
- **Docker Build & Push**: Builds and pushes images to container registry
- **Staging Deployment**: Automated deployment to staging environment

#### Docker Image Artifacts

Docker images are saved as tar files and uploaded as GitHub Actions artifacts:
- Images are built for all services (base-station-service, monitoring-service, notification-service, api-gateway, eureka-server, frontend)
- Artifacts are retained for 7 days
- Images can be downloaded and loaded using `docker load`

## 🐛 Troubleshooting

### Port Already in Use
```bash
# Windows
netstat -ano | findstr :8080

# Linux/Mac
lsof -i :8080
```

### Services Not Starting
1. Check logs: `docker-compose logs [service-name]`
2. Verify Docker resources (memory, CPU)
3. Ensure all ports are available
4. Check database health: `docker-compose ps`

### Database Connection Issues
- Wait longer for databases to initialize (30-60 seconds)
- Check database logs: `docker-compose logs postgres-basestation`
- Verify network connectivity: `docker network ls`

### Eureka Service Registration Issues
- Ensure Eureka Server starts first
- Check Eureka dashboard: http://localhost:8761
- Verify service URLs in `application.yml`

## 📚 Additional Documentation

- [API Documentation](docs/API.md) - Complete API reference
- [Docker Guide](docs/DOCKER.md) - Docker setup and usage
- [Development Guide](docs/DEVELOPMENT.md) - Development setup and guidelines

## 📚 API Documentation

Once services are running, access the interactive API documentation:

| Service | Swagger UI | OpenAPI Spec |
|---------|------------|--------------|
| Base Station | http://localhost:8084/swagger-ui.html | http://localhost:8084/v3/api-docs |
| Monitoring | http://localhost:8085/swagger-ui.html | http://localhost:8085/v3/api-docs |

### WebSocket Endpoint

Connect to real-time metrics stream:
```javascript
const ws = new WebSocket('ws://localhost:8085/ws/metrics');
ws.onmessage = (event) => {
  const metric = JSON.parse(event.data);
  console.log('Real-time metric:', metric);
};
```

### Alerting Rules API

```bash
# List all alerting rules
GET /api/v1/alerts/rules

# Update threshold dynamically
PUT /api/v1/alerts/rules/cpu-critical/threshold?threshold=95

# Disable a rule
PUT /api/v1/alerts/rules/cpu-critical/disable
```

## 🛠️ Technology Stack

### Backend
| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 21 | Language |
| Spring Boot | 3.2.0 | Framework |
| Spring Cloud | 2023.0.0 | Microservices patterns |
| Spring Data JPA | - | PostgreSQL ORM |
| Spring Data MongoDB | - | NoSQL integration |
| Spring WebSocket | - | Real-time streaming |
| Resilience4j | 2.1.0 | Circuit breakers |
| SpringDoc OpenAPI | 2.3.0 | API documentation |

### Infrastructure
| Technology | Purpose |
|------------|---------|
| PostgreSQL 18 | Relational data |
| MongoDB 8.2 | Time-series metrics |
| Redis 8 | Caching |
| RabbitMQ 4 | Message queue |
| Netflix Eureka | Service discovery |

### DevOps
| Technology | Purpose |
|------------|---------|
| Docker & Compose | Containerization |
| Kubernetes & Helm | Orchestration |
| GitHub Actions | CI/CD pipeline |
| Prometheus | Metrics collection |
| Grafana | Dashboards |
| Zipkin | Distributed tracing |

### Testing
| Technology | Purpose |
|------------|---------|
| JUnit 5 | Unit testing |
| Mockito | Mocking |
| Testcontainers | Integration testing |
| Spring Cloud Contract | API contract testing |
| WireMock | Service mocking for resilience tests |
| Awaitility | Async test assertions |
| AssertJ | Fluent assertions |
| Playwright | E2E testing (frontend) |
| H2 Database | In-memory database for contract tests |

## 📝 Recent Improvements

### Test Infrastructure Enhancements (Latest)

- **Fixed ApplicationContext Failures**: Resolved test context loading issues by:
  - Creating test-specific application classes to avoid conflicts
  - Excluding problematic auto-configurations (Redis, Eureka) in tests
  - Using proper component scanning filters to prevent duplicate bean definitions
  - Configuring test-specific datasource properties

- **Improved CI/CD Pipeline**:
  - Fixed Docker image artifact upload (images are now properly saved as tar files)
  - Added artifact retention policies
  - Enhanced test reporting and artifact management

- **Enhanced Test Reliability**:
  - Better WireMock configuration for resilience tests
  - Improved retry logic and timing for async operations
  - Proper test isolation with separate application contexts

### Testing Improvements

- **Test Application Classes**: Created dedicated test applications to prevent configuration conflicts
- **Better Test Isolation**: Each test type uses its own application context
- **Improved Error Handling**: Better diagnostics for test failures
- **Enhanced Resilience Testing**: More robust circuit breaker and timeout testing

## 📝 About the Project

This project is a comprehensive demonstration of modern microservices architecture and advanced Java development. It showcases:

- **Microservices Design Patterns**: Service discovery, API gateway, circuit breakers
- **Database Integration**: Both SQL (PostgreSQL) and NoSQL (MongoDB) databases
- **Cloud-Native Technologies**: Containerization, orchestration, and service mesh
- **Best Practices**: Clean code, testing, documentation, and CI/CD
- **Comprehensive Testing**: Multi-layered testing strategy with unit, integration, contract, and resilience tests

Built as a portfolio project to demonstrate proficiency in:
- Java 21 and Spring Boot 3.2.0 ecosystem
- Microservices architecture and design
- RESTful API development
- Database design and integration
- Docker and containerization
- DevOps and CI/CD practices
- Test-driven development and test infrastructure

## 📝 License

This project is part of a portfolio demonstration.

## 👤 Author

**Ersel Seyit**

- GitHub: [@ErselSeyit](https://github.com/ErselSeyit)
- Email: erselseyit@gmail.com

## 🔗 Links

- **Repository**: https://github.com/ErselSeyit/base-station-platform

---

**Happy Coding! 🚀**
