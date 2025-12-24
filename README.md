# Base Station Operations & Maintenance Platform

A comprehensive microservices-based platform for managing and monitoring base station operations, built with Java 17, Spring Boot, and modern cloud-native technologies.

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
┌─────────────────────────────────────────────────────────────┐
│                        API Gateway                            │
│                      (Port 8080)                             │
└───────────────────────────┬───────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
┌───────▼────────┐  ┌────────▼────────┐  ┌──────▼──────────┐
│ Base Station   │  │   Monitoring    │  │  Notification   │
│    Service     │  │    Service      │  │    Service      │
│  (Port 8081)   │  │  (Port 8082)   │  │  (Port 8083)     │
└───────┬────────┘  ┌────────┬────────┘  ┌──────┬──────────┘
        │           │        │           │      │
┌───────▼────────┐  │  ┌─────▼─────┐    │  ┌───▼──────────┐
│  PostgreSQL    │  │  │  MongoDB   │    │  │  PostgreSQL │
│  (Port 5432)   │  │  │ (Port 27017)│   │  │ (Port 5433)  │
└────────────────┘  │  └────────────┘    │  └──────────────┘
                    │                    │
            ┌───────▼────────────────────▼───────┐
            │      Eureka Server                 │
            │      (Port 8761)                   │
            └────────────────────────────────────┘
```

## 🔧 Services

### 1. Eureka Server (Service Discovery)
- **Port**: 8761
- **Dashboard**: http://localhost:8761
- **Purpose**: Service registration and discovery

### 2. API Gateway
- **Port**: 8080
- **Purpose**: Single entry point for all API requests
- **Routes**: `/api/v1/*`

### 3. Base Station Service
- **Port**: 8081
- **Database**: PostgreSQL
- **Features**: CRUD operations, geographic search, status management

### 4. Monitoring Service
- **Port**: 8082
- **Database**: MongoDB
- **Features**: Real-time metrics collection, time-range queries

### 5. Notification Service
- **Port**: 8083
- **Database**: PostgreSQL
- **Features**: Async notifications, alert management

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

- **Java**: 17 or higher
- **Maven**: 3.9+ 
- **Docker**: 20.10+
- **Docker Compose**: 2.0+

### Verify Installation
```bash
java -version    # Should show Java 17+
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

The project includes GitHub Actions workflow (`.github/workflows/ci-cd.yml`) that:
- Builds the project
- Runs tests
- Builds Docker images
- (Optional) Deploys to production

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

## 🛠️ Technology Stack

- **Java**: 17
- **Spring Boot**: 3.2.0
- **Spring Cloud**: 2023.0.0
- **Eureka**: Service Discovery
- **PostgreSQL**: Relational Database
- **MongoDB**: NoSQL Database
- **Docker**: Containerization
- **Maven**: Build Tool
- **JUnit 5**: Testing Framework
- **Mockito**: Mocking Framework

## 📝 License

This project is part of a portfolio demonstration.

## 👤 Author

Ersel Seyit

## 🔗 Links

- **Repository**: https://github.com/ErselSeyit/base-station-platform
- **Eureka Dashboard**: http://localhost:8761
- **API Gateway**: http://localhost:8080

---

**Happy Coding! 🚀**
