# API Documentation

Complete API reference for the Base Station Operations & Maintenance Platform.

## Base URL

All API requests go through the API Gateway:
- **Development**: http://localhost:8080
- **Production**: Configure based on deployment

## Base Station Service API

### Endpoints

#### Create Base Station
```http
POST /api/v1/stations
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

**Response**: `201 Created` with station object

#### Get All Stations
```http
GET /api/v1/stations
```

**Response**: `200 OK` with array of stations

#### Get Station by ID
```http
GET /api/v1/stations/{id}
```

**Response**: `200 OK` with station object or `404 Not Found`

#### Update Station
```http
PUT /api/v1/stations/{id}
Content-Type: application/json

{
  "stationName": "BS-001-Updated",
  "status": "MAINTENANCE"
}
```

**Response**: `200 OK` with updated station object

#### Delete Station
```http
DELETE /api/v1/stations/{id}
```

**Response**: `204 No Content` or `404 Not Found`

#### Search by Location
```http
GET /api/v1/stations/search?latitude=40.7128&longitude=-74.0060&radius=10
```

**Query Parameters**:
- `latitude` (required): Latitude coordinate
- `longitude` (required): Longitude coordinate
- `radius` (optional): Search radius in kilometers (default: 10)

**Response**: `200 OK` with array of stations within radius

### Station Types
- `MACRO_CELL`
- `MICRO_CELL`
- `SMALL_CELL`
- `FEMTO_CELL`

### Station Status
- `ACTIVE`
- `INACTIVE`
- `MAINTENANCE`
- `OFFLINE`

## Monitoring Service API

### Endpoints

#### Record Metric
```http
POST /api/v1/metrics
Content-Type: application/json

{
  "stationId": 1,
  "stationName": "BS-001",
  "metricType": "CPU_USAGE",
  "value": 75.5,
  "unit": "%"
}
```

**Response**: `201 Created` with metric object

#### Get Metrics by Station
```http
GET /api/v1/metrics/station/{stationId}
```

**Response**: `200 OK` with array of metrics

#### Get Metrics by Time Range
```http
GET /api/v1/metrics?startTime=2024-01-01T00:00:00&endTime=2024-01-31T23:59:59
```

**Query Parameters**:
- `startTime` (optional): ISO 8601 format
- `endTime` (optional): ISO 8601 format

**Response**: `200 OK` with array of metrics

### Metric Types
- `CPU_USAGE`
- `MEMORY_USAGE`
- `NETWORK_TRAFFIC`
- `POWER_CONSUMPTION`
- `TEMPERATURE`
- `SIGNAL_STRENGTH`

## Notification Service API

### Endpoints

#### Create Notification
```http
POST /api/v1/notifications?stationId=1&message=High%20CPU%20usage&type=ALERT
```

**Query Parameters**:
- `stationId` (required): Station ID
- `message` (required): Notification message
- `type` (required): Notification type

**Response**: `201 Created` with notification object

#### Get All Notifications
```http
GET /api/v1/notifications
```

**Response**: `200 OK` with array of notifications

#### Get Notifications by Station
```http
GET /api/v1/notifications/station/{stationId}
```

**Response**: `200 OK` with array of notifications

#### Mark Notification as Read
```http
PUT /api/v1/notifications/{id}/read
```

**Response**: `200 OK` with updated notification object

### Notification Types
- `INFO`
- `WARNING`
- `ALERT`
- `ERROR`

### Notification Status
- `PENDING`
- `SENT`
- `READ`
- `ARCHIVED`

## Error Responses

All endpoints may return standard HTTP error codes:

- `400 Bad Request`: Invalid request data
- `404 Not Found`: Resource not found
- `500 Internal Server Error`: Server error

### Error Response Format
```json
{
  "timestamp": "2024-01-01T00:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/stations"
}
```

## Health Check Endpoints

All services expose health check endpoints:

- Base Station Service: http://localhost:8081/actuator/health
- Monitoring Service: http://localhost:8082/actuator/health
- Notification Service: http://localhost:8083/actuator/health
- API Gateway: http://localhost:8080/actuator/health

