# API Documentation

## Base URL

All API endpoints are accessed through NGINX Ingress, which routes to the API Gateway:
```
http://basestation.local:{ingress-port}/api/v1
```

To find your ingress port:
```bash
kubectl get svc -n ingress-nginx ingress-nginx-controller
```

## Rate Limiting

Gateway enforces per-service rate limits:

| Endpoint | Rate Limit | Burst |
|----------|-----------|-------|
| Auth (`/auth/**`) | 10 req/s | 20 |
| Stations (`/stations/**`) | 50 req/s | 100 |
| Monitoring (`/metrics/**`) | 100 req/s | 200 |
| Alerts (`/alerts/**`) | 50 req/s | 100 |
| SON (`/son/**`) | 50 req/s | 100 |
| Diagnostics (`/diagnostics/**`) | 30 req/s | 60 |
| Thresholds (`/thresholds/**`) | 30 req/s | 60 |
| Notifications (`/notifications/**`) | 30 req/s | 60 |
| Reports (`/reports/**`) | 5 req/s | 10 |

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

### Refresh
Rotate the refresh token and mint a new access token. Replaying an already-used
refresh token is detected and revokes the whole token family (RFC 6819).
```bash
POST /api/v1/auth/refresh
Content-Type: application/json

{ "refreshToken": "..." }

# Response (TokenResponse)
{
  "accessToken": "eyJ...",
  "refreshToken": "...",
  "expiresIn": 3600,
  "refreshExpiresIn": 604800,
  "username": "admin",
  "role": "ROLE_ADMIN"
}
```

### Validate
```bash
GET /api/v1/auth/validate
Authorization: Bearer <token>
# 200 if valid, 401 otherwise
```

### Logout
Revokes the caller's access token. The API Gateway keeps a Redis-backed
blocklist and rejects revoked tokens on subsequent requests (fail-open if Redis
is unavailable).
```bash
POST /api/v1/auth/logout
Authorization: Bearer <token>
```

### Revoke
Revoke a specific refresh token.
```bash
POST /api/v1/auth/revoke
Content-Type: application/json

{ "refreshToken": "..." }
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

#### Band dimension for radio metrics

The metric type is band-neutral. The NR frequency band a radio metric was
measured on is carried as a separate `band` dimension — matching the 3GPP
model, where a measurement (e.g. `DRB.UEThpDl`) is reported against a measured
object (an NRCellDU) that carries the frequency — rather than being baked into
the type name. So a 700 MHz downlink-throughput reading is:

```json
{
  "stationId": 1,
  "metricType": "DL_THROUGHPUT",
  "band": "N28",
  "value": 87.0,
  "unit": "Mbps"
}
```

`band` is one of `N28` (700 MHz), `N78` (3.5 GHz), or `NONE`. It defaults to
`NONE` when absent, which is correct for band-less metrics (CPU, temperature,
transport, power, environment). The radio metrics that take a real band are
`DL_THROUGHPUT`, `UL_THROUGHPUT`, `RSRP`, and `SINR`.

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

### Batch Record Metrics
Used by edge bridges to upload many metrics at once (roles: ADMIN, OPERATOR,
SERVICE). Returns per-entry results; partial failures yield HTTP 201 with an
`errors` list, all-failures yield HTTP 400.
```bash
POST /api/v1/metrics/batch
Content-Type: application/json

{
  "stationId": "1",
  "metrics": [
    {"type": "TEMPERATURE", "value": 55.2, "band": "N78", "timestamp": "2026-01-28T12:00:00"},
    {"type": "CPU_USAGE", "value": 25.5}
  ]
}

