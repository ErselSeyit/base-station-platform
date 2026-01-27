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

## Files

```
ai-diagnostic/
├── README.md                           # This file
├── requirements.txt                    # Python dependencies
├── service/
│   └── diagnostic_service.py          # Main AI service
└── virtual-basestation/
    └── mips_simulator.py               # Virtual MIPS simulator
```
