# Testing Guide

## Coverage

| Area | Coverage | Test Count |
|------|----------|------------|
| Backend Services | 100% | 19 test classes |
| Frontend | ~87% | 103 tests |

## Test Types

### Backend
- **Unit tests**: JUnit 5 + Mockito
- **Integration tests**: Testcontainers (PostgreSQL, MongoDB, RabbitMQ)
- **Contract tests**: Spring Cloud Contract
- **Resilience tests**: WireMock + Circuit Breakers

### Frontend
- **Unit tests**: Vitest + React Testing Library
- **E2E tests**: Playwright

## Running Tests

### Backend Tests
```bash
# Run all tests
mvn test

# Run specific test type
mvn test -Dtest='*IntegrationTest'
mvn test -Dtest='*Contract*'

# Skip tests during build
mvn clean install -DskipTests
```

### Frontend Tests
```bash
cd frontend

# Unit tests
npm test

# E2E tests
npm run test:e2e

# Coverage report
npm run test:coverage
```

## Demo Mode

When running locally without Docker or external services, enable Demo Mode to skip infrastructure-heavy integration tests:

```bash
# System property
mvn -Ddemo.mode=true clean test

# Or environment variable
DEMO_MODE=true mvn clean test
```

In Demo Mode, integration suites in base-station-service are disabled:
- `BaseStationIntegrationTest`
- `BatchMetricsIntegrationTest`
- `JwtFlowIntegrationTest`

Omit the property/variable to run full integration tests when Docker is available.

## CI/CD

All tests run automatically on every push via GitHub Actions:
- Matrix builds for parallel execution
- Testcontainers for real database tests
- Frontend tests enforce failures (no silent passes)

See [.github/workflows/ci.yml](../.github/workflows/ci.yml) for pipeline configuration.
