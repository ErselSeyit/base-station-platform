# Base Station Platform Documentation

**Version:** 2.2
**Last Updated:** 2026-02-20

## Quick Navigation

| Getting Started | Development | Operations |
|-----------------|-------------|------------|
| [Quick Start](QUICK_START.md) | [Architecture](ARCHITECTURE.md) | [Secret Management](SECRET_MANAGEMENT.md) |
| [Setup Guide](SETUP.md) | [API Reference](API.md) | [Kubernetes](../k8s/README.md) |
| | [Testing Guide](TESTING.md) | |

## Documentation Index

### Core Documentation

| Document | Description |
|----------|-------------|
| [QUICK_START.md](QUICK_START.md) | 5-minute getting started guide |
| [SETUP.md](SETUP.md) | Detailed setup and development guide |
| [ARCHITECTURE.md](ARCHITECTURE.md) | System architecture and design decisions |
| [API.md](API.md) | REST API reference (100 metric types, RBAC) |

### Operations

| Document | Description |
|----------|-------------|
| [SECRET_MANAGEMENT.md](SECRET_MANAGEMENT.md) | Secrets, rotation, best practices |
| [TESTING.md](TESTING.md) | Test coverage and running tests |
| [Kubernetes](../k8s/README.md) | Kubernetes deployment (Fabric8/JKube) |

### Sub-project Documentation

| Document | Description |
|----------|-------------|
| [AI Diagnostic](../ai-diagnostic/README.md) | AI/ML diagnostic service |
| [Testing Suite](../testing/README.md) | Live data simulation and integration testing |

---

## Platform Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     BASE STATION PLATFORM v2.2                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   Frontend (React 18)    →    API Gateway    →    Backend       │
│   Port: 3000                  Port: 8080          Services      │
│                                   │                              │
│                           ┌───────┼───────┐                     │
│                           ▼       ▼       ▼                     │
│                        Auth   Stations  Monitoring              │
│                       (8084)  (8081)    (8082)                  │
│                           │       │       │                     │
│                           ▼       ▼       ▼                     │
│                        PostgreSQL      MongoDB                  │
│                                                                  │
│   Edge:   Go Bridge + SNMP/MQTT Adapters + C Protocol Library   │
│   AI:     Python + Traffic Prediction + SON Functions           │
│   Metrics: 100 types including 5G NR, Power, Environmental      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## Key Features

### Security
- HttpOnly cookie JWT (XSS-safe)
- Internal HMAC authentication
- Role-based access control
- Actuator protection
- No hardcoded credentials

### Monitoring
- 100 metric types (5G NR, Power, Environmental, Transport)
- Real-time WebSocket streaming
- Prometheus + Grafana dashboards
- Distributed tracing (Zipkin)

### AI/ML Capabilities
- **Traffic Prediction**: LSTM-based multi-horizon forecasting
- **Anomaly Detection**: Isolation Forest with severity classification
- **SON Functions**: MLB, MRO, CCO, Energy Saving optimization
- **Predictive Maintenance**: Fan failure, battery degradation detection
- 9 diagnostic rules with confidence scoring

### Edge Integration
- Go bridge with SNMP/MQTT protocol adapters
- C library for MIPS devices (with new metric categories)
- Binary protocol with CRC-16 + TLS support (planned)
- Serial and TCP transport

## Getting Help

1. **Quick issues**: Check [QUICK_START.md](QUICK_START.md) troubleshooting section
2. **Architecture questions**: See [ARCHITECTURE.md](ARCHITECTURE.md)
3. **API usage**: See [API.md](API.md)

## Contributing

1. Read [ARCHITECTURE.md](ARCHITECTURE.md) for design decisions
2. Follow patterns in existing code
3. Write tests (see [TESTING.md](TESTING.md))
4. Update relevant documentation
