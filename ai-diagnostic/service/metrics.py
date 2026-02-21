"""Prometheus metrics for AI diagnostic service."""

from prometheus_client import Counter, Histogram, Gauge, generate_latest, CONTENT_TYPE_LATEST
import time
from functools import wraps

# Request metrics
DIAGNOSIS_REQUESTS = Counter(
    'ai_diagnostic_requests_total',
    'Total diagnosis requests',
    ['category', 'severity']
)

DIAGNOSIS_DURATION = Histogram(
    'ai_diagnostic_duration_seconds',
    'Time to complete diagnosis',
    ['category'],
    buckets=[0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0]
)

HEALING_ACTIONS = Counter(
    'ai_diagnostic_healing_actions_total',
    'Total healing actions submitted',
    ['action_type', 'result']
)

HEALING_ACTIVE = Gauge(
    'ai_diagnostic_healing_active',
    'Currently active healing actions'
)

ANOMALY_DETECTIONS = Counter(
    'ai_diagnostic_anomaly_detections_total',
    'Total anomaly detections',
    ['metric_type', 'severity']
)

MODEL_CONFIDENCE = Histogram(
    'ai_diagnostic_model_confidence',
    'AI model confidence distribution',
    ['problem_code'],
    buckets=[0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0]
)

RCA_REQUESTS = Counter(
    'ai_diagnostic_rca_requests_total',
    'Root cause analysis requests',
    ['result']
)

VISION_INSPECTIONS = Counter(
    'ai_diagnostic_vision_inspections_total',
    'Computer vision inspections',
    ['result']
)

REQUEST_ERRORS = Counter(
    'ai_diagnostic_errors_total',
    'Total request errors',
    ['endpoint', 'error_type']
)


def register_metrics_endpoint(app):
    """Register the /metrics endpoint on the Flask app."""
    @app.route('/metrics')
    def metrics():
        from flask import Response
        return Response(generate_latest(), mimetype=CONTENT_TYPE_LATEST)


def track_diagnosis(category):
    """Decorator to track diagnosis request duration and count."""
    def decorator(func):
        @wraps(func)
        def wrapper(*args, **kwargs):
            start = time.monotonic()
            try:
                result = func(*args, **kwargs)
                duration = time.monotonic() - start
                DIAGNOSIS_DURATION.labels(category=category).observe(duration)
                # Try to extract severity from result
                severity = 'unknown'
                if isinstance(result, dict):
                    severity = result.get('severity', result.get('risk_level', 'unknown'))
                DIAGNOSIS_REQUESTS.labels(category=category, severity=severity).inc()
                return result
            except Exception as e:
                REQUEST_ERRORS.labels(endpoint=f'/diagnose/{category}', error_type=type(e).__name__).inc()
                raise
        return wrapper
    return decorator
