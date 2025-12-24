# Base Station Operations & Maintenance Platform

A comprehensive microservices-based platform for managing and monitoring base station operations, built with Java 21, Spring Boot, and modern cloud-native technologies.

## 🎯 Project Overview

This project demonstrates enterprise-level Java development skills including:
- **Microservices Architecture** with Spring Cloud
- **RESTful APIs** with Spring Boot
- **Database Integration** (PostgreSQL for RDBMS, MongoDB for NoSQL)
- **Service Discovery** with Eureka
- **API Gateway** for routing
- **Multithreaded Processing** for concurrent operations
- **Comprehensive Unit Testing** with Mockito
- **Docker & Docker Compose** for containerization
- **CI/CD Pipeline** with GitHub Actions

## 🏗️ Architecture

```
┌─────────────┐
│  API Gateway │ (Port 8080)
└──────┬──────┘
       │
       ├──────────────┬──────────────┬──────────────┐
       │              │              │              │
┌──────▼──────┐ ┌─────▼──────┐ ┌─────▼──────┐ ┌─────▼──────┐
│ Base Station│ │ Monitoring │ │Notification│ │   Eureka   │
│   Service   │ │  Service   │ │  Service   │ │   Server   │
│  (Port 8081)│ │ (Port 8082)│ │ (Port 8083)│ │ (Port 8761)│
└──────┬──────┘ └─────┬──────┘ └─────┬──────┘ └────────────┘
       │              │              │
┌──────▼──────┐ ┌─────▼──────┐ ┌─────▼──────┐
│ PostgreSQL  │ │  MongoDB   │ │ PostgreSQL │
│ (Port 5432) │ │ (Port 27017)│ │ (Port 5433)│
└─────────────┘ └────────────┘ └────────────┘
```

## 🚀 Features

### Base Station Service
- CRUD operations for base stations
- Geographic search (latitude/longitude)
- Station status and type management
- RESTful API with validation
- PostgreSQL integration with JPA

### Monitoring Service
- Real-time metric collection and storage
- Time-range queries
- Threshold-based alerts
- MongoDB for high-performance metric storage
- Multiple metric types (CPU, Memory, Signal Strength, etc.)

### Notification Service
- Asynchronous notification processing
- Multithreaded notification sending
- Batch processing capabilities
- Status tracking (Pending, Sent, Failed)
- PostgreSQL for notification persistence

### API Gateway
- Centralized routing
- Load balancing
- Service discovery integration

## 🛠️ Technology Stack

- **Java**: 21
- **Spring Boot**: 3.2.0
- **Spring Cloud**: 2023.0.0
- **Spring Data JPA**: For PostgreSQL
- **Spring Data MongoDB**: For NoSQL
- **Eureka**: Service discovery
- **Spring Cloud Gateway**: API Gateway
- **PostgreSQL**: 15 (RDBMS)
- **MongoDB**: 7.0 (NoSQL)
- **Maven**: Build tool
- **Docker**: Containerization
- **Mockito**: Unit testing
- **JUnit 5**: Testing framework

## 📋 Prerequisites

- Java 21 or higher
- Maven 3.9+
- Docker and Docker Compose
- Git

## 🏃 Getting Started

### Option 1: Using Docker Compose (Recommended)

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd base-station-platform
   ```

2. **Start all services**
   ```bash
   docker-compose up -d
   ```

3. **Verify services are running**
   ```bash
   docker-compose ps
   ```

4. **Access services**
   - API Gateway: http://localhost:8080
   - Eureka Dashboard: http://localhost:8761
   - Base Station Service: http://localhost:8081
   - Monitoring Service: http://localhost:8082
   - Notification Service: http://localhost:8083

### Option 2: Local Development

1. **Start databases**
   ```bash
   docker-compose up -d postgres-basestation postgres-notification mongodb
   ```

2. **Start Eureka Server**
   ```bash
   cd eureka-server
   mvn spring-boot:run
   ```

3. **Start microservices** (in separate terminals)
   ```bash
   # Terminal 1
   cd base-station-service
   mvn spring-boot:run
   
   # Terminal 2
   cd monitoring-service
   mvn spring-boot:run
   
   # Terminal 3
   cd notification-service
   mvn spring-boot:run
   
   # Terminal 4
   cd api-gateway
   mvn spring-boot:run
   ```

## 📚 API Documentation

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
  "powerConsumption": 1500.0,
  "description": "Main downtown base station"
}
```

