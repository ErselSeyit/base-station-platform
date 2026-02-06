"""
Drone Integration Service for Automated Site Inspections.

Provides integration with drone platforms for:
- Automated site inspection missions
- Visual data collection
- Thermal imaging
- Real-time video streaming
- Flight path planning
- Safety zone management
"""

import json
import logging
import threading
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from enum import Enum
from typing import Any, Callable, Dict, List, Optional, Tuple

import numpy as np

logger = logging.getLogger(__name__)

# Modern RNG (replaces deprecated np.random functions)
_rng = np.random.default_rng(42)


class DroneStatus(Enum):
    """Drone operational status."""
    OFFLINE = "offline"
    STANDBY = "standby"
    PREFLIGHT = "preflight"
    FLYING = "flying"
    HOVERING = "hovering"
    RETURNING = "returning"
    LANDING = "landing"
    CHARGING = "charging"
    MAINTENANCE = "maintenance"
    ERROR = "error"


class MissionType(Enum):
    """Types of drone missions."""
    SITE_INSPECTION = "site_inspection"
    TOWER_INSPECTION = "tower_inspection"
    ANTENNA_INSPECTION = "antenna_inspection"
    THERMAL_SCAN = "thermal_scan"
    EMERGENCY_RESPONSE = "emergency_response"
    PERIMETER_CHECK = "perimeter_check"
    VEGETATION_SURVEY = "vegetation_survey"
    DAMAGE_ASSESSMENT = "damage_assessment"


class MissionStatus(Enum):
    """Mission execution status."""
    PLANNED = "planned"
    QUEUED = "queued"
    PREFLIGHT_CHECK = "preflight_check"
    IN_PROGRESS = "in_progress"
    PAUSED = "paused"
    RETURNING = "returning"
    COMPLETED = "completed"
    ABORTED = "aborted"
    FAILED = "failed"


class CaptureType(Enum):
    """Types of data capture."""
    PHOTO = "photo"
    VIDEO = "video"
    THERMAL = "thermal"
    MULTISPECTRAL = "multispectral"
    LIDAR = "lidar"


@dataclass
class GeoPoint:
    """Geographic point with altitude."""
    latitude: float
    longitude: float
    altitude: float  # meters above ground level
    heading: Optional[float] = None  # degrees, 0 = north

    def to_dict(self) -> Dict[str, float]:
        return {
            "latitude": self.latitude,
            "longitude": self.longitude,
            "altitude": self.altitude,
            "heading": self.heading
        }

    def distance_to(self, other: 'GeoPoint') -> float:
        """Calculate approximate distance in meters."""
        # Simplified haversine
        lat1, lon1 = np.radians(self.latitude), np.radians(self.longitude)
        lat2, lon2 = np.radians(other.latitude), np.radians(other.longitude)

        dlat = lat2 - lat1
        dlon = lon2 - lon1

        a = np.sin(dlat/2)**2 + np.cos(lat1) * np.cos(lat2) * np.sin(dlon/2)**2
        c = 2 * np.arcsin(np.sqrt(a))

        r = 6371000  # Earth radius in meters
        horizontal = r * c
        vertical = abs(self.altitude - other.altitude)

        return np.sqrt(horizontal**2 + vertical**2)


@dataclass
class Waypoint:
    """Mission waypoint with actions."""
    waypoint_id: str
    position: GeoPoint
    action: str = "flyover"  # flyover, hover, capture, orbit
    hover_time: float = 0.0  # seconds
    capture_type: Optional[CaptureType] = None
    gimbal_pitch: float = -90.0  # degrees, -90 = straight down
    speed: float = 5.0  # m/s

    def to_dict(self) -> Dict[str, Any]:
        return {
            "waypoint_id": self.waypoint_id,
            "position": self.position.to_dict(),
            "action": self.action,
            "hover_time": self.hover_time,
            "capture_type": self.capture_type.value if self.capture_type else None,
            "gimbal_pitch": self.gimbal_pitch,
            "speed": self.speed
        }


