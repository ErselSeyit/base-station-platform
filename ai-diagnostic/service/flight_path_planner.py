"""
Flight-path planning for drone inspections.

Pure computational geometry: turns a mission type and station location into an
ordered list of waypoints (orbit, tower spiral, or thermal grid) with distance
and duration estimates. Extracted from drone_integration.py.
"""

import uuid
from typing import Dict, List, Optional

import numpy as np

from .drone_models import (
    CaptureType, FlightPath, GeoPoint, MissionType, Waypoint,
)


class FlightPathPlanner:
    """Plans optimal flight paths for inspections."""

    # Standard inspection patterns
    PATTERNS = {
        MissionType.SITE_INSPECTION: {
            "altitude": 30,
            "orbit_radius": 50,
            "capture_interval": 5,  # seconds
            "gimbal_pitch": -45
        },
        MissionType.TOWER_INSPECTION: {
            "altitude_start": 5,
            "altitude_end": 50,
            "altitude_step": 5,
            "orbit_radius": 15,
            "capture_interval": 3,
            "gimbal_pitch": 0
        },
        MissionType.ANTENNA_INSPECTION: {
            "altitude": 35,
            "orbit_radius": 10,
            "hover_time": 5,
            "gimbal_pitch": -30
        },
        MissionType.THERMAL_SCAN: {
            "altitude": 20,
            "grid_spacing": 10,
            "overlap_percent": 30,
            "gimbal_pitch": -90
        }
    }

    def plan_mission(self,
                     mission_type: MissionType,
                     station_location: GeoPoint,
                     custom_params: Optional[Dict] = None) -> FlightPath:
        """Generate a flight path for a mission."""
        path_id = str(uuid.uuid4())
        params = self.PATTERNS.get(mission_type, self.PATTERNS[MissionType.SITE_INSPECTION])

        if custom_params:
            params.update(custom_params)

        waypoints = []

        if mission_type == MissionType.TOWER_INSPECTION:
            waypoints = self._plan_tower_spiral(station_location, params)
        elif mission_type == MissionType.THERMAL_SCAN:
            waypoints = self._plan_grid_pattern(station_location, params)
        else:
            waypoints = self._plan_orbit_pattern(station_location, params)

        # Calculate totals
        total_distance = self._calculate_total_distance(waypoints)
        estimated_duration = self._estimate_duration(waypoints, total_distance)

        return FlightPath(
            path_id=path_id,
            waypoints=waypoints,
            total_distance=total_distance,
            estimated_duration=estimated_duration
        )

    def _plan_orbit_pattern(self, center: GeoPoint, params: Dict) -> List[Waypoint]:
        """Plan an orbital inspection pattern."""
        waypoints = []
        altitude = params.get("altitude", 30)
        radius = params.get("orbit_radius", 50)
        num_points = 8

        for i in range(num_points + 1):  # +1 to close the orbit
            angle = (2 * np.pi * i) / num_points

            # Calculate offset in meters, convert to lat/lon
            dx = radius * np.cos(angle)
            dy = radius * np.sin(angle)

            # Approximate conversion (valid for small distances)
            lat_offset = dy / 111000
            lon_offset = dx / (111000 * np.cos(np.radians(center.latitude)))

            position = GeoPoint(
                latitude=center.latitude + lat_offset,
                longitude=center.longitude + lon_offset,
                altitude=altitude,
                heading=(angle * 180 / np.pi + 90) % 360  # Face center
            )

            waypoint = Waypoint(
                waypoint_id=f"wp_{i}",
                position=position,
                action="capture" if i < num_points else "flyover",
                capture_type=CaptureType.PHOTO if i < num_points else None,
                gimbal_pitch=params.get("gimbal_pitch", -45)
            )
            waypoints.append(waypoint)

        return waypoints

    def _plan_tower_spiral(self, center: GeoPoint, params: Dict) -> List[Waypoint]:
        """Plan a spiral pattern for tower inspection."""
        waypoints = []
        alt_start = params.get("altitude_start", 5)
        alt_end = params.get("altitude_end", 50)
        alt_step = params.get("altitude_step", 5)
        radius = params.get("orbit_radius", 15)

        altitude = alt_start
        wp_index = 0

        while altitude <= alt_end:
            num_points = 4  # Points per level
            for i in range(num_points):
                angle = (2 * np.pi * i) / num_points

                dx = radius * np.cos(angle)
                dy = radius * np.sin(angle)

                lat_offset = dy / 111000
                lon_offset = dx / (111000 * np.cos(np.radians(center.latitude)))

                position = GeoPoint(
                    latitude=center.latitude + lat_offset,
                    longitude=center.longitude + lon_offset,
                    altitude=altitude,
                    heading=(angle * 180 / np.pi + 90) % 360
                )

                waypoint = Waypoint(
                    waypoint_id=f"wp_{wp_index}",
                    position=position,
                    action="capture",
                    capture_type=CaptureType.PHOTO,
                    gimbal_pitch=params.get("gimbal_pitch", 0),
                    hover_time=2.0
                )
                waypoints.append(waypoint)
                wp_index += 1

            altitude += alt_step

        return waypoints

    def _plan_grid_pattern(self, center: GeoPoint, params: Dict) -> List[Waypoint]:
        """Plan a grid pattern for area coverage."""
        waypoints = []
        altitude = params.get("altitude", 20)
        spacing = params.get("grid_spacing", 10)
        grid_size = 5  # 5x5 grid

        wp_index = 0
        for row in range(grid_size):
            for col in range(grid_size):
                # Serpentine pattern
                if row % 2 == 0:
                    actual_col = col
                else:
                    actual_col = grid_size - 1 - col

                dx = (actual_col - grid_size // 2) * spacing
                dy = (row - grid_size // 2) * spacing

                lat_offset = dy / 111000
                lon_offset = dx / (111000 * np.cos(np.radians(center.latitude)))

                position = GeoPoint(
                    latitude=center.latitude + lat_offset,
                    longitude=center.longitude + lon_offset,
                    altitude=altitude
                )

                waypoint = Waypoint(
                    waypoint_id=f"wp_{wp_index}",
                    position=position,
                    action="capture",
                    capture_type=CaptureType.THERMAL,
                    gimbal_pitch=params.get("gimbal_pitch", -90)
                )
                waypoints.append(waypoint)
                wp_index += 1

        return waypoints

    def _calculate_total_distance(self, waypoints: List[Waypoint]) -> float:
        """Calculate total flight distance."""
        total = 0.0
        for i in range(1, len(waypoints)):
            total += waypoints[i-1].position.distance_to(waypoints[i].position)
        return total

    def _estimate_duration(self, waypoints: List[Waypoint], total_distance: float) -> float:
        """Estimate mission duration in seconds."""
        avg_speed = 5.0  # m/s
        flight_time = total_distance / avg_speed

        hover_time = sum(w.hover_time for w in waypoints)
        capture_time = sum(3.0 for w in waypoints if w.capture_type)  # 3s per capture

        return flight_time + hover_time + capture_time
