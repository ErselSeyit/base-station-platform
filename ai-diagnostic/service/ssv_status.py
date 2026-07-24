"""
Site Verification (SSV) status logic.

Pure pass/warn/fail evaluation of a measured KPI against its SSV threshold
config (the 3GPP / Huawei acceptance criteria). Extracted from
bi_report_generator.py so the acceptance logic can be tested apart from the PDF
rendering; the report maps the returned status to a display colour.
"""

from typing import Optional


def ssv_status(value: float, thresholds: Optional[dict]) -> str:
    """
    Classify a KPI value as ``"PASS"``, ``"WARN"``, ``"FAIL"`` or ``"N/A"``.

    ``thresholds`` is the per-metric config: ``higher_better`` (default True)
    with ``min``/``warn``, or a lower-is-better metric with ``max``/``warn``.
    Returns ``"N/A"`` when no thresholds are known for the metric.
    """
    if not thresholds:
        return "N/A"

    if thresholds.get("higher_better", True):
        min_val = thresholds.get("min", 0)
        warn_val = thresholds.get("warn", min_val)
        if value >= warn_val:
            return "PASS"
        if value >= min_val:
            return "WARN"
        return "FAIL"

    max_val = thresholds.get("max", 100)
    warn_val = thresholds.get("warn", max_val)
    if value <= warn_val:
        return "PASS"
    if value <= max_val:
        return "WARN"
    return "FAIL"
