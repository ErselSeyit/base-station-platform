# AI Diagnostic Service for Base Stations

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         PHYSICAL LAYER                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   ┌─────────────────┐         ┌─────────────────┐                  │
│   │  MIPS Base      │         │  ARM Base       │                  │
│   │  Station        │         │  Station        │                  │
│   │                 │         │                 │                  │
│   │  ├─ Firmware    │         │  ├─ Firmware    │                  │
│   │  ├─ Radio HW    │         │  ├─ Radio HW    │                  │
│   │  └─ Sensors     │         │  └─ Sensors     │                  │
│   └────────┬────────┘         └────────┬────────┘                  │
│            │                           │                            │
└────────────┼───────────────────────────┼────────────────────────────┘
             │                           │
             │  USB / Ethernet / UART    │  MQTT / HTTP / Serial
             │                           │
┌────────────┼───────────────────────────┼────────────────────────────┐
│            │     PROTOCOL ADAPTERS     │                            │
├────────────┼───────────────────────────┼────────────────────────────┤
│            ▼                           ▼                            │
│   ┌────────────────────────────────────────────────────────────┐   │
│   │                    UNIVERSAL ADAPTER LAYER                  │   │
│   │                                                             │   │
│   │   ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐       │   │
│   │   │  TCP/IP │  │ Serial  │  │  MQTT   │  │  HTTP   │       │   │
│   │   │ :9090   │  │ /ttyUSB │  │ :1883   │  │ :9091   │       │   │
│   │   └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘       │   │
│   │        │            │            │            │             │   │
│   └────────┼────────────┼────────────┼────────────┼─────────────┘   │
│            │            │            │            │                  │
│            └────────────┴─────┬──────┴────────────┘                  │
│                               │                                      │
│                               ▼                                      │
│                    ┌─────────────────────┐                          │
│                    │   PROBLEM QUEUE     │                          │
│                    │   (Unified Format)  │                          │
│                    └──────────┬──────────┘                          │
│                               │                                      │
└───────────────────────────────┼──────────────────────────────────────┘
                                │
┌───────────────────────────────┼──────────────────────────────────────┐
│                               ▼         AI ENGINE                    │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   ┌────────────────────────────────────────────────────────────┐    │
│   │                    AI BACKEND SELECTOR                      │    │
│   │                                                             │    │
│   │   ┌──────────────────────┐  ┌──────────────────────┐       │    │
│   │   │ Rule-Based Expert    │  │ Local LLM (Ollama)   │       │    │
│   │   │ System               │  │                      │       │    │
│   │   │                      │  │ - Offline operation  │       │    │
│   │   │ - Offline operation  │  │ - Privacy focused    │       │    │
│   │   │ - Fast response      │  │ - Custom models      │       │    │
│   │   │ - Reliable           │  │ - LLaMA, Mistral     │       │    │
│   │   └──────────┬───────────┘  └──────────┬───────────┘       │    │
│   │              │                         │                    │    │
│   └──────────────┴─────────────────────────┴────────────────────┘    │
│                                │                                     │
│                                ▼                                     │
│                    ┌─────────────────────┐                          │
│                    │  DIAGNOSIS ENGINE   │                          │
│                    │                     │                          │
│                    │  1. Parse problem   │                          │
│                    │  2. Analyze metrics │                          │
│                    │  3. Check logs      │                          │
│                    │  4. Generate fix    │                          │
│                    │  5. Assess risk     │                          │
│                    └──────────┬──────────┘                          │
│                               │                                      │
└───────────────────────────────┼──────────────────────────────────────┘
                                │
                                ▼
                    ┌─────────────────────┐
                    │      SOLUTION       │
                    │                     │
                    │  - Action plan      │
                    │  - Shell commands   │
                    │  - Expected result  │
                    │  - Risk level       │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │ SEND BACK TO DEVICE │
                    │ (Same protocol)     │
                    └─────────────────────┘
