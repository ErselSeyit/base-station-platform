"""
Vision Service - Computer Vision for Base Station Inspection

Quick Win #1: LED Status Reading
- Reads equipment LED indicators from camera images
- Classifies status: OK (green), Warning (yellow/amber), Error (red), Off (no light)

Future: Antenna damage detection, connector corrosion, thermal anomaly detection
"""

import logging
import base64
import io
from typing import Optional, List, Dict, Any, Tuple
from dataclasses import dataclass
from enum import Enum
from datetime import datetime

import numpy as np

# Lazy imports for optional CV dependencies
cv2 = None
Image = None

logger = logging.getLogger(__name__)


def _ensure_cv2():
    """Lazy load OpenCV."""
    global cv2
    if cv2 is None:
        try:
            import cv2 as _cv2
            cv2 = _cv2
        except ImportError:
            raise ImportError("opencv-python-headless is required for vision features")
    return cv2


def _ensure_pil():
    """Lazy load PIL."""
    global Image
    if Image is None:
        try:
            from PIL import Image as _Image
            Image = _Image
        except ImportError:
            raise ImportError("pillow is required for vision features")
    return Image


class LEDStatus(Enum):
    """LED indicator status classification."""
    OK = "ok"              # Green - normal operation
    WARNING = "warning"    # Yellow/Amber - attention needed
    ERROR = "error"        # Red - fault condition
    OFF = "off"            # No light - powered off or failed
    UNKNOWN = "unknown"    # Cannot determine


@dataclass
class LEDDetection:
    """Result of LED detection."""
    status: LEDStatus
    confidence: float
    color_rgb: Tuple[int, int, int]
    position: Optional[Tuple[int, int, int, int]] = None  # x, y, w, h bounding box
    brightness: float = 0.0


@dataclass
class VisionAnalysisResult:
    """Complete vision analysis result for a site image."""
    station_id: str
    timestamp: datetime
    image_type: str  # "led_panel", "antenna", "equipment", "thermal"
    led_detections: List[LEDDetection]
    overall_status: str
    anomalies: List[Dict[str, Any]]
    processing_time_ms: float
    model_version: str = "1.0.0"


