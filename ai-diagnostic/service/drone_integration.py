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

import logging
import threading
import time
import uuid
from datetime import datetime
from typing import Any, Dict, List, Optional, Tuple

logger = logging.getLogger(__name__)

# Shared RNG for reproducibility
from .utils.rng import get_rng
_rng = get_rng()


# Data models extracted to service/drone_models.py; re-exported for importers.
from .drone_models import (  # noqa: F401
    DroneStatus, MissionType, MissionStatus, CaptureType,
    GeoPoint, Waypoint, FlightPath, CapturedData, DroneState, Mission,
)


# Flight-path planner extracted to service/flight_path_planner.py; re-exported.
from .flight_path_planner import FlightPathPlanner  # noqa: F401


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
                    file_size_bytes=int(_rng.integers(500000, 2000000))
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
from .utils.singleton import singleton_factory
get_drone_service = singleton_factory(DroneIntegrationService)
