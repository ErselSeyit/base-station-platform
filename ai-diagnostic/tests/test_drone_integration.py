"""
Characterisation tests for the pure geometry in drone_integration.py — the
GeoPoint haversine distance and the FlightPathPlanner waypoint patterns — which
had no coverage. They lock the observable geometry (waypoint counts, orbit
radius, positive distance/duration) so the models and planner can be split into
their own modules without changing the planned paths.
"""

import pytest

from service.drone_integration import (
    CaptureType,
    FlightPath,
    FlightPathPlanner,
    GeoPoint,
    MissionType,
    Waypoint,
)


class TestGeoPoint:
    def test_distance_to_self_is_zero(self):
        p = GeoPoint(latitude=40.0, longitude=-74.0, altitude=30.0)
        assert p.distance_to(p) == pytest.approx(0.0, abs=1e-6)

    def test_one_milli_degree_north_is_about_111_metres(self):
        a = GeoPoint(latitude=0.0, longitude=0.0, altitude=0.0)
        b = GeoPoint(latitude=0.001, longitude=0.0, altitude=0.0)
        assert a.distance_to(b) == pytest.approx(111.2, abs=1.0)

    def test_altitude_difference_contributes(self):
        a = GeoPoint(latitude=0.0, longitude=0.0, altitude=0.0)
        b = GeoPoint(latitude=0.0, longitude=0.0, altitude=30.0)
        assert a.distance_to(b) == pytest.approx(30.0, abs=1e-3)

    def test_to_dict_carries_optional_heading(self):
        assert GeoPoint(1.0, 2.0, 3.0).to_dict()["heading"] is None


class TestFlightPathPlanner:
    def _center(self):
        return GeoPoint(latitude=40.0, longitude=-74.0, altitude=0.0)

    def test_orbit_pattern_has_nine_closed_waypoints(self):
        path = FlightPathPlanner().plan_mission(MissionType.SITE_INSPECTION, self._center())
        assert isinstance(path, FlightPath)
        assert len(path.waypoints) == 9  # 8 points + 1 to close the orbit
        assert all(isinstance(w, Waypoint) for w in path.waypoints)
        assert path.total_distance > 0
        assert path.estimated_duration > 0

    def test_orbit_waypoints_sit_near_the_configured_radius(self):
        center = self._center()
        path = FlightPathPlanner().plan_mission(MissionType.SITE_INSPECTION, center)
        ground = GeoPoint(center.latitude, center.longitude, altitude=30.0)
        # SITE_INSPECTION orbit_radius is 50 m; each capture point should be ~that
        # far from the center at the same altitude.
        for wp in path.waypoints[:8]:
            horizontal = ground.distance_to(
                GeoPoint(wp.position.latitude, wp.position.longitude, altitude=30.0)
            )
            assert horizontal == pytest.approx(50.0, abs=5.0)

    def test_capture_points_use_photo_capture(self):
        path = FlightPathPlanner().plan_mission(MissionType.ANTENNA_INSPECTION, self._center())
        assert path.waypoints[0].capture_type == CaptureType.PHOTO

    def test_thermal_scan_produces_a_grid(self):
        path = FlightPathPlanner().plan_mission(MissionType.THERMAL_SCAN, self._center())
        assert len(path.waypoints) > 0
        assert path.total_distance > 0

    def test_tower_inspection_produces_a_spiral(self):
        path = FlightPathPlanner().plan_mission(MissionType.TOWER_INSPECTION, self._center())
        assert len(path.waypoints) > 0
        assert path.total_distance > 0
