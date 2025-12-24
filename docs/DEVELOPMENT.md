# Development Guide

This guide provides information for developers working on the Base Station Platform.

## Development Setup

### Prerequisites
- Java 17+
- Maven 3.9+
- Docker & Docker Compose
- IDE (IntelliJ IDEA, Eclipse, VS Code)

### Initial Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/ErselSeyit/base-station-platform.git
   cd base-station-platform
   ```

2. **Start databases only**
   ```bash
   docker-compose up -d postgres-basestation postgres-notification mongodb
   ```

3. **Build the project**
   ```bash
   mvn clean install -DskipTests
   ```

## Running Services Locally

### Start Services in Order

1. **Eureka Server** (Terminal 1)
   ```bash
   cd eureka-server
   mvn spring-boot:run
   ```

2. **Base Station Service** (Terminal 2)
   ```bash
   cd base-station-service
   mvn spring-boot:run
   ```

3. **Monitoring Service** (Terminal 3)
   ```bash
   cd monitoring-service
   mvn spring-boot:run
   ```

4. **Notification Service** (Terminal 4)
   ```bash
   cd notification-service
   mvn spring-boot:run
   ```

5. **API Gateway** (Terminal 5)
   ```bash
   cd api-gateway
   mvn spring-boot:run
   ```

## Project Structure

```
base-station-platform/
├── api-gateway/          # API Gateway service
├── base-station-service/ # Base Station management
├── eureka-server/        # Service discovery
├── monitoring-service/   # Metrics collection
├── notification-service/ # Notification management
├── docker-compose.yml    # Docker orchestration
├── pom.xml              # Parent POM
└── docs/                # Documentation
```

## Code Style

- Follow Java naming conventions
- Use meaningful variable and method names
- Add Javadoc comments for public methods
- Keep methods focused and single-purpose

## Testing

### Run All Tests
```bash
mvn test
```

### Run Tests for Specific Module
```bash
mvn test -pl base-station-service
```

### Run with Coverage
```bash
mvn test jacoco:report
```

## Building

### Build All Modules
```bash
mvn clean install
```

### Build Specific Module
```bash
mvn clean install -pl base-station-service
```

### Skip Tests
```bash
mvn clean install -DskipTests
```

## Debugging

### Remote Debugging

Add JVM arguments when starting services:
```bash
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
```

Then attach debugger to port 5005.

## Database Migrations

Currently using JPA auto-ddl. For production, consider:
- Flyway
- Liquibase

## Adding New Features

1. Create feature branch
2. Implement feature with tests
3. Update documentation
4. Create pull request

## Common Issues

### Port Already in Use
Change port in `application.yml` or stop conflicting service.

### Database Connection Failed
- Ensure databases are running: `docker-compose ps`
- Check connection strings in `application.yml`
- Verify network connectivity

### Service Not Registering with Eureka
- Ensure Eureka Server is running first
- Check Eureka URL in `application.yml`
- Verify network connectivity

