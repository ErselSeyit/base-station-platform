"""
HTTP/REST adapter for the AI diagnostic service.

Exposes the diagnostic engine and every optional AI subsystem (BI reports,
vision, predictive maintenance, self-healing, digital twin, drone, ...) over a
Flask API with HMAC / internal-service authentication and optional OpenTelemetry
tracing. Extracted from diagnostic_service.py; the optional subsystems and shared
constants come from service.optional_services, keeping this module and the
orchestrator free of a circular import.
"""

import hashlib
import hmac
import logging
import os
import threading
import time
from dataclasses import asdict
from datetime import datetime
from functools import wraps
from typing import Callable, List, Optional

from service.models import Problem, Solution
from service.learning_engine import LearningEngine
from service.transport_adapters import ProtocolAdapter
from service.metrics import register_metrics_endpoint
from service.optional_services import *  # noqa: F401,F403

logger = logging.getLogger(__name__)


class HTTPAdapter(ProtocolAdapter):
    """HTTP/REST API adapter with HMAC authentication and OpenTelemetry tracing"""

    def __init__(self, on_problem: Optional[Callable[[Problem], Solution]] = None, host: str = "0.0.0.0", port: int = 9091):
        super().__init__(on_problem)
        self.host = host
        self.port = port
        self.app: Optional["Flask"] = None
        self.secret = os.environ.get("DIAGNOSTIC_SECRET", "")
        self.tracer = None
        # References to diagnostic logs and learning engine (set by DiagnosticService)
        self.problem_log: List[Problem] = []
        self.solution_log: List[Solution] = []
        self.learning_engine: Optional[LearningEngine] = None
        self.diagnostic_service: Optional["DiagnosticService"] = None  # Reference to parent service
        require_auth = os.environ.get("DIAGNOSTIC_REQUIRE_AUTH", "true").lower() == "true"
        if not self.secret:
            if require_auth:
                raise ValueError("DIAGNOSTIC_SECRET is required - set via environment variable or set DIAGNOSTIC_REQUIRE_AUTH=false for development")
            logger.warning("DIAGNOSTIC_SECRET not set - authentication disabled (development mode only)")

    def _setup_tracing(self):
        """Initialize OpenTelemetry tracing"""
        if not OTEL_AVAILABLE:
            logger.info("OpenTelemetry not available - tracing disabled")
            return

        zipkin_endpoint = os.environ.get("ZIPKIN_ENDPOINT", "")
        if not zipkin_endpoint:
            logger.info("ZIPKIN_ENDPOINT not set - tracing disabled")
            return

        try:
            resource = Resource.create({"service.name": "ai-diagnostic"})
            provider = TracerProvider(resource=resource)
            exporter = ZipkinExporter(endpoint=zipkin_endpoint)
            provider.add_span_processor(BatchSpanProcessor(exporter))
            trace.set_tracer_provider(provider)
            self.tracer = trace.get_tracer(__name__)

            # Instrument Flask app
            if self.app:
                FlaskInstrumentor().instrument_app(self.app)

            logger.info(f"OpenTelemetry tracing enabled, exporting to {zipkin_endpoint}")
        except Exception as e:
            logger.warning(f"Failed to initialize tracing: {e}")

    def _verify_hmac(self, body: bytes, signature: str) -> bool:
        """Verify HMAC signature of request body"""
        if not self.secret:
            return True  # Auth disabled if no secret configured

        expected = hmac.new(
            self.secret.encode(),
            body,
            hashlib.sha256
        ).hexdigest()

        return hmac.compare_digest(expected, signature)

    def _require_auth(self, f):
        """Decorator to require HMAC or internal service authentication"""
        @wraps(f)
        def decorated(*args, **kwargs):
            if not self.secret:
                return f(*args, **kwargs)

            # Try HMAC signature first
            signature = request.headers.get("X-HMAC-Signature", "")
            if signature:
                if self._verify_hmac(request.get_data(), signature):
                    return f(*args, **kwargs)
                logger.warning("Invalid HMAC signature from %s", request.remote_addr)
                return jsonify({"error": "Invalid authentication"}), 403

            # Try internal service auth (via internal_auth module)
            internal_auth = request.headers.get("X-Internal-Auth", "")
            if internal_auth and INTERNAL_AUTH_AVAILABLE:
                if verify_internal_auth(internal_auth, self.secret):
                    return f(*args, **kwargs)
                logger.warning("Invalid internal auth from %s", request.remote_addr)
                return jsonify({"error": "Invalid authentication"}), 403

            logger.warning("Missing authentication header")
            return jsonify({"error": "Missing authentication"}), 401

        return decorated

    def _handle_diagnose(self):
        """Handle POST /diagnose request with optional auto-healing."""
        if not self.on_problem:
            return jsonify({"error": "Diagnostic handler not configured"}), 503

        problem_data = request.json
        problem = Problem(**problem_data, source_protocol="http")
        solution = self.on_problem(problem)
        response = asdict(solution)

        # Auto-healing integration via healing_integration module
        auto_heal = request.args.get('auto_heal', 'true').lower() == 'true'
        if auto_heal and HEALING_INTEGRATION_AVAILABLE:
            healing_result = submit_healing_action(problem, solution)
            if healing_result:
                response['healing'] = healing_result

        return jsonify(response)

    def _handle_health(self):
        """Handle GET /health request."""
        return jsonify({
            "status": "ok",
            "authenticated": bool(self.secret),
            "tracing": OTEL_AVAILABLE and self.tracer is not None
        })

    def _handle_bi_report(self):
        """Handle GET /reports/bi request."""
        if not BI_REPORT_AVAILABLE:
            return jsonify({"error": "BI report generation not available"}), 503

        api_url = os.environ.get("API_GATEWAY_URL", "http://localhost:8080")
        generator = BIReportGenerator(api_url)
        pdf_bytes = generator.generate_report_bytes()

        if pdf_bytes is None:
            return jsonify({"error": "Failed to generate report"}), 500

        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = f"bi-report-{timestamp}.pdf"

        return Response(
            pdf_bytes,
            mimetype='application/pdf',
            headers={
                'Content-Disposition': f'attachment; filename="{filename}"',
                'Content-Length': str(len(pdf_bytes))
            }
        )

    def _build_diagnostic_entry(self, index: int, problem: Problem) -> dict:
        """Build a diagnostic entry with optional solution."""
        entry = {
            "id": problem.id,
            "timestamp": problem.timestamp,
            "station_id": problem.station_id,
            "category": problem.category,
            "severity": problem.severity,
            "code": problem.code,
            "message": problem.message,
            "source_protocol": problem.source_protocol,
            "solution": None
        }
        if index < len(self.solution_log):
            sol = self.solution_log[index]
            entry["solution"] = {
                "action": sol.action,
                "commands": sol.commands,
                "expected_outcome": sol.expected_outcome,
                "risk_level": sol.risk_level,
                "confidence": sol.confidence,
                "reasoning": sol.reasoning
            }
        return entry

    def _handle_diagnostics_log(self):
        """Handle GET /reports/diagnostics request."""
        diagnostics = [
            self._build_diagnostic_entry(i, problem)
            for i, problem in enumerate(self.problem_log)
        ]
        return jsonify({"total": len(diagnostics), "diagnostics": diagnostics})

    def _handle_feedback(self):
        """Handle POST /learning/feedback request."""
        if not self.diagnostic_service:
            return jsonify({"error": LEARNING_ENGINE_NOT_AVAILABLE}), 503

        data = request.json
        problem_code = data.get('problem_code')
        if not problem_code:
            return jsonify({"error": "problem_code is required"}), 400

        pattern = self.diagnostic_service.record_feedback(
            problem_code,
            data.get('category', 'unknown'),
            data.get('was_effective', False),
            data.get('action', '')
        )

        return jsonify({
            "problem_code": pattern.problem_code,
            "success_rate": pattern.success_rate(),
            "resolved_count": pattern.resolved_count,
            "failed_count": pattern.failed_count,
            "adjusted_confidence": pattern.adjusted_confidence
        })

    @staticmethod
    def _serialize_pattern(pattern) -> dict:
        """Serialize a learning pattern to dict."""
        return {
            "problem_code": pattern.problem_code,
            "category": pattern.category,
            "resolved_count": pattern.resolved_count,
            "failed_count": pattern.failed_count,
            "success_rate": pattern.success_rate(),
            "adjusted_confidence": pattern.adjusted_confidence,
            "successful_actions": pattern.successful_actions,
            "failed_actions": pattern.failed_actions
        }

    def _handle_learning_stats(self):
        """Handle GET /learning/stats request."""
        if not self.learning_engine:
            return jsonify({"error": LEARNING_ENGINE_NOT_AVAILABLE}), 503
        return jsonify(self.learning_engine.get_stats())

    def _handle_patterns(self):
        """Handle GET /learning/patterns request."""
        if not self.learning_engine:
            return jsonify({"error": LEARNING_ENGINE_NOT_AVAILABLE}), 503

        patterns = self.learning_engine.get_all_patterns()
        return jsonify({
            "total": len(patterns),
            "patterns": [self._serialize_pattern(p) for p in patterns]
        })

    def _handle_pattern(self, problem_code: str):
        """Handle GET /learning/patterns/<problem_code> request."""
        if not self.learning_engine:
            return jsonify({"error": LEARNING_ENGINE_NOT_AVAILABLE}), 503

        pattern = self.learning_engine.get_pattern(problem_code)
        if not pattern:
            return jsonify({"error": "Pattern not found"}), 404

        return jsonify(self._serialize_pattern(pattern))

    # =========================================================================
    # Computer Vision Endpoints
    # =========================================================================

    def _handle_vision_analyze_led(self):
        """Handle POST /vision/analyze-led request.

        Accepts either:
        - JSON with base64_image field
        - multipart/form-data with image file
        """
        if not VISION_AVAILABLE:
            return jsonify({"error": "Vision service not available - install opencv-python-headless"}), 503

        vision_service = get_vision_service()

        try:
            if request.content_type and 'multipart/form-data' in request.content_type:
                # Handle file upload
                if 'image' not in request.files:
                    return jsonify({"error": "No image file provided"}), 400
                file = request.files['image']
                image_data = file.read()
                station_id = request.form.get('station_id', 'unknown')
                expected_leds = int(request.form.get('expected_leds', 0))
            else:
                # Handle JSON with base64
                data = request.json
                if not data or 'base64_image' not in data:
                    return jsonify({"error": "base64_image field is required"}), 400
                station_id = data.get('station_id', 'unknown')
                expected_leds = data.get('expected_leds', 0)
                result = vision_service.analyze_from_base64(
                    data['base64_image'], station_id, expected_leds
                )
                return jsonify(vision_service.to_dict(result))

            result = vision_service.analyze_led_panel(image_data, station_id, expected_leds)
            return jsonify(vision_service.to_dict(result))

        except ValueError as e:
            return jsonify({"error": str(e)}), 400
        except Exception as e:
            logger.error(f"Vision analysis error: {e}")
            return jsonify({"error": f"Analysis failed: {str(e)}"}), 500

    # =========================================================================
    # Alarm Correlation Endpoints
    # =========================================================================

    def _handle_alarms_correlate(self):
        """Handle POST /alarms/correlate request.

        Body: {"alarms": [{"alarm_id": "...", "station_id": "...", ...}, ...]}
        """
        if not ALARM_CORRELATION_AVAILABLE:
            return jsonify({"error": "Alarm correlation service not available - install scikit-learn"}), 503

        correlation_service = get_alarm_correlation_service()

        try:
            data = request.json
            if not data or 'alarms' not in data:
                return jsonify({"error": "alarms field is required"}), 400

            # Convert JSON to Alarm objects
            from alarm_correlation import AlarmSeverity
            alarms = []
            for alarm_data in data['alarms']:
                # Map severity string to enum
                severity_str = alarm_data['severity'].upper()
                try:
                    severity = AlarmSeverity[severity_str]
                except KeyError:
                    severity = AlarmSeverity.WARNING

                alarm = Alarm(
                    alarm_id=alarm_data['alarm_id'],
                    station_id=alarm_data['station_id'],
                    timestamp=datetime.fromisoformat(alarm_data['timestamp']),
                    alarm_type=alarm_data['alarm_type'],
                    severity=severity,
                    message=alarm_data.get('message', ''),
                    metric_type=alarm_data.get('metric_type'),
                    metric_value=alarm_data.get('metric_value')
                )
                alarms.append(alarm)

            # Run correlation analysis
            result = correlation_service.correlate_alarms(alarms)

            return jsonify(result.to_dict())

        except KeyError as e:
            return jsonify({"error": f"Missing required field: {e}"}), 400
        except Exception as e:
            logger.error(f"Alarm correlation error: {e}")
            return jsonify({"error": f"Correlation failed: {str(e)}"}), 500

    # =========================================================================
    # Predictive Maintenance Endpoints
    # =========================================================================

    def _handle_maintenance_health(self, station_id: str):
        """Handle GET /maintenance/<station_id>/health request."""
        if not PREDICTIVE_MAINTENANCE_AVAILABLE:
            return jsonify({"error": f"{ERR_PREDICTIVE_MAINTENANCE} - install scikit-learn"}), 503

        maintenance_service = get_predictive_maintenance_service()

        try:
            # Get metric data from query params or use defaults
            include_recommendations = request.args.get('include_recommendations', 'true').lower() == 'true'

            report = maintenance_service.get_station_health_report(station_id)
            result = report  # Already a dict

            if not include_recommendations:
                result.pop('recommendations', None)

            return jsonify(result)

        except Exception as e:
            logger.error(f"Maintenance health report error: {e}")
            return jsonify({"error": f"Health report failed: {str(e)}"}), 500

    def _handle_maintenance_analyze(self):
        """Handle POST /maintenance/analyze request.

        Body: {
            "station_id": "BS-001",
            "metrics": [
                {"metric_type": "FAN_SPEED", "value": 2500, "timestamp": "..."},
                ...
            ]
        }
        """
        if not PREDICTIVE_MAINTENANCE_AVAILABLE:
            return jsonify({"error": ERR_PREDICTIVE_MAINTENANCE}), 503

        maintenance_service = get_predictive_maintenance_service()

        try:
            data = request.json
            if not data:
                return jsonify({"error": ERR_REQUEST_BODY}), 400

            station_id = data.get('station_id', 'unknown')
            metrics_data = data.get('metrics', [])

            # Convert to MetricDataPoint objects and add to service
            for m in metrics_data:
                point = MetricDataPoint(
                    timestamp=datetime.fromisoformat(m['timestamp']),
                    metric_type=m['metric_type'],
                    value=m['value'],
                    station_id=station_id
                )
                maintenance_service.add_metric(point)

            # Generate health report (already returns dict)
            report = maintenance_service.get_station_health_report(station_id)
            return jsonify(report)

        except KeyError as e:
            return jsonify({"error": f"Missing required field: {e}"}), 400
        except Exception as e:
            logger.error(f"Maintenance analysis error: {e}")
            return jsonify({"error": f"Analysis failed: {str(e)}"}), 500

    def _handle_battery_health(self, station_id: str):
        """Handle GET /maintenance/<station_id>/battery request.

        Analyzes battery health including SOC, DOD, temperature, and cycle count.
        """
        if not PREDICTIVE_MAINTENANCE_AVAILABLE:
            return jsonify({"error": ERR_PREDICTIVE_MAINTENANCE}), 503

        maintenance_service = get_predictive_maintenance_service()

        try:
            from datetime import timedelta
            window_hours = request.args.get('window_hours', 24, type=int)
            analysis_window = timedelta(hours=window_hours)

            prediction = maintenance_service.analyze_battery_health(
                station_id, analysis_window
            )

            if prediction:
                return jsonify({
                    "station_id": station_id,
                    "component": "battery_system",
                    "analysis_window_hours": window_hours,
                    "prediction": prediction.to_dict()
                })
            else:
                return jsonify({
                    "station_id": station_id,
                    "component": "battery_system",
                    "analysis_window_hours": window_hours,
                    "status": "healthy",
                    "message": "No battery issues detected or insufficient data"
                })

        except Exception as e:
            logger.error(f"Battery health analysis error: {e}")
            return jsonify({"error": f"Battery analysis failed: {str(e)}"}), 500

    def _handle_fiber_health(self, station_id: str):
        """Handle GET /maintenance/<station_id>/fiber request.

        Analyzes fiber transport health including RX/TX power, BER, and OSNR.
        """
        if not PREDICTIVE_MAINTENANCE_AVAILABLE:
            return jsonify({"error": ERR_PREDICTIVE_MAINTENANCE}), 503

        maintenance_service = get_predictive_maintenance_service()

        try:
            from datetime import timedelta
            window_hours = request.args.get('window_hours', 24, type=int)
            analysis_window = timedelta(hours=window_hours)

            prediction = maintenance_service.analyze_fiber_transport(
                station_id, analysis_window
            )

            if prediction:
                return jsonify({
                    "station_id": station_id,
                    "component": "fiber_transport",
                    "analysis_window_hours": window_hours,
                    "prediction": prediction.to_dict()
                })
            else:
                return jsonify({
                    "station_id": station_id,
                    "component": "fiber_transport",
                    "analysis_window_hours": window_hours,
                    "status": "healthy",
                    "message": "No fiber issues detected or insufficient data"
                })

        except Exception as e:
            logger.error(f"Fiber health analysis error: {e}")
            return jsonify({"error": f"Fiber analysis failed: {str(e)}"}), 500

    # =========================================================================
    # Configuration Drift Detection Endpoints
    # =========================================================================

    def _handle_config_drift_detect(self):
        """Handle POST /config/drift request.

        Body: {
            "station_id": "BS-001",
            "current_config": {"param1": "value1", ...},
            "baseline_config": {"param1": "baseline1", ...}  # optional
        }
        """
        if not CONFIG_DRIFT_AVAILABLE:
            return jsonify({"error": ERR_CONFIG_DRIFT}), 503

        drift_service = get_config_drift_service()

        try:
            data = request.json
            if not data:
                return jsonify({"error": ERR_REQUEST_BODY}), 400

            station_id = data.get('station_id', 'unknown')
            current_config = data.get('current_config')

            if not current_config:
                return jsonify({"error": "current_config is required"}), 400

            # Set baseline if provided
            baseline_config = data.get('baseline_config')
            if baseline_config:
                drift_service.set_baseline(station_id, baseline_config)

            # Detect drift
            report = drift_service.detect_drift(station_id, current_config)

            if report is None:
                return jsonify({
                    "error": "No baseline configuration found for station",
                    "hint": "Provide baseline_config in the request or call /config/baseline first"
                }), 400

            return jsonify(report.to_dict())

        except Exception as e:
            logger.error(f"Config drift detection error: {e}")
            return jsonify({"error": f"Drift detection failed: {str(e)}"}), 500

    def _handle_config_baseline_set(self):
        """Handle POST /config/baseline request.

        Body: {
            "station_id": "BS-001",
            "config": {"param1": "value1", ...}
        }
        """
        if not CONFIG_DRIFT_AVAILABLE:
            return jsonify({"error": ERR_CONFIG_DRIFT}), 503

        drift_service = get_config_drift_service()

        try:
            data = request.json
            if not data:
                return jsonify({"error": ERR_REQUEST_BODY}), 400

            station_id = data.get('station_id')
            config = data.get('config')

            if not station_id:
                return jsonify({"error": "station_id is required"}), 400
            if not config:
                return jsonify({"error": "config is required"}), 400

            drift_service.set_baseline(station_id, config)

            return jsonify({
                "status": "ok",
                "station_id": station_id,
                "parameters_count": len(config),
                "message": "Baseline configuration saved"
            })

        except Exception as e:
            logger.error(f"Config baseline set error: {e}")
            return jsonify({"error": f"Failed to set baseline: {str(e)}"}), 500

    def _handle_config_baseline_get(self, station_id: str):
        """Handle GET /config/baseline/<station_id> request."""
        if not CONFIG_DRIFT_AVAILABLE:
            return jsonify({"error": ERR_CONFIG_DRIFT}), 503

        drift_service = get_config_drift_service()

        baseline = drift_service.export_baseline(station_id)
        if baseline is None:
            return jsonify({"error": "No baseline found for station"}), 404

        return jsonify(baseline)

    def _handle_rca_analyze(self):
        """Handle POST /rca/analyze request - analyze events to find root cause."""
        if not RCA_AVAILABLE:
            return jsonify({"error": ERR_RCA}), 503

        data = request.get_json()
        if not data or 'events' not in data:
            return jsonify({"error": "Missing 'events' in request body"}), 400

        events_data = data['events']
        if not events_data:
            return jsonify({"error": "Events list cannot be empty"}), 400

        rca_service = get_rca_service()

        # Parse events
        events = [parse_event_from_dict(e) for e in events_data]

        # Analyze
        result = rca_service.analyze(events)

        if result is None:
            return jsonify({"error": "Analysis failed"}), 500

        return jsonify(result.to_dict())

    def _handle_rca_stats(self):
        """Handle GET /rca/stats request - get RCA statistics."""
        if not RCA_AVAILABLE:
            return jsonify({"error": ERR_RCA}), 503

        rca_service = get_rca_service()
        stats = rca_service.get_statistics()

        return jsonify(stats)

    def _handle_rca_feedback(self):
        """Handle POST /rca/feedback request - learn from operator feedback."""
        if not RCA_AVAILABLE:
            return jsonify({"error": ERR_RCA}), 503

        data = request.get_json()
        if not data:
            return jsonify({"error": "Missing request body"}), 400

        required_fields = ['analysis_id', 'actual_root_cause', 'was_correct']
        for field in required_fields:
            if field not in data:
                return jsonify({"error": f"Missing required field: {field}"}), 400

        rca_service = get_rca_service()
        rca_service.learn_from_feedback(
            analysis_id=data['analysis_id'],
            actual_root_cause=data['actual_root_cause'],
            was_correct=data['was_correct'],
            corrective_action=data.get('corrective_action')
        )

        return jsonify({"status": "feedback recorded"})

    # =========================================================================
    # Self-Healing Endpoints
    # =========================================================================

    def _handle_healing_submit(self):
        """Handle POST /healing/actions request - submit a healing action."""
        if not SELF_HEALING_AVAILABLE:
            return jsonify({"error": ERR_SELF_HEALING}), 503

        data = request.get_json()
        if not data:
            return jsonify({"error": "Missing request body"}), 400

        required_fields = ['station_id', 'action_type', 'description']
        for field in required_fields:
            if field not in data:
                return jsonify({"error": f"Missing required field: {field}"}), 400

        try:
            healing_service = get_self_healing_service()

            action = HealingAction(
                id=f"heal-manual-{data['station_id']}-{int(time.time())}",
                station_id=data['station_id'],
                action_type=ActionType[data['action_type'].upper()],
                parameters=data.get('parameters', {}),
                description=data['description'],
                risk_level=RiskLevel[data.get('risk_level', 'MEDIUM').upper()],
                source='manual',
                source_id='user-submitted',
                auto_execute=data.get('auto_execute', False),
                timeout_seconds=data.get('timeout_seconds', 300),
                rollback_action=data.get('rollback_action')
            )

            result = healing_service.submit_action(action)
            return jsonify(result)

        except KeyError as e:
            return jsonify({"error": f"Invalid enum value: {e}"}), 400
        except Exception as e:
            logger.error(f"Healing action submit error: {e}")
            return jsonify({"error": str(e)}), 500

    def _handle_healing_from_son(self):
        """Handle POST /healing/from-son request - create action from SON recommendation."""
        if not SELF_HEALING_AVAILABLE:
            return jsonify({"error": ERR_SELF_HEALING}), 503

        data = request.get_json()
        if not data:
            return jsonify({"error": "Missing SON recommendation data"}), 400

        try:
            healing_service = get_self_healing_service()
            action = healing_service.create_action_from_son(data)
            result = healing_service.submit_action(action)
            return jsonify({
                "action": action.to_dict(),
                "submission": result
            })

        except Exception as e:
            logger.error(f"Healing from SON error: {e}")
            return jsonify({"error": str(e)}), 500

    def _handle_healing_from_rca(self):
        """Handle POST /healing/from-rca request - create action from RCA result."""
        if not SELF_HEALING_AVAILABLE:
            return jsonify({"error": ERR_SELF_HEALING}), 503

        data = request.get_json()
        if not data:
            return jsonify({"error": "Missing RCA result data"}), 400

        station_id = data.get('station_id')
        if not station_id:
            return jsonify({"error": "Missing station_id"}), 400

        try:
            healing_service = get_self_healing_service()
            action = healing_service.create_action_from_rca(data, station_id)

            if not action:
                return jsonify({"message": "No actionable remediation for this root cause"}), 200

            result = healing_service.submit_action(action)
            return jsonify({
                "action": action.to_dict(),
                "submission": result
            })

        except Exception as e:
            logger.error(f"Healing from RCA error: {e}")
            return jsonify({"error": str(e)}), 500

    def _handle_healing_approve(self, action_id: str):
        """Handle POST /healing/actions/{id}/approve request."""
        if not SELF_HEALING_AVAILABLE:
            return jsonify({"error": ERR_SELF_HEALING}), 503

        data = request.get_json() or {}
        approved_by = data.get('approved_by', 'unknown')

        healing_service = get_self_healing_service()
        result = healing_service.approve_action(action_id, approved_by)

        if result:
            return jsonify(result)
        return jsonify({"error": "Action not found"}), 404

    def _handle_healing_cancel(self, action_id: str):
        """Handle POST /healing/actions/{id}/cancel request."""
        if not SELF_HEALING_AVAILABLE:
            return jsonify({"error": ERR_SELF_HEALING}), 503

        data = request.get_json() or {}
        reason = data.get('reason', 'Cancelled by user')

        healing_service = get_self_healing_service()
        result = healing_service.cancel_action(action_id, reason)

        if result:
            return jsonify(result)
        return jsonify({"error": "Action not found"}), 404

    def _handle_healing_pending(self):
        """Handle GET /healing/pending request - list pending actions."""
        if not SELF_HEALING_AVAILABLE:
            return jsonify({"error": ERR_SELF_HEALING}), 503

        station_id = request.args.get('station_id')
        healing_service = get_self_healing_service()
        actions = healing_service.get_pending_actions(station_id)
        return jsonify({"actions": actions, "count": len(actions)})

    def _handle_healing_history(self):
        """Handle GET /healing/history request - get execution history."""
        if not SELF_HEALING_AVAILABLE:
            return jsonify({"error": ERR_SELF_HEALING}), 503

        station_id = request.args.get('station_id')
        limit = request.args.get('limit', 100, type=int)

        healing_service = get_self_healing_service()
        history = healing_service.get_execution_history(station_id, limit)
        return jsonify({"history": history, "count": len(history)})

    def _handle_healing_stats(self):
        """Handle GET /healing/stats request - get service statistics."""
        if not SELF_HEALING_AVAILABLE:
            return jsonify({"error": ERR_SELF_HEALING}), 503

        healing_service = get_self_healing_service()
        return jsonify(healing_service.get_stats())

    # ========== Digital Twin Handlers ==========

    def _handle_twin_create(self, station_id: str):
        """Handle POST /digital-twin/<station_id> - create digital twin."""
        if not DIGITAL_TWIN_AVAILABLE:
            return jsonify({"error": ERR_DIGITAL_TWIN}), 503

        data = request.get_json() or {}
        twin_service = get_digital_twin_service()
        twin = twin_service.create_twin(station_id, data.get("station_data"))
        return jsonify(twin.get_state()), 201

    def _handle_twin_get(self, station_id: str):
        """Handle GET /digital-twin/<station_id> - get twin state."""
        if not DIGITAL_TWIN_AVAILABLE:
            return jsonify({"error": ERR_DIGITAL_TWIN}), 503

        twin_service = get_digital_twin_service()
        twin = twin_service.get_twin(station_id)
        if not twin:
            return jsonify({"error": ERR_TWIN_NOT_FOUND}), 404
        return jsonify(twin.get_state())

    def _handle_twin_delete(self, station_id: str):
        """Handle DELETE /digital-twin/<station_id> - delete twin."""
        if not DIGITAL_TWIN_AVAILABLE:
            return jsonify({"error": ERR_DIGITAL_TWIN}), 503

        twin_service = get_digital_twin_service()
        if twin_service.delete_twin(station_id):
            return jsonify({"status": "deleted"})
        return jsonify({"error": ERR_TWIN_NOT_FOUND}), 404

    def _handle_twin_simulate(self, station_id: str):
        """Handle POST /digital-twin/<station_id>/simulate - run simulation."""
        if not DIGITAL_TWIN_AVAILABLE:
            return jsonify({"error": ERR_DIGITAL_TWIN}), 503

        data = request.get_json() or {}
        twin_service = get_digital_twin_service()

        result = twin_service.run_what_if(
            station_id,
            scenario=data.get("scenario", {}),
            duration_hours=data.get("duration_hours", 168.0)
        )
        if not result:
            return jsonify({"error": "Twin not found or simulation failed"}), 404
        return jsonify(result.to_dict())

    def _handle_twin_predict(self, station_id: str):
        """Handle GET /digital-twin/<station_id>/predict - predict failures."""
        if not DIGITAL_TWIN_AVAILABLE:
            return jsonify({"error": ERR_DIGITAL_TWIN}), 503

        horizon = request.args.get("horizon_days", 30, type=int)
        twin_service = get_digital_twin_service()
        twin = twin_service.get_twin(station_id)
        if not twin:
            return jsonify({"error": ERR_TWIN_NOT_FOUND}), 404

        predictions = twin.predict_failures(horizon)
        return jsonify({"station_id": station_id, "predictions": predictions})

    def _handle_twin_fleet_health(self):
        """Handle GET /digital-twin/fleet/health - get fleet health."""
        if not DIGITAL_TWIN_AVAILABLE:
            return jsonify({"error": ERR_DIGITAL_TWIN}), 503

        twin_service = get_digital_twin_service()
        return jsonify(twin_service.get_fleet_health())

    # ========== Generative AI Handlers ==========

    def _handle_generate_scenario(self):
        """Handle POST /generative/scenario - generate failure scenario."""
        if not GENERATIVE_AI_AVAILABLE:
            return jsonify({"error": ERR_GENERATIVE_AI}), 503

        data = request.get_json() or {}
        gen_service = get_generative_ai_service()

        scenario_type = ScenarioType(data.get("scenario_type", "hardware_failure"))
        method = GenerationMethod(data.get("method", "template"))

        scenario = gen_service.generate_scenario(
            scenario_type=scenario_type,
            pattern_name=data.get("pattern_name"),
            method=method,
            custom_params=data.get("custom_params")
        )
        return jsonify(scenario.to_dict()), 201

    def _handle_generate_batch(self):
        """Handle POST /generative/batch - generate training batch."""
        if not GENERATIVE_AI_AVAILABLE:
            return jsonify({"error": ERR_GENERATIVE_AI}), 503

        data = request.get_json() or {}
        gen_service = get_generative_ai_service()

        batch = gen_service.generate_training_batch(
            batch_size=data.get("batch_size", 100)
        )
        return jsonify(batch.to_dict()), 201

    def _handle_generate_edge_cases(self):
        """Handle POST /generative/edge-cases - generate edge cases."""
        if not GENERATIVE_AI_AVAILABLE:
            return jsonify({"error": ERR_GENERATIVE_AI}), 503

        data = request.get_json() or {}
        gen_service = get_generative_ai_service()

        edge_cases = gen_service.generate_edge_cases(count=data.get("count", 20))
        return jsonify({
            "count": len(edge_cases),
            "scenarios": [s.to_dict() for s in edge_cases]
        }), 201

    def _handle_generative_stats(self):
        """Handle GET /generative/stats - get generation statistics."""
        if not GENERATIVE_AI_AVAILABLE:
            return jsonify({"error": ERR_GENERATIVE_AI}), 503

        gen_service = get_generative_ai_service()
        return jsonify(gen_service.get_statistics())

    # ========== Computer Vision Handlers ==========

    def _handle_cv_inspect(self):
        """Handle POST /cv/inspect - inspect image from base64."""
        if not COMPUTER_VISION_AVAILABLE:
            return jsonify({"error": ERR_COMPUTER_VISION}), 503

        data = request.get_json() or {}
        cv_service = get_computer_vision_service()

        if "image_base64" not in data:
            return jsonify({"error": "image_base64 required"}), 400

        inspection_type = InspectionType(
            data.get("inspection_type", "equipment_condition")
        )

        result = cv_service.inspect_from_base64(
            base64_data=data["image_base64"],
            station_id=data.get("station_id", "unknown"),
            inspection_type=inspection_type,
            equipment_type=data.get("equipment_type", "generic")
        )
        return jsonify(result.to_dict())

    def _handle_cv_inspect_file(self):
        """Handle POST /cv/inspect-file - inspect image from file path."""
        if not COMPUTER_VISION_AVAILABLE:
            return jsonify({"error": ERR_COMPUTER_VISION}), 503

        data = request.get_json() or {}
        cv_service = get_computer_vision_service()

        if "file_path" not in data:
            return jsonify({"error": "file_path required"}), 400

        inspection_type = InspectionType(
            data.get("inspection_type", "equipment_condition")
        )

        result = cv_service.inspect_from_file(
            file_path=data["file_path"],
            station_id=data.get("station_id", "unknown"),
            inspection_type=inspection_type,
            equipment_type=data.get("equipment_type", "generic")
        )
        return jsonify(result.to_dict())

    def _handle_cv_history(self, station_id: str):
        """Handle GET /cv/history/<station_id> - get inspection history."""
        if not COMPUTER_VISION_AVAILABLE:
            return jsonify({"error": ERR_COMPUTER_VISION}), 503

        limit = request.args.get("limit", 100, type=int)
        cv_service = get_computer_vision_service()

        history = cv_service.get_inspection_history(station_id, limit)
        return jsonify({
            "station_id": station_id,
            "inspections": [r.to_dict() for r in history]
        })

    def _handle_cv_stats(self):
        """Handle GET /cv/stats - get CV statistics."""
        if not COMPUTER_VISION_AVAILABLE:
            return jsonify({"error": ERR_COMPUTER_VISION}), 503

        cv_service = get_computer_vision_service()
        return jsonify(cv_service.get_statistics())

    # ========== Drone Integration Handlers ==========

    def _handle_drone_list(self):
        """Handle GET /drone/list - list all drones."""
        if not DRONE_INTEGRATION_AVAILABLE:
            return jsonify({"error": ERR_DRONE}), 503

        drone_service = get_drone_service()
        drones = drone_service.get_all_drones()
        return jsonify({"drones": [d.to_dict() for d in drones]})

    def _handle_drone_state(self, drone_id: str):
        """Handle GET /drone/<drone_id> - get drone state."""
        if not DRONE_INTEGRATION_AVAILABLE:
            return jsonify({"error": ERR_DRONE}), 503

        drone_service = get_drone_service()
        state = drone_service.get_drone_state(drone_id)
        if not state:
            return jsonify({"error": "Drone not found"}), 404
        return jsonify(state.to_dict())

    def _handle_drone_create_mission(self):
        """Handle POST /drone/mission - create inspection mission."""
        if not DRONE_INTEGRATION_AVAILABLE:
            return jsonify({"error": ERR_DRONE}), 503

        data = request.get_json() or {}
        drone_service = get_drone_service()

        if "station_id" not in data or "location" not in data:
            return jsonify({"error": "station_id and location required"}), 400

        loc = data["location"]
        location = GeoPoint(
            latitude=loc["latitude"],
            longitude=loc["longitude"],
            altitude=loc.get("altitude", 0)
        )

        mission_type = MissionType(data.get("mission_type", "site_inspection"))

        mission = drone_service.create_mission(
            mission_type=mission_type,
            station_id=data["station_id"],
            station_location=location,
            drone_id=data.get("drone_id"),
            custom_params=data.get("params")
        )
        return jsonify(mission.to_dict()), 201

    def _handle_drone_mission_status(self, mission_id: str):
        """Handle GET /drone/mission/<mission_id> - get mission status."""
        if not DRONE_INTEGRATION_AVAILABLE:
            return jsonify({"error": ERR_DRONE}), 503

        drone_service = get_drone_service()
        status = drone_service.get_mission_status(mission_id)
        if not status:
            return jsonify({"error": "Mission not found"}), 404
        return jsonify(status)

    def _handle_drone_start_mission(self, mission_id: str):
        """Handle POST /drone/mission/<mission_id>/start - start mission."""
        if not DRONE_INTEGRATION_AVAILABLE:
            return jsonify({"error": ERR_DRONE}), 503

        drone_service = get_drone_service()
        success = drone_service.start_mission(mission_id)
        if success:
            return jsonify({"status": "started", "mission_id": mission_id})
        return jsonify({"error": "Failed to start mission"}), 400

    def _handle_drone_abort_mission(self, mission_id: str):
        """Handle POST /drone/mission/<mission_id>/abort - abort mission."""
        if not DRONE_INTEGRATION_AVAILABLE:
            return jsonify({"error": ERR_DRONE}), 503

        drone_service = get_drone_service()
        success = drone_service.abort_mission(mission_id)
        if success:
            return jsonify({"status": "aborted", "mission_id": mission_id})
        return jsonify({"error": "Failed to abort mission"}), 400

    def _handle_drone_captures(self, mission_id: str):
        """Handle GET /drone/mission/<mission_id>/captures - get captures."""
        if not DRONE_INTEGRATION_AVAILABLE:
            return jsonify({"error": ERR_DRONE}), 503

        drone_service = get_drone_service()
        captures = drone_service.get_captured_data(mission_id)
        return jsonify({
            "mission_id": mission_id,
            "captures": [c.to_dict() for c in captures]
        })

    def _handle_drone_stats(self):
        """Handle GET /drone/stats - get drone statistics."""
        if not DRONE_INTEGRATION_AVAILABLE:
            return jsonify({"error": ERR_DRONE}), 503

        drone_service = get_drone_service()
        return jsonify(drone_service.get_statistics())

    def _handle_son_test(self):
        """Handle POST /son/test - trigger SON analysis with test data."""
        if not SON_SCHEDULER_AVAILABLE:
            return jsonify({"error": "SON scheduler not available"}), 503

        from son_functions import analyze_cells

        # Test data with values that exceed thresholds:
        # - PRB > 80% for MLB (overloaded cells)
        # - HO failure > 5% for MRO
        # - RSRP < -110 dBm for CCO (poor coverage)
        # - PRB < 20% for ES (energy saving)
        test_cells = [
            {
                "cell_id": "test-cell-overloaded",
                "station_id": "1",
                "timestamp": datetime.now().isoformat(),
                "prb_utilization": 85.0,  # Triggers MLB (> 80%)
                "active_users": 120,
                "dl_throughput": 500.0,
                "ul_throughput": 200.0,
                "rsrp_avg": -90.0,
                "sinr_avg": 15.0,
                "handover_success_rate": 92.0,  # Triggers MRO (< 95%)
                "handover_failure_rate": 8.0,
                "rrc_setup_success_rate": 99.5,
                "paging_success_rate": 99.0,
                "interference_level": -85.0,  # Triggers CCO (> -90 dBm)
                "cqi_avg": 10.0,
                "power_consumption": 600.0,
                "neighbor_cells": ["test-cell-underloaded"],
            },
            {
                "cell_id": "test-cell-underloaded",
                "station_id": "1",
                "timestamp": datetime.now().isoformat(),
                "prb_utilization": 15.0,  # Triggers ES (< 20%)
                "active_users": 5,
                "dl_throughput": 50.0,
                "ul_throughput": 20.0,
                "rsrp_avg": -115.0,  # Triggers CCO (< -110 dBm)
                "sinr_avg": 5.0,
                "handover_success_rate": 99.0,
                "handover_failure_rate": 1.0,
                "rrc_setup_success_rate": 99.0,
                "paging_success_rate": 99.0,
                "interference_level": -100.0,
                "cqi_avg": 8.0,
                "power_consumption": 400.0,
                "neighbor_cells": ["test-cell-overloaded"],
            },
        ]

        # Run SON analysis on test data
        recommendations = analyze_cells(test_cells, ["mlb", "mro", "cco", "es"])

        logger.info(f"SON test generated {len(recommendations)} recommendations")

        return jsonify({
            "test_cells": len(test_cells),
            "recommendations_generated": len(recommendations),
            "recommendations": recommendations,
        })

    def _handle_son_test_post(self):
        """Handle POST /son/test/post - generate test recommendations and post to monitoring service."""
        if not SON_SCHEDULER_AVAILABLE:
            return jsonify({"error": "SON scheduler not available"}), 503

        from son_scheduler import get_son_scheduler

        # Get the scheduler
        scheduler = get_son_scheduler()

        # Generate test recommendations
        from son_functions import analyze_cells

        test_cells = [
            {
                "cell_id": f"test-cell-{datetime.now().strftime('%H%M%S')}-overloaded",
                "station_id": "1",
                "timestamp": datetime.now().isoformat(),
                "prb_utilization": 85.0,
                "active_users": 120,
                "dl_throughput": 500.0,
                "ul_throughput": 200.0,
                "rsrp_avg": -90.0,
                "sinr_avg": 15.0,
                "handover_success_rate": 92.0,
                "handover_failure_rate": 8.0,
                "rrc_setup_success_rate": 99.5,
                "paging_success_rate": 99.0,
                "interference_level": -85.0,
                "cqi_avg": 10.0,
                "power_consumption": 600.0,
                "neighbor_cells": [f"test-cell-{datetime.now().strftime('%H%M%S')}-underloaded"],
            },
            {
                "cell_id": f"test-cell-{datetime.now().strftime('%H%M%S')}-underloaded",
                "station_id": "1",
                "timestamp": datetime.now().isoformat(),
                "prb_utilization": 15.0,
                "active_users": 5,
                "dl_throughput": 50.0,
                "ul_throughput": 20.0,
                "rsrp_avg": -115.0,
                "sinr_avg": 5.0,
                "handover_success_rate": 99.0,
                "handover_failure_rate": 1.0,
                "rrc_setup_success_rate": 99.0,
                "paging_success_rate": 99.0,
                "interference_level": -100.0,
                "cqi_avg": 8.0,
                "power_consumption": 400.0,
                "neighbor_cells": [f"test-cell-{datetime.now().strftime('%H%M%S')}-overloaded"],
            },
        ]

        recommendations = analyze_cells(test_cells, ["mlb", "mro", "cco", "es"])
        posted = 0
        errors = []

        for rec in recommendations:
            try:
                if scheduler._post_recommendation(rec):
                    posted += 1
                else:
                    errors.append(f"Failed to post {rec.get('recommendation_id')}")
            except Exception as e:
                errors.append(f"Error posting {rec.get('recommendation_id')}: {e}")

        logger.info(f"SON test post: generated {len(recommendations)}, posted {posted}")

        return jsonify({
            "recommendations_generated": len(recommendations),
            "recommendations_posted": posted,
            "errors": errors if errors else None,
        })

    def _add_cors_headers(self, response):
        """Add CORS headers to response."""
        response.headers['Access-Control-Allow-Origin'] = '*'
        response.headers['Access-Control-Allow-Methods'] = 'GET, POST, OPTIONS'
        response.headers['Access-Control-Allow-Headers'] = 'Content-Type, X-HMAC-Signature'
        return response

    def _register_routes(self):
        """Register all Flask routes."""
        assert self.app is not None, "Flask app must be initialized before registering routes"
        app = self.app  # Local reference for type narrowing

        app.after_request(self._add_cors_headers)

        app.route('/diagnose', methods=['POST'])(
            self._require_auth(self._handle_diagnose))
        app.route('/health', methods=['GET'])(self._handle_health)
        app.route('/reports/bi', methods=['GET'])(self._handle_bi_report)
        app.route('/reports/diagnostics', methods=['GET'])(self._handle_diagnostics_log)
        app.route('/learning/feedback', methods=['POST'])(
            self._require_auth(self._handle_feedback))
        app.route('/learning/stats', methods=['GET'])(self._handle_learning_stats)
        app.route('/learning/patterns', methods=['GET'])(self._handle_patterns)
        app.route('/learning/patterns/<problem_code>', methods=['GET'])(self._handle_pattern)

        # Computer Vision endpoints
        app.route('/vision/analyze-led', methods=['POST'])(
            self._require_auth(self._handle_vision_analyze_led))

        # Alarm Correlation endpoints
        app.route('/alarms/correlate', methods=['POST'])(
            self._require_auth(self._handle_alarms_correlate))

        # Predictive Maintenance endpoints
        app.route('/maintenance/<station_id>/health', methods=['GET'])(
            self._handle_maintenance_health)
        app.route('/maintenance/analyze', methods=['POST'])(
            self._require_auth(self._handle_maintenance_analyze))
        app.route('/maintenance/<station_id>/battery', methods=['GET'])(
            self._handle_battery_health)
        app.route('/maintenance/<station_id>/fiber', methods=['GET'])(
            self._handle_fiber_health)

        # Configuration Drift Detection endpoints
        app.route('/config/drift', methods=['POST'])(
            self._require_auth(self._handle_config_drift_detect))
        app.route('/config/baseline', methods=['POST'])(
            self._require_auth(self._handle_config_baseline_set))
        app.route('/config/baseline/<station_id>', methods=['GET'])(
            self._handle_config_baseline_get)

        # Root Cause Analysis endpoints
        app.route('/rca/analyze', methods=['POST'])(
            self._require_auth(self._handle_rca_analyze))
        app.route('/rca/stats', methods=['GET'])(
            self._handle_rca_stats)
        app.route('/rca/feedback', methods=['POST'])(
            self._require_auth(self._handle_rca_feedback))

        # Self-Healing endpoints
        app.route('/healing/actions', methods=['POST'])(
            self._require_auth(self._handle_healing_submit))
        app.route('/healing/from-son', methods=['POST'])(
            self._require_auth(self._handle_healing_from_son))
        app.route('/healing/from-rca', methods=['POST'])(
            self._require_auth(self._handle_healing_from_rca))
        app.route('/healing/actions/<action_id>/approve', methods=['POST'])(
            self._require_auth(self._handle_healing_approve))
        app.route('/healing/actions/<action_id>/cancel', methods=['POST'])(
            self._require_auth(self._handle_healing_cancel))
        app.route('/healing/pending', methods=['GET'])(
            self._handle_healing_pending)
        app.route('/healing/history', methods=['GET'])(
            self._handle_healing_history)
        app.route('/healing/stats', methods=['GET'])(
            self._handle_healing_stats)

        # Digital Twin endpoints
        app.route(ROUTE_DIGITAL_TWIN, methods=['POST'])(
            self._require_auth(self._handle_twin_create))
        app.route(ROUTE_DIGITAL_TWIN, methods=['GET'])(
            self._handle_twin_get)
        app.route(ROUTE_DIGITAL_TWIN, methods=['DELETE'])(
            self._require_auth(self._handle_twin_delete))
        app.route(f"{ROUTE_DIGITAL_TWIN}/simulate", methods=['POST'])(
            self._require_auth(self._handle_twin_simulate))
        app.route(f"{ROUTE_DIGITAL_TWIN}/predict", methods=['GET'])(
            self._handle_twin_predict)
        app.route('/digital-twin/fleet/health', methods=['GET'])(
            self._handle_twin_fleet_health)

        # Generative AI endpoints
        app.route('/generative/scenario', methods=['POST'])(
            self._require_auth(self._handle_generate_scenario))
        app.route('/generative/batch', methods=['POST'])(
            self._require_auth(self._handle_generate_batch))
        app.route('/generative/edge-cases', methods=['POST'])(
            self._require_auth(self._handle_generate_edge_cases))
        app.route('/generative/stats', methods=['GET'])(
            self._handle_generative_stats)

        # Computer Vision (Advanced) endpoints
        app.route('/cv/inspect', methods=['POST'])(
            self._require_auth(self._handle_cv_inspect))
        app.route('/cv/inspect-file', methods=['POST'])(
            self._require_auth(self._handle_cv_inspect_file))
        app.route('/cv/history/<station_id>', methods=['GET'])(
            self._handle_cv_history)
        app.route('/cv/stats', methods=['GET'])(
            self._handle_cv_stats)

        # Drone Integration endpoints
        app.route('/drone/list', methods=['GET'])(
            self._handle_drone_list)
        app.route('/drone/<drone_id>', methods=['GET'])(
            self._handle_drone_state)
        app.route('/drone/mission', methods=['POST'])(
            self._require_auth(self._handle_drone_create_mission))
        app.route('/drone/mission/<mission_id>', methods=['GET'])(
            self._handle_drone_mission_status)
        app.route('/drone/mission/<mission_id>/start', methods=['POST'])(
            self._require_auth(self._handle_drone_start_mission))
        app.route('/drone/mission/<mission_id>/abort', methods=['POST'])(
            self._require_auth(self._handle_drone_abort_mission))
        app.route('/drone/mission/<mission_id>/captures', methods=['GET'])(
            self._handle_drone_captures)
        app.route('/drone/stats', methods=['GET'])(
            self._handle_drone_stats)

        # SON Test endpoints
        app.route('/son/test', methods=['POST'])(
            self._require_auth(self._handle_son_test))
        app.route('/son/test/post', methods=['POST'])(
            self._require_auth(self._handle_son_test_post))

    def start(self):
        if not FLASK_AVAILABLE:
            logger.warning("Flask not available - install flask")
            return

        self.running = True
        self.app = Flask(__name__)
        register_metrics_endpoint(self.app)
        self._setup_tracing()
        self._register_routes()

        app = self.app
        assert app is not None  # Type narrowing for lambda
        threading.Thread(
            target=lambda: app.run(host=self.host, port=self.port, threaded=True),
            daemon=True
        ).start()

        logger.info(f"HTTP adapter listening on {self.host}:{self.port}")

    def send_solution(self, solution: Solution, _):
        pass  # HTTP is request/response, solution sent in response

    def stop(self):
        self.running = False
