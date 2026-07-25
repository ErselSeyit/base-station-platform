"""
Characterisation tests for the SSV (Site Verification) pass/warn/fail logic
extracted from bi_report_generator. Locks the acceptance criteria (which KPI
values pass, warn or fail) so the report rendering can change without shifting
what counts as a passing site.
"""

from service.ssv_status import ssv_status

# 3GPP/Huawei acceptance thresholds mirrored from the report.
_DL = {"min": 1000, "warn": 1100, "higher_better": True}
_LATENCY = {"max": 15, "warn": 12, "higher_better": False}


class TestHigherIsBetter:
    def test_pass_at_or_above_warn(self):
        assert ssv_status(1200, _DL) == "PASS"
        assert ssv_status(1100, _DL) == "PASS"

    def test_warn_between_min_and_warn(self):
        assert ssv_status(1050, _DL) == "WARN"
        assert ssv_status(1000, _DL) == "WARN"

    def test_fail_below_min(self):
        assert ssv_status(999, _DL) == "FAIL"


class TestLowerIsBetter:
    def test_pass_at_or_below_warn(self):
        assert ssv_status(10, _LATENCY) == "PASS"
        assert ssv_status(12, _LATENCY) == "PASS"

    def test_warn_between_warn_and_max(self):
        assert ssv_status(14, _LATENCY) == "WARN"
        assert ssv_status(15, _LATENCY) == "WARN"

    def test_fail_above_max(self):
        assert ssv_status(20, _LATENCY) == "FAIL"


class TestUnknown:
    def test_no_thresholds_is_not_applicable(self):
        assert ssv_status(123.0, None) == "N/A"
        assert ssv_status(123.0, {}) == "N/A"