# Response (BatchRecordResponse)
{ "received": 2, "failed": 0, "status": "OK", "errors": null }
```

### Metric Catalog
```bash
# Every metric the platform records, with its unit and 3GPP TS 28.552 counter
GET /api/v1/metrics/catalog
Authorization: Bearer <token>
```

This is the authoritative list of metric types. Each entry is:

```json
{ "name": "DL_THROUGHPUT", "unit": "Mbps", "threeGppCounter": "DRB.UEThpDl" }
```

`threeGppCounter` is omitted for metrics outside the 3GPP RAN performance model
(facility telemetry, environment sensors).

## Metrics Validation

All metrics are validated before storage. The table below is representative;
`GET /api/v1/metrics/catalog` returns the full, current set of types and units.

| Metric Type | Valid Range | Unit |
|------------|-------------|------|
| CPU_USAGE | 0-100 | % |
| MEMORY_USAGE | 0-100 | % |
| TEMPERATURE | -50 to 150 | C |
| POWER_CONSUMPTION | 0-50,000 | W |
| SIGNAL_STRENGTH | -120 to -20 | dBm |
| DATA_THROUGHPUT | 0-100,000 | Mbps |
| CONNECTION_COUNT | 0-10,000 | count |
| DL_THROUGHPUT / UL_THROUGHPUT | 0-100,000 | Mbps |
| RSRP | -140 to -40 | dBm |
| SINR | -20 to 40 | dB |

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
GET /api/v1/notifications/station/{stationId}
GET /api/v1/notifications/page?page=0&size=20
GET /api/v1/notifications/counts
GET /api/v1/notifications/recent
```

### Delete Notification
```bash
DELETE /api/v1/notifications/{id}
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
  "timestamp": "2026-01-27T10:30:00Z",
  "station_id": "1",
  "category": "thermal",
  "severity": "HIGH",
  "code": "TEMPERATURE_HIGH",
  "message": "Temperature exceeds threshold",
  "metrics": {"temperature": 85.5, "threshold": 75.0}
}

# Response
{
  "problem_id": "alert-123",
  "action": "Increase cooling system capacity",
  "commands": ["increase_cooling", "check_hvac"],
  "expected_outcome": "Temperature should drop below threshold",
  "risk_level": "HIGH",
  "confidence": 0.92,
  "reasoning": "High temperature detected, cooling system adjustment needed"
}
```

## TMF Open APIs (tmf-api)

The `tmf-api` service (port 8086) implements TM Forum Open APIs and is **not**
routed through the gateway — reach it directly at `http://tmf-api:8086` (or
`http://localhost:8086`). OpenAPI/Swagger is at `/swagger-ui.html` (`/api-docs`
for the raw spec).

### TMF638 — Service Inventory
Base path: `/tmf-api/serviceInventoryManagement/v4`
```bash
GET    /service            # list (fields, offset, limit, state, category, serviceType, name, externalId, relatedPartyId) — X-Total-Count/X-Result-Count headers
GET    /service/{id}
POST   /service
PUT    /service/{id}
PATCH  /service/{id}
DELETE /service/{id}
POST   /service/{id}/activate | /deactivate | /terminate
GET    /service/external/{externalId} | /byResource/{resourceId} | /stats
POST   /hub ; DELETE /hub/{id}    # event subscriptions
```

### TMF639 — Resource Inventory
Base path: `/tmf-api/resourceInventoryManagement/v4`
```bash
GET    /resource           # list (fields, offset, limit, category, resourceType, operationalState, administrativeState, name, externalId)
GET    /resource/{id}
POST   /resource
PUT    /resource/{id}
PATCH  /resource/{id}
DELETE /resource/{id}
GET    /resource/external/{externalId} | /resource/{id}/children | /resource/stats
POST   /hub ; DELETE /hub/{id}
```

### TMF642 — Alarm Management
Base path: `/tmf-api/alarmManagement/v4`
```bash
GET    /alarm              # list (fields, offset, limit, state, perceivedSeverity, alarmType, sourceSystemId, affectedResourceId)
GET    /alarm/{id}
POST   /alarm
PATCH  /alarm/{id}
DELETE /alarm/{id}
POST   /alarm/{id}/ack | /unack | /clear | /comment
POST   /alarm/groupAck | /alarm/groupClear
GET    /alarm/stats | /alarm/active | /alarm/byResource/{resourceId} | /alarm/history/{resourceId}
POST   /hub ; DELETE /hub/{id}
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

Each service exposes health endpoints. Externally accessible via ingress:

```bash
# Via ingress
GET http://basestation.local:{ingress-port}/api/actuator/health
```

Internal health checks (within the cluster):
```bash
GET http://auth-service:8084/actuator/health
GET http://base-station-service:8081/actuator/health
GET http://monitoring-service:8082/actuator/health
GET http://notification-service:8083/actuator/health
GET http://tmf-api:8086/actuator/health
GET http://ai-diagnostic:9091/health
```