class VisionService:
    """
    Computer Vision service for base station visual inspection.

    Features:
    - LED status reading from equipment panel images
    - Color-based classification with HSV analysis
    - Supports base64-encoded images or file paths
    """

    # HSV color ranges for LED classification
    # Format: (H_min, S_min, V_min), (H_max, S_max, V_max)
    COLOR_RANGES = {
        "green": ((35, 100, 100), (85, 255, 255)),    # Green LED
        "yellow": ((20, 100, 100), (35, 255, 255)),   # Yellow/Amber LED
        "red": ((0, 100, 100), (10, 255, 255)),       # Red LED (low hue)
        "red2": ((170, 100, 100), (180, 255, 255)),   # Red LED (high hue, wraps around)
        "blue": ((100, 100, 100), (130, 255, 255)),   # Blue LED (info/status)
    }

    # Minimum brightness threshold to consider LED as "on"
    MIN_BRIGHTNESS = 50

    # Minimum area (pixels) for valid LED detection
    MIN_LED_AREA = 20
    MAX_LED_AREA = 5000

    def __init__(self):
        self.model_version = "1.0.0"
        logger.info("VisionService initialized (model_version=%s)", self.model_version)

    def analyze_led_panel(
        self,
        image_data: bytes,
        station_id: str,
        expected_leds: int = 0
    ) -> VisionAnalysisResult:
        """
        Analyze LED panel image to detect and classify LED status indicators.

        Args:
            image_data: Raw image bytes (JPEG, PNG)
            station_id: Base station identifier
            expected_leds: Expected number of LEDs (0 = auto-detect)

        Returns:
            VisionAnalysisResult with LED detections and overall status
        """
        import time
        start_time = time.time()

        cv2 = _ensure_cv2()

        # Decode image
        nparr = np.frombuffer(image_data, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

        if img is None:
            raise ValueError("Failed to decode image data")

        # Convert to HSV for color analysis
        hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)

        # Detect LEDs
        led_detections = self._detect_leds(img, hsv)

        # Determine overall status
        overall_status = self._determine_overall_status(led_detections)

        # Check for anomalies
        anomalies = self._check_anomalies(led_detections, expected_leds)

        processing_time = (time.time() - start_time) * 1000

        return VisionAnalysisResult(
            station_id=station_id,
            timestamp=datetime.utcnow(),
            image_type="led_panel",
            led_detections=led_detections,
            overall_status=overall_status,
            anomalies=anomalies,
            processing_time_ms=processing_time,
            model_version=self.model_version
        )

    def _detect_leds(self, img: np.ndarray, hsv: np.ndarray) -> List[LEDDetection]:
        """Detect and classify LED indicators in image."""
        cv2 = _ensure_cv2()
        detections = []

        # Find bright regions (potential LEDs)
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        _, bright_mask = cv2.threshold(gray, self.MIN_BRIGHTNESS, 255, cv2.THRESH_BINARY)

        # Find contours of bright regions
        contours, _ = cv2.findContours(bright_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

        for contour in contours:
            area = cv2.contourArea(contour)

            # Filter by area
            if area < self.MIN_LED_AREA or area > self.MAX_LED_AREA:
                continue

            # Get bounding box
            x, y, w, h = cv2.boundingRect(contour)

            # Extract LED region
            led_region_hsv = hsv[y:y+h, x:x+w]
            led_region_bgr = img[y:y+h, x:x+w]

            # Classify LED color
            status, confidence, dominant_color = self._classify_led_color(led_region_hsv, led_region_bgr)

            # Calculate brightness
            brightness = float(np.mean(gray[y:y+h, x:x+w]))

            detection = LEDDetection(
                status=status,
                confidence=confidence,
                color_rgb=dominant_color,
                position=(x, y, w, h),
                brightness=brightness
            )
            detections.append(detection)

        # Sort by position (left to right, top to bottom)
        detections.sort(key=lambda d: (d.position[1] // 20, d.position[0]) if d.position else (0, 0))

        return detections

    def _classify_led_color(
        self,
        hsv_region: np.ndarray,
        bgr_region: np.ndarray
    ) -> Tuple[LEDStatus, float, Tuple[int, int, int]]:
        """Classify LED color from HSV region."""
        cv2 = _ensure_cv2()

        # Get dominant color
        mean_bgr = np.mean(bgr_region, axis=(0, 1)).astype(int)
        dominant_color = (int(mean_bgr[2]), int(mean_bgr[1]), int(mean_bgr[0]))  # BGR to RGB

        # Count pixels matching each color range
        color_scores = {}
        total_pixels = hsv_region.shape[0] * hsv_region.shape[1]

        for color_name, (lower, upper) in self.COLOR_RANGES.items():
            mask = cv2.inRange(hsv_region, np.array(lower), np.array(upper))
            matching_pixels = np.count_nonzero(mask)
            color_scores[color_name] = matching_pixels / total_pixels

        # Combine red ranges
        if "red" in color_scores and "red2" in color_scores:
            color_scores["red"] = color_scores["red"] + color_scores["red2"]
            del color_scores["red2"]

        # Find dominant color
        if not color_scores:
            return LEDStatus.UNKNOWN, 0.0, dominant_color

        best_color = max(color_scores, key=color_scores.get)
        confidence = color_scores[best_color]

        # Map color to status
        if confidence < 0.3:
            return LEDStatus.UNKNOWN, confidence, dominant_color

        status_map = {
            "green": LEDStatus.OK,
            "yellow": LEDStatus.WARNING,
            "red": LEDStatus.ERROR,
            "blue": LEDStatus.OK,  # Blue often indicates active/ready
        }

        status = status_map.get(best_color, LEDStatus.UNKNOWN)
        return status, confidence, dominant_color

    def _determine_overall_status(self, detections: List[LEDDetection]) -> str:
        """Determine overall equipment status from LED detections."""
        if not detections:
            return "unknown"

        # Priority: ERROR > WARNING > UNKNOWN > OK
        has_error = any(d.status == LEDStatus.ERROR for d in detections)
        has_warning = any(d.status == LEDStatus.WARNING for d in detections)
        has_unknown = any(d.status == LEDStatus.UNKNOWN for d in detections)

        if has_error:
            return "error"
        elif has_warning:
            return "warning"
        elif has_unknown:
            return "unknown"
        else:
            return "ok"

    def _check_anomalies(
        self,
        detections: List[LEDDetection],
        expected_leds: int
    ) -> List[Dict[str, Any]]:
        """Check for anomalies in LED detections."""
        anomalies = []

        # Check expected LED count
        if expected_leds > 0 and len(detections) != expected_leds:
            anomalies.append({
                "type": "led_count_mismatch",
                "severity": "warning",
                "message": f"Expected {expected_leds} LEDs, detected {len(detections)}",
                "expected": expected_leds,
                "actual": len(detections)
            })

        # Check for error LEDs
        error_leds = [d for d in detections if d.status == LEDStatus.ERROR]
        if error_leds:
            anomalies.append({
                "type": "error_led_detected",
                "severity": "critical",
                "message": f"{len(error_leds)} error LED(s) detected",
                "count": len(error_leds),
                "positions": [d.position for d in error_leds]
            })

        # Check for all LEDs off (possible power failure)
        if detections and all(d.status == LEDStatus.OFF for d in detections):
            anomalies.append({
                "type": "all_leds_off",
                "severity": "critical",
                "message": "All LEDs appear to be off - possible power failure"
            })

        # Check for low brightness (dim LEDs)
        dim_leds = [d for d in detections if d.brightness < 100 and d.status != LEDStatus.OFF]
        if dim_leds:
            anomalies.append({
                "type": "dim_leds",
                "severity": "warning",
                "message": f"{len(dim_leds)} LED(s) appear dim",
                "count": len(dim_leds)
            })

        return anomalies

    def analyze_from_base64(
        self,
        base64_image: str,
        station_id: str,
        expected_leds: int = 0
    ) -> VisionAnalysisResult:
        """Analyze LED panel from base64-encoded image."""
        # Remove data URL prefix if present
        if "," in base64_image:
            base64_image = base64_image.split(",")[1]

        image_data = base64.b64decode(base64_image)
        return self.analyze_led_panel(image_data, station_id, expected_leds)

    def analyze_from_file(
        self,
        file_path: str,
        station_id: str,
        expected_leds: int = 0
    ) -> VisionAnalysisResult:
        """Analyze LED panel from image file."""
        with open(file_path, "rb") as f:
            image_data = f.read()
        return self.analyze_led_panel(image_data, station_id, expected_leds)

    def to_dict(self, result: VisionAnalysisResult) -> Dict[str, Any]:
        """Convert VisionAnalysisResult to dictionary for JSON serialization."""
        return {
            "station_id": result.station_id,
            "timestamp": result.timestamp.isoformat(),
            "image_type": result.image_type,
            "led_detections": [
                {
                    "status": d.status.value,
                    "confidence": round(d.confidence, 3),
                    "color_rgb": d.color_rgb,
                    "position": d.position,
                    "brightness": round(d.brightness, 1)
                }
                for d in result.led_detections
            ],
            "overall_status": result.overall_status,
            "anomalies": result.anomalies,
            "processing_time_ms": round(result.processing_time_ms, 2),
            "model_version": result.model_version,
            "summary": {
                "total_leds": len(result.led_detections),
                "ok_count": sum(1 for d in result.led_detections if d.status == LEDStatus.OK),
                "warning_count": sum(1 for d in result.led_detections if d.status == LEDStatus.WARNING),
                "error_count": sum(1 for d in result.led_detections if d.status == LEDStatus.ERROR),
                "off_count": sum(1 for d in result.led_detections if d.status == LEDStatus.OFF),
                "anomaly_count": len(result.anomalies)
            }
        }


# Singleton instance
_vision_service: Optional[VisionService] = None


def get_vision_service() -> VisionService:
    """Get or create singleton VisionService instance."""
    global _vision_service
    if _vision_service is None:
        _vision_service = VisionService()
    return _vision_service
