"""
Internal Authentication Module

Handles service-to-service authentication using HMAC signatures.
"""

import hmac
import hashlib
import time
import logging

logger = logging.getLogger(__name__)


def verify_internal_auth(auth_header: str, secret: str) -> bool:
    """
    Verify X-Internal-Auth header (signature.payload format).

    Args:
        auth_header: The X-Internal-Auth header value
        secret: The shared secret for HMAC verification

    Returns:
        True if authentication is valid, False otherwise
    """
    if not secret or not auth_header:
        return False

    try:
        parts = auth_header.split(".", 1)
        if len(parts) != 2:
            return False

        signature, payload = parts
        expected = hmac.new(
            secret.encode(),
            payload.encode(),
            hashlib.sha256
        ).hexdigest()

        if not hmac.compare_digest(expected, signature):
            return False

        # Parse payload: "service:role:timestamp"
        payload_parts = payload.split(":")
        if len(payload_parts) >= 3:
            timestamp_ms = int(payload_parts[2])
            now_ms = int(time.time() * 1000)
            max_age_ms = 300000  # 5 minutes
            if abs(now_ms - timestamp_ms) > max_age_ms:
                logger.warning("Internal auth timestamp too old")
                return False

        return True
    except Exception as e:
        logger.warning(f"Internal auth verification failed: {e}")
        return False
