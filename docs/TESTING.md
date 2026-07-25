# Testing Guide

## Test Suites

| Area | Framework | Count |
| ---- | --------- | ----- |
| Backend (Java) | JUnit 5 + Mockito + Testcontainers | 62 test classes, 700+ tests |
| Frontend | Vitest + React Testing Library | 318 unit tests (15 files) |
| Frontend E2E | Playwright | 19 cases (5 specs) |
| Edge bridge (Go) | `go test` | 9 packages under test |
| AI diagnostic (Python) | pytest | 224 tests (`pytest` in `ai-diagnostic/`) |
| Device protocol (C) | custom harness | 21 tests (`make -C device-protocol-c test`) |

Coverage is tracked as an indicator, not a gate. Following Khorikov's
*Unit Testing* (ch. 1), we do not target a fixed coverage percentage:
an assertion-free test can hit 100% and verify nothing. The Go `config`
package sits at ~94% because it is pure validation logic worth covering
closely; hardware-facing adapters are deliberately lower and belong in
integration tests against a real device.

## Test Types

### Backend
- **Unit tests**: JUnit 5 + Mockito
- **Integration tests**: Testcontainers (PostgreSQL, MongoDB, RabbitMQ)
- **Contract tests**: Spring Cloud Contract
- **Resilience tests**: WireMock + Circuit Breakers

Integration tests need Docker: they start real PostgreSQL, MongoDB and
RabbitMQ containers. From a clean checkout, bring the datastores up first
(`docker compose up -d postgres mongodb redis rabbitmq`) or run in Demo
Mode (below).

### Frontend
- **Unit tests**: Vitest + React Testing Library
- **E2E tests**: Playwright

### Edge bridge (Go)

```bash
cd edge-bridge
go test ./...        # unit tests
go test -race ./...  # with the race detector
go vet ./...
```

### Device protocol (C)

```bash
make -C device-protocol-c test   # release build, hardened flags
```

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

In Demo Mode, integration suites in base-station-service and monitoring-service are disabled:
- `BaseStationIntegrationTest`
- `BatchMetricsIntegrationTest`
- `JwtFlowIntegrationTest`

Omit the property/variable to run full integration tests when Docker is available.

## CI/CD

All tests run automatically on every push via GitHub Actions:
- Parallel jobs per language/component (Java, Go, C incl. `TLS=1`, Python, frontend)
- Testcontainers for real database tests
- Frontend tests enforce failures (no silent passes)

See [.github/workflows/ci.yml](../.github/workflows/ci.yml) for pipeline configuration.
