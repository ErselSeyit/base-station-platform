"""
Tests for X.733 / 3GPP TS 28.111 alignment of the alarm model.

TS 28.111 clause 6 defines the alarmRecord attributes. Two of them exist
specifically to express alarm correlation:

  rootCauseIndicator      "indicates that this AlarmRecord is the root cause of
                           the events captured by the notifications whose
                           identifiers are in the related CorrelatedNotification
                           instances" (type: boolean)
  correlatedNotifications "At least one of these attributes shall be supported
                           if the MnS producer supports alarm correlation."

AlarmCorrelationService performs alarm correlation, so these attributes are
required for it to be standards-conformant.
"""

from datetime import datetime, timedelta

import pytest

from service.alarm_correlation import (
    Alarm,
    AlarmCorrelationService,
    AlarmSeverity,
)


def _alarm(alarm_id, alarm_type, station_id="STATION-1", offset_seconds=0,
           severity=AlarmSeverity.MAJOR):
    return Alarm(
        alarm_id=alarm_id,
        station_id=station_id,
        alarm_type=alarm_type,
        severity=severity,
        timestamp=datetime(2026, 7, 23, 12, 0, 0) + timedelta(seconds=offset_seconds),
        message=f"{alarm_type} on {station_id}",
    )


class TestAlarmSeverityVocabulary:

    def test_defines_all_six_28111_allowed_values(self):
        names = {s.name for s in AlarmSeverity}
        assert {"CRITICAL", "MAJOR", "MINOR", "WARNING",
                "INDETERMINATE", "CLEARED"} <= names

    def test_from_string_is_case_insensitive(self):
        assert AlarmSeverity.from_string("critical") is AlarmSeverity.CRITICAL
        assert AlarmSeverity.from_string("MAJOR") is AlarmSeverity.MAJOR

    def test_legacy_info_maps_to_indeterminate(self):
        # X.733 has no INFO value; INDETERMINATE is the non-escalating equivalent.
        assert AlarmSeverity.from_string("info") is AlarmSeverity.INDETERMINATE

    def test_unknown_value_raises_rather_than_being_coerced(self):
        with pytest.raises(ValueError):
            AlarmSeverity.from_string("SEVERE")

    def test_only_cleared_is_inactive(self):
        assert AlarmSeverity.CLEARED.is_active() is False
        for sev in (AlarmSeverity.CRITICAL, AlarmSeverity.MAJOR, AlarmSeverity.MINOR,
                    AlarmSeverity.WARNING, AlarmSeverity.INDETERMINATE):
            assert sev.is_active() is True


class TestAlarmRecordFields:

    def test_alarm_exposes_x733_correlation_attributes(self):
        alarm = _alarm("A-1", "POWER_FAILURE")
        assert alarm.root_cause_indicator is False
        assert alarm.correlated_notifications == []
        assert alarm.probable_cause is None
        assert alarm.specific_problem is None
        assert alarm.object_instance is None

    def test_to_dict_emits_x733_attribute_names(self):
        alarm = _alarm("A-1", "POWER_FAILURE")
        alarm.probable_cause = "powerSupplyFailure"
        alarm.object_instance = "ManagedElement=1,NRCellDU=3"
        payload = alarm.to_dict()

        assert payload["rootCauseIndicator"] is False
        assert payload["correlatedNotifications"] == []
        assert payload["probableCause"] == "powerSupplyFailure"
        assert payload["objectInstance"] == "ManagedElement=1,NRCellDU=3"
        assert payload["perceivedSeverity"] == "MAJOR"

    def test_to_dict_keeps_existing_keys_for_backward_compatibility(self):
        payload = _alarm("A-1", "POWER_FAILURE").to_dict()
        for legacy_key in ("alarm_id", "station_id", "alarm_type", "severity",
                           "timestamp", "message", "cleared", "acknowledged"):
            assert legacy_key in payload


class TestCorrelationPopulatesRootCause:

    def test_root_cause_alarm_is_flagged_and_carries_correlated_ids(self):
        # POWER_FAILURE -> TEMPERATURE_HIGH is a known causal rule.
        alarms = [
            _alarm("A-root", "POWER_FAILURE", offset_seconds=0),
            _alarm("A-effect", "TEMPERATURE_HIGH", offset_seconds=30),
        ]
        result = AlarmCorrelationService().correlate_alarms(alarms)

        clustered = [a for c in result.clusters for a in c.alarms]
        assert clustered, "expected the two alarms to be correlated into a cluster"

        roots = [a for a in clustered if a.root_cause_indicator]
        assert len(roots) == 1, "exactly one alarm record is the root cause"
        assert roots[0].alarm_type == "POWER_FAILURE"

        # The root record carries the identifiers of the notifications it explains.
        assert "A-effect" in roots[0].correlated_notifications
        assert roots[0].alarm_id not in roots[0].correlated_notifications

    def test_non_root_alarms_are_not_flagged(self):
        alarms = [
            _alarm("A-root", "POWER_FAILURE", offset_seconds=0),
            _alarm("A-effect", "TEMPERATURE_HIGH", offset_seconds=30),
        ]
        result = AlarmCorrelationService().correlate_alarms(alarms)

        clustered = [a for c in result.clusters for a in c.alarms]
        effects = [a for a in clustered if a.alarm_type == "TEMPERATURE_HIGH"]
        assert effects
        assert all(not a.root_cause_indicator for a in effects)
        assert all(a.correlated_notifications == [] for a in effects)

    def test_single_alarm_cluster_has_no_root_cause_flag(self):
        result = AlarmCorrelationService().correlate_alarms([_alarm("A-1", "FAN_FAILURE")])
        all_alarms = [a for c in result.clusters for a in c.alarms] + result.uncorrelated_alarms
        assert all(not a.root_cause_indicator for a in all_alarms)