```

## Problem Format (Universal)

```json
{
    "id": "PRB-1234567890-5678",
    "timestamp": "2026-01-27T15:30:00Z",
    "station_id": "MIPS-BS-001",
    "category": "hardware|network|software|power|security",
    "severity": "low|medium|high|critical",
    "code": "CPU_OVERHEAT",
    "message": "CPU temperature exceeded threshold",
    "metrics": {
        "cpu_temp": 82.5,
        "cpu_usage": 95.0,
        "memory_usage": 78.0,
        "power_consumption": 1850.0,
        "signal_strength": -85.0
    },
    "raw_logs": "kernel: CPU thermal throttling activated\n..."
}
```

## Solution Format

```json
{
    "problem_id": "PRB-1234567890-5678",
    "action": "Reduce thermal load and increase cooling",
    "commands": [
        "echo 1 > /sys/class/thermal/cooling_device0/cur_state",
        "systemctl restart fan-controller",
        "cpufreq-set -g powersave"
    ],
    "expected_outcome": "CPU temperature should drop below 70C within 5 minutes",
    "risk_level": "low",
    "confidence": 0.85,
    "reasoning": "High CPU temperature detected with high load"
}
```

## Quick Start

### 1. Start AI Diagnostic Service

```bash
# Basic (rule-based, TCP only)
python ai-diagnostic/service/diagnostic_service.py

# With all protocols
python ai-diagnostic/service/diagnostic_service.py \
    --tcp-port 9090 \
    --http-port 9091 \
    --serial /dev/ttyUSB0 \
    --mqtt-broker localhost

# With local Ollama LLM
python ai-diagnostic/service/diagnostic_service.py --backend ollama --ollama-model llama3.2
```

### 2. Start Virtual Base Station (for testing)

```bash
python ai-diagnostic/virtual-basestation/mips_simulator.py \
    --station-id MIPS-BS-001 \
    --host localhost \
    --port 9090