@dataclass
class FlightPath:
    """Planned flight path for a mission."""
    path_id: str
    waypoints: List[Waypoint]
    total_distance: float = 0.0  # meters
    estimated_duration: float = 0.0  # seconds
    safety_buffer: float = 10.0  # meters from obstacles

    def to_dict(self) -> Dict[str, Any]:
        return {
            "path_id": self.path_id,
            "waypoints": [w.to_dict() for w in self.waypoints],
            "total_distance": self.total_distance,
            "estimated_duration": self.estimated_duration,
            "waypoint_count": len(self.waypoints)
        }


@dataclass
class CapturedData:
    """Data captured during mission."""
    capture_id: str
    capture_type: CaptureType
    timestamp: datetime
    position: GeoPoint
    file_path: Optional[str] = None
    file_size_bytes: int = 0
    metadata: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "capture_id": self.capture_id,
            "capture_type": self.capture_type.value,
            "timestamp": self.timestamp.isoformat(),
            "position": self.position.to_dict(),
            "file_path": self.file_path,
            "file_size_bytes": self.file_size_bytes,
            "metadata": self.metadata
        }


@dataclass
class DroneState:
    """Current state of a drone."""
    drone_id: str
    status: DroneStatus
    position: Optional[GeoPoint] = None
    battery_percent: float = 100.0
    signal_strength: float = 100.0
    speed: float = 0.0
    heading: float = 0.0
    gimbal_pitch: float = 0.0
    gps_satellites: int = 0
    wind_speed: float = 0.0
    temperature: float = 20.0
    last_update: datetime = field(default_factory=datetime.now)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "drone_id": self.drone_id,
            "status": self.status.value,
            "position": self.position.to_dict() if self.position else None,
            "battery_percent": self.battery_percent,
            "signal_strength": self.signal_strength,
            "speed": self.speed,
            "heading": self.heading,
            "gimbal_pitch": self.gimbal_pitch,
            "gps_satellites": self.gps_satellites,
            "wind_speed": self.wind_speed,
            "temperature": self.temperature,
            "last_update": self.last_update.isoformat()
        }


@dataclass
class Mission:
    """Drone inspection mission."""
    mission_id: str
    mission_type: MissionType
    station_id: str
    status: MissionStatus
    created_at: datetime
    scheduled_at: Optional[datetime] = None
    started_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None
    drone_id: Optional[str] = None
    flight_path: Optional[FlightPath] = None
    captured_data: List[CapturedData] = field(default_factory=list)
    home_position: Optional[GeoPoint] = None
    current_waypoint_index: int = 0
    progress_percent: float = 0.0
    notes: str = ""
    metadata: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "mission_id": self.mission_id,
            "mission_type": self.mission_type.value,
            "station_id": self.station_id,
            "status": self.status.value,
            "created_at": self.created_at.isoformat(),
            "scheduled_at": self.scheduled_at.isoformat() if self.scheduled_at else None,
            "started_at": self.started_at.isoformat() if self.started_at else None,
            "completed_at": self.completed_at.isoformat() if self.completed_at else None,
            "drone_id": self.drone_id,
            "flight_path": self.flight_path.to_dict() if self.flight_path else None,
            "captured_data_count": len(self.captured_data),
            "current_waypoint_index": self.current_waypoint_index,
            "progress_percent": self.progress_percent,
            "notes": self.notes
        }


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


