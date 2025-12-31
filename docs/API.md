# API Documentation

## Interactive Documentation

Swagger UI available for each service:
- **Base Station**: http://localhost:8084/swagger-ui.html
- **Monitoring**: http://localhost:8085/swagger-ui.html

OpenAPI specs:
- http://localhost:8084/v3/api-docs
- http://localhost:8085/v3/api-docs

## Rate Limiting

Gateway enforces per-service rate limits:
- Auth endpoints: 10 req/s
- Base Station: 50 req/s
- Monitoring: 100 req/s
- Notifications: 30 req/s

## API Examples

### Authentication
```bash
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "password"
}
```

### Create Station
```bash
POST http://localhost:8080/api/v1/stations
Content-Type: application/json
Authorization: Bearer <token>

{
  "stationName": "NYC-Manhattan-001",
  "location": "Manhattan, NY",
  "latitude": 40.7580,
  "longitude": -73.9855,
  "stationType": "MACRO_CELL",
  "status": "ACTIVE",
  "powerConsumption": 3500
}
```

### Record Metric
```bash
POST http://localhost:8080/api/v1/metrics
Content-Type: application/json
Authorization: Bearer <token>

{
  "stationId": 1,
  "stationName": "NYC-Manhattan-001",
  "metricType": "CPU_USAGE",
  "value": 65.5,
  "unit": "%"
}
```

**Note**: Unit is optional and will be auto-set if not provided. See [METRIC_RANGES.md](../monitoring-service/docs/METRIC_RANGES.md) for validation rules.

Invalid values are rejected:
```json
// ❌ This will be rejected (CPU > 100%)
{"stationId": 1, "metricType": "CPU_USAGE", "value": 150}

// Response:
{"message": "CPU_USAGE must be between 0 and 100%, received: 150.00"}
```

### Query Metrics
```bash
# Get last 7 days
GET http://localhost:8080/api/v1/metrics?startTime=2025-12-22T00:00:00

# Get by station
GET http://localhost:8080/api/v1/metrics/station/1

# Get latest for multiple stations (batch - prevents N+1)
POST http://localhost:8080/api/v1/metrics/batch/latest
Content-Type: application/json

{"stationIds": [1, 2, 3]}
```

### Geographic Search
```bash
# Find stations within 10km radius
GET http://localhost:8080/api/v1/stations/search?latitude=40.7128&longitude=-74.0060&radius=10
```

## Metrics Validation

All metrics are validated before storage:
- **CPU/Memory/Uptime**: 0-100%
- **Temperature**: -50°C to 150°C (typical: 20-75°C)
- **Power**: 0-50,000W (typical: 500-8,000W)
- **Signal Strength**: -120 to -20 dBm
- **Throughput**: 0-100,000 Mbps (typical: 100-5,000 Mbps)
- **Connections**: 0-10,000

See [METRIC_RANGES.md](../monitoring-service/docs/METRIC_RANGES.md) for complete validation rules.
