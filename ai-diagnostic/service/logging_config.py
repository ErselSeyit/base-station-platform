"""
Structured JSON logging configuration for production.

In production (ENVIRONMENT=production), outputs JSON-formatted logs compatible
with ELK/Loki log aggregation. In development, uses human-readable format.

Usage:
    from service.logging_config import configure_logging
    configure_logging()
"""

import json
import logging
import os
import traceback
from datetime import datetime, timezone


class JsonFormatter(logging.Formatter):
    """Formats log records as JSON, matching Java Logstash encoder output."""

    def format(self, record):
        log_entry = {
            "@timestamp": datetime.now(timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "service": "ai-diagnostic",
            "thread": record.threadName,
        }

        if record.exc_info and record.exc_info[0] is not None:
            log_entry["exception"] = {
                "class": record.exc_info[0].__name__,
                "message": str(record.exc_info[1]),
                "stacktrace": traceback.format_exception(*record.exc_info),
            }

        # Propagate extra fields if set (e.g., correlation_id, station_id)
        for attr in ("correlation_id", "station_id", "problem_id", "request_id"):
            value = getattr(record, attr, None)
            if value is not None:
                log_entry[attr] = value

        return json.dumps(log_entry, default=str)


def configure_logging():
    """Configure logging based on ENVIRONMENT and LOG_LEVEL env vars."""
    env = os.environ.get("ENVIRONMENT", "development")
    level_name = os.environ.get("LOG_LEVEL", "INFO")
    level = getattr(logging, level_name.upper(), logging.INFO)

    root = logging.getLogger()
    root.handlers.clear()

    handler = logging.StreamHandler()

    if env == "production":
        handler.setFormatter(JsonFormatter())
    else:
        handler.setFormatter(logging.Formatter(
            "%(asctime)s - %(name)s - %(levelname)s - %(message)s"
        ))

    root.addHandler(handler)
    root.setLevel(level)