class DroneController:
    """Simulated drone controller for testing."""

    def __init__(self, drone_id: str):
        self.drone_id = drone_id
        self.state = DroneState(
            drone_id=drone_id,
            status=DroneStatus.STANDBY,
            position=None,
            battery_percent=100.0
        )
        self._mission: Optional[Mission] = None
        self._flying = False
        self._flight_thread: Optional[threading.Thread] = None

    def preflight_check(self) -> Tuple[bool, List[str]]:
        """Perform preflight checks."""
        issues = []

        if self.state.battery_percent < 30:
            issues.append("Low battery")
        if self.state.gps_satellites < 6:
            issues.append("Insufficient GPS satellites")
        if self.state.wind_speed > 15:
            issues.append("Wind speed too high")
        if self.state.status == DroneStatus.MAINTENANCE:
            issues.append("Drone in maintenance mode")

        passed = len(issues) == 0
        return passed, issues

    def start_mission(self, mission: Mission) -> bool:
        """Start executing a mission."""
        passed, issues = self.preflight_check()
        if not passed:
            logger.error(f"Preflight check failed: {issues}")
            return False

        self._mission = mission
        self._mission.status = MissionStatus.IN_PROGRESS
        self._mission.started_at = datetime.now()
        self.state.status = DroneStatus.FLYING
        self._flying = True

        # Start simulated flight in background
        self._flight_thread = threading.Thread(target=self._simulate_flight)
        self._flight_thread.daemon = True
        self._flight_thread.start()

        return True

    def abort_mission(self) -> bool:
        """Abort current mission and return home."""
        if not self._mission:
            return False

        self._flying = False
        self._mission.status = MissionStatus.ABORTED
        self.state.status = DroneStatus.RETURNING

        return True

    def return_home(self) -> bool:
        """Command drone to return to home position."""
        self._flying = False
        self.state.status = DroneStatus.RETURNING
        return True

    def get_state(self) -> DroneState:
        """Get current drone state."""
        return self.state

    def _simulate_flight(self):
        """Simulate mission execution."""
        if not self._mission or not self._mission.flight_path:
            return

        waypoints = self._mission.flight_path.waypoints

        for i, waypoint in enumerate(waypoints):
            if not self._flying:
                break

            # Update position
            self.state.position = waypoint.position
            self.state.heading = waypoint.position.heading or 0
            self.state.gimbal_pitch = waypoint.gimbal_pitch

            # Update progress
            self._mission.current_waypoint_index = i
            self._mission.progress_percent = (i + 1) / len(waypoints) * 100

            # Simulate capture
            if waypoint.capture_type:
                capture = CapturedData(
                    capture_id=str(uuid.uuid4()),
                    capture_type=waypoint.capture_type,
                    timestamp=datetime.now(),
                    position=waypoint.position,
                    file_path=f"/captures/{self._mission.mission_id}/img_{i:04d}.jpg",
                    file_size_bytes=_rng.integers(500000, 2000000)
                )
                self._mission.captured_data.append(capture)

            # Drain battery
            self.state.battery_percent -= 0.5

            # Wait for next waypoint
            time.sleep(1.0)  # Accelerated simulation

        if self._flying:
            self._mission.status = MissionStatus.COMPLETED
            self._mission.completed_at = datetime.now()
            self.state.status = DroneStatus.RETURNING


