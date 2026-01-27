# API Documentation

## Base URL

All API endpoints are accessed through the API Gateway:
```
http://localhost:8080/api/v1
```

## Rate Limiting

Gateway enforces per-service rate limits:

| Endpoint | Rate Limit | Burst |
|----------|-----------|-------|
| Auth (`/auth/**`) | 10 req/s | 20 |
| Stations (`/stations/**`) | 50 req/s | 100 |
| Monitoring (`/metrics/**`) | 100 req/s | 200 |
| Notifications (`/notifications/**`) | 30 req/s | 60 |

## Authentication

### Login
```bash
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "your-password"
}

# Response
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "username": "admin",
  "role": "ROLE_ADMIN"
}
```

### Use Token
Include the token in the Authorization header:
```bash
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

## Stations API

### Create Station
```bash
POST /api/v1/stations
Authorization: Bearer <token>
Content-Type: application/json

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

### Get All Stations
```bash
GET /api/v1/stations
GET /api/v1/stations?status=ACTIVE
GET /api/v1/stations?type=MACRO_CELL
```

### Get Station by ID
```bash
GET /api/v1/stations/{id}
```

### Update Station
```bash
PUT /api/v1/stations/{id}
Authorization: Bearer <token>
Content-Type: application/json

{
  "stationName": "NYC-Manhattan-001-Updated",
  "status": "MAINTENANCE"
}
```

### Delete Station
```bash
DELETE /api/v1/stations/{id}
Authorization: Bearer <token>
```

### Geographic Search
```bash
# Find stations within radius
GET /api/v1/stations/search/nearby?lat=40.7128&lon=-74.0060&radiusKm=10

# Find stations in bounding box
GET /api/v1/stations/search/area?minLat=40.0&maxLat=41.0&minLon=-75.0&maxLon=-73.0
```

## Metrics API

### Record Metric
```bash
POST /api/v1/metrics
Authorization: Bearer <token>
Content-Type: application/json

{
  "stationId": 1,
  "stationName": "NYC-Manhattan-001",
  "metricType": "CPU_USAGE",
  "value": 65.5,
  "unit": "%"
}
```

### Query Metrics
```bash
# Get all metrics
GET /api/v1/metrics

# Get by station
GET /api/v1/metrics/station/{stationId}

# Get with time range
GET /api/v1/metrics?startTime=2026-01-20T00:00:00

# Batch latest metrics (prevents N+1)
POST /api/v1/metrics/batch/latest
Content-Type: application/json

{"stationIds": [1, 2, 3]}
```

## Metrics Validation

All metrics are validated before storage:

| Metric Type | Valid Range | Unit |
|------------|-------------|------|
| CPU_USAGE | 0-100 | % |
| MEMORY_USAGE | 0-100 | % |
| TEMPERATURE | -50 to 150 | C |
| POWER | 0-50,000 | W |
| SIGNAL_STRENGTH | -120 to -20 | dBm |
| THROUGHPUT | 0-100,000 | Mbps |
| CONNECTIONS | 0-10,000 | count |

Invalid values are rejected:
```json
// Response for CPU_USAGE = 150
{
  "message": "CPU_USAGE must be between 0 and 100%, received: 150.00"
}
```

## Notifications API

### Get Notifications
```bash
GET /api/v1/notifications
GET /api/v1/notifications?status=UNREAD
```

### Mark as Read
```bash
PUT /api/v1/notifications/{id}/read
Authorization: Bearer <token>
```

## AI Diagnostics API

The AI Diagnostic service exposes a direct endpoint (internal use):

```bash
POST http://ai-diagnostic:9091/diagnose
Content-Type: application/json
X-HMAC-Signature: <hmac-signature>

{
  "id": "alert-123",
  "code": "TEMPERATURE_HIGH",
  "station_id": 1,
  "station_name": "NYC-Manhattan-001",
  "metric_type": "TEMPERATURE",
  "current_value": 85.5,
  "threshold": 75.0,
  "description": "Temperature exceeds threshold"
}

# Response
{
  "id": "alert-123",
  "action": "Increase cooling system capacity",
  "confidence": 0.92,
  "risk_level": "HIGH",
  "additional_checks": ["Check HVAC system", "Verify airflow"]
}
```

## Error Responses

All errors follow a consistent format:

```json
{
  "timestamp": "2026-01-27T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed: username must not be blank",
  "path": "/api/v1/auth/login"
}
```

## Health Endpoints

Each service exposes health endpoints:

```bash
# API Gateway
GET http://localhost:8080/actuator/health

# Individual services (internal)
GET http://auth-service:8084/actuator/health
GET http://base-station-service:8081/actuator/health
GET http://monitoring-service:8082/actuator/health
GET http://ai-diagnostic:9091/health
```
