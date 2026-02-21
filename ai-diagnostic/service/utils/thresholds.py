"""
Threshold constants for equipment health monitoring.

Organized by component type for easy access and maintenance.
"""

from dataclasses import dataclass
from typing import Tuple


@dataclass(frozen=True)
class ThresholdLevels:
    """Generic threshold levels (healthy, warning, critical)."""
    healthy: float
    warning: float
    critical: float

    def get_health_factor(self, value: float, higher_is_worse: bool = True) -> float:
        """
        Get health factor (0.0-1.0) based on value and thresholds.

        Args:
            value: Current metric value
            higher_is_worse: If True, higher values = worse health

        Returns:
            Health factor where 1.0 = healthy, 0.2 = critical
        """
        if higher_is_worse:
            if value >= self.critical:
                return 0.2
            elif value >= self.warning:
                return 0.5
            elif value >= self.healthy:
                return 0.8
            return 1.0
        else:
            if value <= self.critical:
                return 0.2
            elif value <= self.warning:
                return 0.5
            elif value <= self.healthy:
                return 0.8
            return 1.0


class FanThresholds:
    """Fan RPM thresholds."""
    HEALTHY_RPM_MIN = 2000
    WARNING_RPM_MIN = 1500
    CRITICAL_RPM_MIN = 1000
    VARIATION_THRESHOLD = 0.15  # 15% variation is concerning
    DEGRADATION_SLOPE = -10  # RPM/hour decline threshold

    RPM = ThresholdLevels(
        healthy=HEALTHY_RPM_MIN,
        warning=WARNING_RPM_MIN,
        critical=CRITICAL_RPM_MIN,
    )


class TemperatureThresholds:
    """Temperature thresholds in Celsius."""
    HEALTHY_MAX = 55
    WARNING_MAX = 65
    CRITICAL_MAX = 75

    AMBIENT = ThresholdLevels(
        healthy=HEALTHY_MAX,
        warning=WARNING_MAX,
        critical=CRITICAL_MAX,
    )


class BatteryThresholds:
    """Battery health thresholds."""
    # State of Charge (%)
    SOC_HEALTHY_MIN = 80
    SOC_WARNING_MIN = 50
    SOC_CRITICAL_MIN = 20

    # Depth of Discharge (%)
    DOD_HEALTHY_MAX = 50
    DOD_WARNING_MAX = 70
    DOD_CRITICAL_MAX = 85

    # Temperature (Celsius)
    TEMP_HEALTHY_MAX = 35
    TEMP_WARNING_MAX = 45
    TEMP_CRITICAL_MAX = 55

    # Cycle count
    CYCLE_HEALTHY_MAX = 500
    CYCLE_WARNING_MAX = 800
    CYCLE_CRITICAL_MAX = 1000

    # Capacity degradation
    CAPACITY_DEGRADATION_THRESHOLD = 0.10  # 10% capacity loss

    SOC = ThresholdLevels(
        healthy=SOC_HEALTHY_MIN,
        warning=SOC_WARNING_MIN,
        critical=SOC_CRITICAL_MIN,
    )

    DOD = ThresholdLevels(
        healthy=DOD_HEALTHY_MAX,
        warning=DOD_WARNING_MAX,
        critical=DOD_CRITICAL_MAX,
    )

    TEMP = ThresholdLevels(
        healthy=TEMP_HEALTHY_MAX,
        warning=TEMP_WARNING_MAX,
        critical=TEMP_CRITICAL_MAX,
    )


class FiberThresholds:
    """Fiber optic transport thresholds."""
    # RX Power (dBm) - lower is worse
    RX_POWER_HEALTHY_MIN = -20
    RX_POWER_WARNING_MIN = -25
    RX_POWER_CRITICAL_MIN = -30

    # TX Power (dBm) - lower is worse
    TX_POWER_HEALTHY_MIN = -5
    TX_POWER_WARNING_MIN = -8
    TX_POWER_CRITICAL_MIN = -10

    # Bit Error Rate - higher is worse
    BER_HEALTHY_MAX = 1e-12
    BER_WARNING_MAX = 1e-9
    BER_CRITICAL_MAX = 1e-6

    # Optical Signal-to-Noise Ratio (dB) - lower is worse
    OSNR_HEALTHY_MIN = 25
    OSNR_WARNING_MIN = 20
    OSNR_CRITICAL_MIN = 15

    RX_POWER = ThresholdLevels(
        healthy=RX_POWER_HEALTHY_MIN,
        warning=RX_POWER_WARNING_MIN,
        critical=RX_POWER_CRITICAL_MIN,
    )

    TX_POWER = ThresholdLevels(
        healthy=TX_POWER_HEALTHY_MIN,
        warning=TX_POWER_WARNING_MIN,
        critical=TX_POWER_CRITICAL_MIN,
    )

    OSNR = ThresholdLevels(
        healthy=OSNR_HEALTHY_MIN,
        warning=OSNR_WARNING_MIN,
        critical=OSNR_CRITICAL_MIN,
    )


class SignalThresholds:
    """RF signal thresholds."""
    # RSRP (dBm) - lower is worse
    RSRP_POOR = -110
    RSRP_FAIR = -100
    RSRP_GOOD = -90

    # SINR (dB) - lower is worse
    SINR_POOR = 0
    SINR_FAIR = 5
    SINR_GOOD = 10

    # Interference (dBm) - higher is worse
    INTERFERENCE_HIGH = -90
    INTERFERENCE_MEDIUM = -100
    INTERFERENCE_LOW = -110


class PowerThresholds:
    """Power supply thresholds."""
    VOLTAGE_TOLERANCE = 0.05  # 5% tolerance from nominal
    NOMINAL_VOLTAGE = 48.0  # Volts (typical for telecom)


class LoadThresholds:
    """Traffic/load thresholds."""
    HIGH_LOAD = 80.0  # % PRB utilization
    LOW_LOAD = 30.0   # % PRB utilization
    IMBALANCE = 20.0  # % difference between cells