```

### 3. Watch the magic happen

The virtual base station will:
1. Generate realistic problems (CPU overheat, memory pressure, etc.)
2. Send them to the AI diagnostic service
3. Receive solutions
4. Apply fixes automatically

## Supported Protocols

| Protocol | Port | Use Case |
|----------|------|----------|
| TCP/IP | 9090 | Ethernet-connected devices |
| HTTP | 9091 | REST API integration |
| Serial | /dev/ttyUSB* | USB/UART connected devices |
| MQTT | 1883 | IoT devices, pub/sub |
| WebSocket | 9092 | Real-time web clients |

## Supported AI Backends

| Backend | Internet | Speed | Accuracy | Use Case |
|---------|----------|-------|----------|----------|
| Rule-Based | No | Fast | Good | Production, offline |
| Ollama | No | Medium | Good | Privacy, custom models |

## Supported Problem Types

- **Hardware**: CPU overheat, memory pressure, fan failure
- **Network**: Signal degradation, backhaul latency, packet loss
- **Software**: Process crash, config errors, memory leaks
- **Power**: Voltage fluctuation, high consumption, UPS issues
- **Security**: Auth failures, certificate expiry, intrusion

## Integration with Real Hardware

### For MIPS/ARM devices:

1. Implement the problem format JSON on your device
2. Send via any supported protocol
3. Receive and execute solution commands

### Example (C for embedded):

```c
// Send problem to diagnostic service
void send_problem(int sock, const char* code, float cpu_temp) {
    char json[1024];
    snprintf(json, sizeof(json),
        "{\"id\":\"PRB-%ld\",\"station_id\":\"MIPS-001\","
        "\"category\":\"hardware\",\"severity\":\"high\","
        "\"code\":\"%s\",\"message\":\"Problem detected\","
        "\"metrics\":{\"cpu_temp\":%.1f},\"raw_logs\":\"...\"}",
        time(NULL), code, cpu_temp);
    send(sock, json, strlen(json), 0);
}
```

## Module Structure

The larger services are decomposed into cohesive modules (models / pure
analytics / orchestration), each extracted under a characterisation-test suite
so behaviour is preserved. The original module re-exports every name, so imports
and the runtime entrypoint are unchanged.

```
ai-diagnostic/
├── README.md                           # This file
├── requirements.txt                    # Python dependencies
├── pyproject.toml                      # Project config (linting, testing)
├── requirements-dev.txt                # Dev/test dependencies
├── service/
│   │  # --- Diagnostic engine (decomposed from diagnostic_service.py) ---
│   ├── diagnostic_service.py           # Orchestration + main (re-exports below)
│   ├── models.py                       # Problem, Solution, LearnedPattern
│   ├── learning_engine.py              # Feedback-driven confidence learning
│   ├── backends.py                     # AIBackend, RuleBased, Ollama + RULES
│   ├── cloud_client.py                 # Post solutions back to the cloud
│   ├── transport_adapters.py           # ProtocolAdapter, TCP/Serial/MQTT
│   ├── http_adapter.py                 # Flask HTTP API (~50 routes, port 9091)
│   ├── optional_services.py            # Optional AI-subsystem imports + flags
│   │
│   │  # --- Predictive maintenance (decomposed) ---
│   ├── predictive_maintenance.py       # Service orchestration (delegates)
│   ├── maintenance_models.py           # MetricDataPoint, TrendAnalysis, ...
│   ├── maintenance_analytics.py        # Pure trend/regression, failure prob.
│   │
│   │  # --- Anomaly detection (decomposed) ---
│   ├── anomaly_detection.py            # AnomalyDetector ingestion
│   ├── isolation_forest.py             # Isolation Forest / Tree (pure numpy)
│   │
│   │  # --- Self-healing (decomposed) ---
│   ├── self_healing.py                 # Automated remediation workflow
│   ├── healing_models.py               # Action/status/risk enums + records
│   │
│   │  # --- SON functions (decomposed) ---
│   ├── son_functions.py                # SONEngine orchestration + API
│   ├── son_models.py                   # Enums + CellMetrics, SONRecommendation
│   ├── son_optimizers.py               # MLB, MRO, CCO, Energy Saving
│   ├── son_scheduler.py                # SON optimization scheduler
│   │
│   │  # --- Drone inspection (decomposed) ---
│   ├── drone_integration.py            # DroneController + service
│   ├── drone_models.py                 # Geo/mission/capture value types
│   ├── flight_path_planner.py          # Orbit/spiral/grid geometry (pure)
│   │
│   │  # --- Reporting ---
│   ├── bi_report_generator.py          # Business intelligence PDF reports
│   ├── ssv_status.py                   # Pure SSV pass/warn/fail acceptance
│   │
│   │  # --- Other AI subsystems ---
│   ├── alarm_correlation.py            # Multi-alarm correlation engine
│   ├── computer_vision.py              # Tower image analysis
│   ├── config_drift_detection.py       # Configuration drift detection
│   ├── digital_twin.py                 # Digital twin simulation
│   ├── generative_ai.py                # LLM-based diagnostics (Ollama)
│   ├── healing_integration.py          # Self-healing orchestration bridge
│   ├── root_cause_analysis.py          # Root cause analysis engine
│   ├── traffic_prediction.py           # LSTM traffic forecasting
│   ├── vision_service.py               # Computer vision service layer
│   │
│   │  # --- Infrastructure ---
│   ├── internal_auth.py                # HMAC authentication
│   ├── logging_config.py               # Structured logging
│   ├── metrics.py                      # Prometheus metrics
│   └── utils/                          # Shared utilities
│       ├── confidence.py               # Confidence scoring
│       ├── enums.py                    # Shared enums
│       ├── health.py                   # Health check helpers
│       ├── rng.py                      # Seeded random number generation
│       ├── serialization.py            # JSON serialization
│       ├── singleton.py                # Singleton pattern
│       ├── threshold_client.py         # Threshold config client
│       ├── thresholds.py               # Threshold evaluation
│       └── validation.py               # Input validation
├── tests/                              # Pytest suite (224 tests)
│   ├── conftest.py
│   ├── test_diagnostic_service_core.py # models/backends/learning/cloud client
│   ├── test_transport_adapters.py      # TCP/Serial via fake sockets
│   ├── test_http_adapter.py            # Flask routes via test client
│   ├── test_maintenance_analytics.py   # trend/regression/failure probability
│   ├── test_anomaly_detection.py       # Isolation Forest + detector
│   ├── test_self_healing.py            # remediation workflow
│   ├── test_son_functions.py           # MLB scenarios + engine
│   ├── test_drone_integration.py       # haversine + flight-path geometry
│   ├── test_ssv_status.py              # SSV acceptance thresholds
│   ├── test_alarm_x733.py              # X.733 alarm correlation
│   ├── test_confidence.py, test_health.py, test_rng.py
│   ├── test_serialization.py, test_threshold_client.py, test_validation.py
├── anomaly-simulator/                  # Anomaly injection tool
│   └── Dockerfile
└── virtual-basestation/                # Virtual device simulators
    ├── device_protocol.py              # Binary protocol implementation
    ├── mips_device.py                  # MIPS device emulator (single-file image)
    ├── mips_simulator.py               # Multi-device simulator
    └── Dockerfile
```