#### Get All Stations
```bash
GET http://localhost:8080/api/v1/stations
```

#### Get Station by ID
```bash
GET http://localhost:8080/api/v1/stations/1
```

#### Search Stations by Area
```bash
GET http://localhost:8080/api/v1/stations/search/area?minLat=40.0&maxLat=41.0&minLon=-75.0&maxLon=-74.0
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
GET http://localhost:8080/api/v1/metrics/station/1
```

#### Get Metrics Above Threshold
```bash
GET http://localhost:8080/api/v1/metrics/threshold?metricType=CPU_USAGE&threshold=80.0
```

### Notification Service

#### Create Notification
```bash
POST http://localhost:8080/api/v1/notifications?stationId=1&message=Alert: High CPU usage&type=ALERT
```

#### Process Pending Notifications
```bash
POST http://localhost:8080/api/v1/notifications/process-pending
```

## 🧪 Testing

### Run All Tests
```bash
mvn clean test
```

### Run Tests for Specific Module
```bash
cd base-station-service
mvn test
```

### Test Coverage
The project includes comprehensive unit tests using Mockito and JUnit 5:
- Service layer tests
- Controller tests
- Repository tests (with embedded databases)

## 🐳 Docker

### Build Individual Services
```bash
docker build -t base-station-service:latest -f base-station-service/Dockerfile .
docker build -t monitoring-service:latest -f monitoring-service/Dockerfile .
docker build -t notification-service:latest -f notification-service/Dockerfile .
docker build -t api-gateway:latest -f api-gateway/Dockerfile .
docker build -t eureka-server:latest -f eureka-server/Dockerfile .
```

### Docker Compose Commands
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

## 🔄 CI/CD Pipeline

The project includes a GitHub Actions workflow (`.github/workflows/ci-cd.yml`) that:
- Runs tests on push/PR
- Builds the project
- Creates Docker images
- Uploads test results and artifacts

## 📁 Project Structure

```
base-station-platform/
├── api-gateway/              # API Gateway service
├── base-station-service/     # Base station management service
├── monitoring-service/       # Metrics monitoring service (MongoDB)
├── notification-service/     # Notification service (multithreaded)
├── eureka-server/            # Service discovery server
├── docker-compose.yml        # Docker Compose configuration
├── pom.xml                   # Parent POM
└── README.md                 # This file
```

## 🎓 Design Patterns & Best Practices

- **Repository Pattern**: Data access abstraction
- **DTO Pattern**: Data transfer objects for API
- **Service Layer Pattern**: Business logic separation
- **RESTful API Design**: Standard HTTP methods and status codes
- **Exception Handling**: Global exception handler
- **Validation**: Bean validation with Jakarta Validation
- **Async Processing**: @Async for concurrent operations
- **Thread Pool Management**: ExecutorService for controlled concurrency

## 🔒 Security Considerations

For production deployment, consider:
- Authentication and Authorization (Spring Security)
- API rate limiting
- HTTPS/TLS encryption
- Database connection encryption
- Secrets management (Vault, AWS Secrets Manager)

## 📈 Performance Optimizations

- Connection pooling for databases
- Caching strategies (Redis)
- Async processing for notifications
- Database indexing
- Query optimization

## 🚧 Future Enhancements

- [ ] Kubernetes deployment manifests
- [ ] Distributed tracing (Zipkin/Jaeger)
- [ ] Centralized logging (ELK Stack)
- [ ] API documentation (Swagger/OpenAPI)
- [ ] Integration tests
- [ ] Performance testing
- [ ] Monitoring and alerting (Prometheus, Grafana)

## 📝 License

This project is created as a portfolio/demonstration project.

## 👤 Author

Created as a demonstration project showcasing enterprise Java development skills.

---

**Note**: This project is designed to demonstrate proficiency in Java development, Spring Boot, microservices architecture, and modern DevOps practices. It showcases skills relevant to enterprise software development positions.

