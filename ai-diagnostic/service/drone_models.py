"""
Data models for drone-based site inspection.

The drone/mission/capture enums and the geo and mission value types (GeoPoint
with haversine distance, Waypoint, FlightPath, CapturedData, DroneState,
Mission). Extracted from drone_integration.py so the planner and service can
share them without a circular import.
"""

import uuid
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any, Dict, List, Optional

import numpy as np


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
    heading: Optional[float] = None  # Heading in degrees (north is 0)

    def to_dict(self) -> Dict[str, Optional[float]]:
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