class DroneIntegrationService:
    """Main service for drone integration."""

    def __init__(self):
        self.drones: Dict[str, DroneController] = {}
        self.missions: Dict[str, Mission] = {}
        self.flight_planner = FlightPathPlanner()

        # Register simulated drones
        self._register_simulated_drones()

        logger.info("Drone Integration Service initialized")

    def _register_simulated_drones(self):
        """Register simulated drones for testing."""
        for i in range(3):
            drone_id = f"drone_{i+1:03d}"
            self.drones[drone_id] = DroneController(drone_id)
            # Set simulated GPS
            self.drones[drone_id].state.gps_satellites = 12

    def register_drone(self, drone_id: str) -> DroneController:
        """Register a new drone."""
        if drone_id not in self.drones:
            self.drones[drone_id] = DroneController(drone_id)
        return self.drones[drone_id]

    def get_drone_state(self, drone_id: str) -> Optional[DroneState]:
        """Get current state of a drone."""
        controller = self.drones.get(drone_id)
        return controller.get_state() if controller else None

    def get_all_drones(self) -> List[DroneState]:
        """Get state of all registered drones."""
        return [c.get_state() for c in self.drones.values()]

    def create_mission(self,
                       mission_type: MissionType,
                       station_id: str,
                       station_location: GeoPoint,
                       drone_id: Optional[str] = None,
                       scheduled_at: Optional[datetime] = None,
                       custom_params: Optional[Dict] = None) -> Mission:
        """Create a new inspection mission."""
        mission_id = str(uuid.uuid4())

        # Plan flight path
        flight_path = self.flight_planner.plan_mission(
            mission_type, station_location, custom_params
        )

        # Find available drone if not specified
        if not drone_id:
            drone_id = self._find_available_drone()

        mission = Mission(
            mission_id=mission_id,
            mission_type=mission_type,
            station_id=station_id,
            status=MissionStatus.PLANNED,
            created_at=datetime.now(),
            scheduled_at=scheduled_at,
            drone_id=drone_id,
            flight_path=flight_path,
            home_position=station_location
        )

        self.missions[mission_id] = mission
        logger.info(f"Created mission {mission_id} for station {station_id}")

        return mission

    def start_mission(self, mission_id: str) -> bool:
        """Start executing a mission."""
        mission = self.missions.get(mission_id)
        if not mission:
            logger.error(f"Mission {mission_id} not found")
            return False

        if not mission.drone_id:
            mission.drone_id = self._find_available_drone()
            if not mission.drone_id:
                logger.error("No available drones")
                return False

        controller = self.drones.get(mission.drone_id)
        if not controller:
            logger.error(f"Drone {mission.drone_id} not found")
            return False

        success = controller.start_mission(mission)
        if success:
            mission.status = MissionStatus.IN_PROGRESS
            logger.info(f"Started mission {mission_id}")

        return success

    def abort_mission(self, mission_id: str) -> bool:
        """Abort a mission."""
        mission = self.missions.get(mission_id)
        if not mission or not mission.drone_id:
            return False

        controller = self.drones.get(mission.drone_id)
        if controller:
            controller.abort_mission()
            mission.status = MissionStatus.ABORTED
            logger.info(f"Aborted mission {mission_id}")
            return True

        return False

    def get_mission(self, mission_id: str) -> Optional[Mission]:
        """Get mission details."""
        return self.missions.get(mission_id)

    def get_mission_status(self, mission_id: str) -> Optional[Dict[str, Any]]:
        """Get current status of a mission."""
        mission = self.missions.get(mission_id)
        if not mission:
            return None

        return {
            "mission_id": mission_id,
            "status": mission.status.value,
            "progress_percent": mission.progress_percent,
            "current_waypoint": mission.current_waypoint_index,
            "total_waypoints": len(mission.flight_path.waypoints) if mission.flight_path else 0,
            "captures_taken": len(mission.captured_data)
        }

    def get_captured_data(self, mission_id: str) -> List[CapturedData]:
        """Get data captured during a mission."""
        mission = self.missions.get(mission_id)
        return mission.captured_data if mission else []

    def _find_available_drone(self) -> Optional[str]:
        """Find an available drone."""
        for drone_id, controller in self.drones.items():
            if controller.state.status == DroneStatus.STANDBY:
                if controller.state.battery_percent >= 50:
                    return drone_id
        return None

    def get_statistics(self) -> Dict[str, Any]:
        """Get service statistics."""
        mission_statuses = {}
        for mission in self.missions.values():
            status = mission.status.value
            mission_statuses[status] = mission_statuses.get(status, 0) + 1

        total_captures = sum(
            len(m.captured_data) for m in self.missions.values()
        )

        drone_statuses = {}
        for drone in self.drones.values():
            status = drone.state.status.value
            drone_statuses[status] = drone_statuses.get(status, 0) + 1

        return {
            "total_drones": len(self.drones),
            "drone_statuses": drone_statuses,
            "total_missions": len(self.missions),
            "mission_statuses": mission_statuses,
            "total_captures": total_captures
        }


# Singleton instance with thread-safe initialization
_drone_service: Optional[DroneIntegrationService] = None
_drone_service_lock = threading.Lock()


def get_drone_service() -> DroneIntegrationService:
    """Get the singleton drone integration service instance (thread-safe)."""
    global _drone_service
    if _drone_service is None:
        with _drone_service_lock:
            if _drone_service is None:  # Double-check locking
                _drone_service = DroneIntegrationService()
    return _drone_service
