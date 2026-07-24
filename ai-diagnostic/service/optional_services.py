"""
Optional downstream services and shared constants for the AI diagnostic service.

The HTTP API surface depends on ~14 optional AI subsystems (BI reports, vision,
predictive maintenance, self-healing, drone, ...) plus OpenTelemetry and Flask.
Each is imported defensively so the service still boots when a subsystem is
absent (the corresponding *_AVAILABLE flag goes False). Collected here so both
the orchestrator (diagnostic_service) and the HTTP adapter can share one copy
without a circular import.
"""

import logging

logger = logging.getLogger(__name__)

# OpenTelemetry tracing (optional)
try:
    from opentelemetry import trace
    from opentelemetry.sdk.trace import TracerProvider
    from opentelemetry.sdk.trace.export import BatchSpanProcessor
    from opentelemetry.exporter.zipkin.json import ZipkinExporter
    from opentelemetry.sdk.resources import Resource
    from opentelemetry.instrumentation.flask import FlaskInstrumentor
    OTEL_AVAILABLE = True
except ImportError:
    OTEL_AVAILABLE = False

try:
    from flask import Flask, request, jsonify, Response
    FLASK_AVAILABLE = True
except ImportError:
    FLASK_AVAILABLE = False

# BI Report generation (optional)
try:
    from bi_report_generator import BIReportGenerator
    BI_REPORT_AVAILABLE = True
except ImportError:
    BI_REPORT_AVAILABLE = False

# Computer Vision service (optional)
try:
    from vision_service import get_vision_service, VisionService
    VISION_AVAILABLE = True
except ImportError:
    VISION_AVAILABLE = False

# Alarm Correlation service (optional)
try:
    from alarm_correlation import get_alarm_correlation_service, Alarm
    ALARM_CORRELATION_AVAILABLE = True
except ImportError:
    ALARM_CORRELATION_AVAILABLE = False

# Predictive Maintenance service (optional)
try:
    from predictive_maintenance import get_predictive_maintenance_service, MetricDataPoint
    PREDICTIVE_MAINTENANCE_AVAILABLE = True
except ImportError:
    PREDICTIVE_MAINTENANCE_AVAILABLE = False

# Config Drift Detection service (optional)
try:
    from config_drift_detection import get_config_drift_service
    CONFIG_DRIFT_AVAILABLE = True
except ImportError:
    CONFIG_DRIFT_AVAILABLE = False

# Root Cause Analysis service (optional)
try:
    from root_cause_analysis import get_rca_service, parse_event_from_dict
    RCA_AVAILABLE = True
except ImportError:
    RCA_AVAILABLE = False

# Self-Healing service (optional)
try:
    from self_healing import get_self_healing_service, HealingAction, ActionType, RiskLevel
    SELF_HEALING_AVAILABLE = True
except ImportError:
    SELF_HEALING_AVAILABLE = False

# Healing integration (bridges AI solutions to self-healing)
try:
    from healing_integration import submit_healing_action
    HEALING_INTEGRATION_AVAILABLE = True
except ImportError:
    HEALING_INTEGRATION_AVAILABLE = False

# Internal authentication
try:
    from internal_auth import verify_internal_auth
    INTERNAL_AUTH_AVAILABLE = True
except ImportError:
    INTERNAL_AUTH_AVAILABLE = False

# Digital Twin service (optional)
try:
    from digital_twin import get_digital_twin_service, SimulationMode
    DIGITAL_TWIN_AVAILABLE = True
except ImportError:
    DIGITAL_TWIN_AVAILABLE = False

# Generative AI service (optional)
try:
    from generative_ai import get_generative_ai_service, ScenarioType, GenerationMethod
    GENERATIVE_AI_AVAILABLE = True
except ImportError:
    GENERATIVE_AI_AVAILABLE = False

# Computer Vision service (advanced) (optional)
try:
    from computer_vision import get_computer_vision_service, InspectionType
    COMPUTER_VISION_AVAILABLE = True
except ImportError:
    COMPUTER_VISION_AVAILABLE = False

# Drone Integration service (optional)
try:
    from drone_integration import get_drone_service, MissionType, GeoPoint
    DRONE_INTEGRATION_AVAILABLE = True
except ImportError:
    DRONE_INTEGRATION_AVAILABLE = False

# SON Scheduler (optional)
try:
    import sys
    import os
    # Add service directory to path if not already there
    service_dir = os.path.dirname(os.path.abspath(__file__))
    if service_dir not in sys.path:
        sys.path.insert(0, service_dir)
    from son_scheduler import get_son_scheduler
    SON_SCHEDULER_AVAILABLE = True
except ImportError:
    # Logger not yet initialized - will log at startup if needed
    SON_SCHEDULER_AVAILABLE = False

try:
    import websockets
    import asyncio
    WEBSOCKET_AVAILABLE = True
except ImportError:
    WEBSOCKET_AVAILABLE = False

# Error messages
LEARNING_ENGINE_NOT_AVAILABLE = "Learning engine not available"
ERR_PREDICTIVE_MAINTENANCE = "Predictive maintenance service not available"
ERR_REQUEST_BODY = "Request body is required"
ERR_CONFIG_DRIFT = "Config drift detection service not available"
ERR_RCA = "Root cause analysis service not available"
ERR_SELF_HEALING = "Self-healing service not available"
ERR_DIGITAL_TWIN = "Digital twin service not available"
ERR_TWIN_NOT_FOUND = "Twin not found"
ERR_GENERATIVE_AI = "Generative AI service not available"
ERR_COMPUTER_VISION = "Computer vision service not available"
ERR_DRONE = "Drone integration not available"

# Route constants
ROUTE_DIGITAL_TWIN = "/digital-twin/<station_id>"
