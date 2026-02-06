/**
 * Metric Evaluation Utilities
 *
 * Single source of truth for metric threshold evaluation functions and status types.
 * Used by metricsConfig.ts and all components that need to evaluate metric health.
 */

// ============================================================================
// Status Types
// ============================================================================

/**
 * Health status for metrics where semantic meaning matters.
 * Used in dashboard, charts, and general health displays.
 */
export type HealthStatus = 'healthy' | 'warning' | 'critical'

/**
 * Metric status for pass/fail displays (e.g., SSV compliance).
 * Semantically equivalent to HealthStatus but with different labels.
 */
export type MetricStatus = 'pass' | 'warning' | 'fail'

// ============================================================================
// Threshold Evaluation Functions
// ============================================================================

/**
 * Evaluate a metric where LOWER values are better.
 * Examples: CPU usage, temperature, latency
 *
 * @param value - The metric value to evaluate
 * @param goodThreshold - Values at or below this are healthy/pass
 * @param warnThreshold - Values above good but at or below this are warning
 * @returns HealthStatus based on thresholds
 *
 * @example
 * evalLower(65, 70, 85) // 'healthy' - CPU at 65% is good
 * evalLower(75, 70, 85) // 'warning' - CPU at 75% is concerning
 * evalLower(90, 70, 85) // 'critical' - CPU at 90% is critical
 */
export function evalLower(value: number, goodThreshold: number, warnThreshold: number): HealthStatus {
  if (value <= goodThreshold) return 'healthy'
  if (value <= warnThreshold) return 'warning'
  return 'critical'
}

/**
 * Evaluate a metric where HIGHER values are better.
 * Examples: throughput, signal strength, SINR
 *
 * @param value - The metric value to evaluate
 * @param goodThreshold - Values at or above this are healthy/pass
 * @param warnThreshold - Values below good but at or above this are warning
 * @returns HealthStatus based on thresholds
 *
 * @example
 * evalHigher(1200, 1000, 500) // 'healthy' - 1200 Mbps is good
 * evalHigher(700, 1000, 500)  // 'warning' - 700 Mbps is concerning
 * evalHigher(300, 1000, 500)  // 'critical' - 300 Mbps is critical
 */
export function evalHigher(value: number, goodThreshold: number, warnThreshold: number): HealthStatus {
  if (value >= goodThreshold) return 'healthy'
  if (value >= warnThreshold) return 'warning'
  return 'critical'
}

// ============================================================================
// Status Conversion Utilities
// ============================================================================

/**
 * Convert HealthStatus to MetricStatus.
 * Useful when displaying health as pass/fail.
 */
export function healthToMetricStatus(health: HealthStatus): MetricStatus {
  switch (health) {
    case 'healthy':
      return 'pass'
    case 'warning':
      return 'warning'
    case 'critical':
      return 'fail'
  }
}

/**
 * Convert MetricStatus to HealthStatus.
 * Useful when integrating pass/fail into health displays.
 */
export function metricToHealthStatus(metric: MetricStatus): HealthStatus {
  switch (metric) {
    case 'pass':
      return 'healthy'
    case 'warning':
      return 'warning'
    case 'fail':
      return 'critical'
  }
}

// ============================================================================
// Aggregate Status Utilities
// ============================================================================

/**
 * Determine the worst status from an array of statuses.
 * Critical > Warning > Healthy
 */
export function getWorstHealthStatus(statuses: HealthStatus[]): HealthStatus {
  if (statuses.includes('critical')) return 'critical'
  if (statuses.includes('warning')) return 'warning'
  return 'healthy'
}

/**
 * Determine the worst status from an array of metric statuses.
 * Fail > Warning > Pass
 */
export function getWorstMetricStatus(statuses: MetricStatus[]): MetricStatus {
  if (statuses.includes('fail')) return 'fail'
  if (statuses.includes('warning')) return 'warning'
  return 'pass'
}

/**
 * Count statuses in an array.
 */
export function countHealthStatuses(statuses: HealthStatus[]): {
  healthy: number
  warning: number
  critical: number
} {
  return {
    healthy: statuses.filter(s => s === 'healthy').length,
    warning: statuses.filter(s => s === 'warning').length,
    critical: statuses.filter(s => s === 'critical').length,
  }
}

/**
 * Count metric statuses in an array.
 */
export function countMetricStatuses(statuses: MetricStatus[]): {
  pass: number
  warning: number
  fail: number
} {
  return {
    pass: statuses.filter(s => s === 'pass').length,
    warning: statuses.filter(s => s === 'warning').length,
    fail: statuses.filter(s => s === 'fail').length,
  }
}

// ============================================================================
// Power Dashboard Status Evaluation
// ============================================================================

/** Thresholds for power-related metrics */
export const POWER_THRESHOLDS = {
  POWER_HEALTHY_RATIO: 0.7,
  POWER_WARNING_RATIO: 0.9,
  DEFAULT_MAX_POWER_KW: 5,
  BATTERY_HEALTHY_SOC: 50,
  BATTERY_WARNING_SOC: 20,
  BATTERY_FULL_SOC: 95,
  BATTERY_CHARGING_SOC: 50,
  TEMP_HEALTHY_MAX: 65,
  TEMP_WARNING_MAX: 80,
  FAN_HEALTHY_MIN: 2000,
  FAN_HEALTHY_MAX: 5000,
  FAN_WARNING_MIN: 1000,
  FAN_HIGH_SPEED: 3000,
  FAN_ACTIVE_SPEED: 1500,
} as const

/** Evaluate power consumption status based on ratio to max power */
export function getPowerStatus(
  consumption: number,
  maxPower: number = POWER_THRESHOLDS.DEFAULT_MAX_POWER_KW
): HealthStatus {
  const ratio = consumption / maxPower
  if (ratio <= POWER_THRESHOLDS.POWER_HEALTHY_RATIO) return 'healthy'
  if (ratio <= POWER_THRESHOLDS.POWER_WARNING_RATIO) return 'warning'
  return 'critical'
}

/** Evaluate battery state of charge status */
export function getBatteryStatus(soc: number): HealthStatus {
  if (soc >= POWER_THRESHOLDS.BATTERY_HEALTHY_SOC) return 'healthy'
  if (soc >= POWER_THRESHOLDS.BATTERY_WARNING_SOC) return 'warning'
  return 'critical'
}

/** Evaluate temperature status */
export function getTempStatus(temp: number): HealthStatus {
  if (temp <= POWER_THRESHOLDS.TEMP_HEALTHY_MAX) return 'healthy'
  if (temp <= POWER_THRESHOLDS.TEMP_WARNING_MAX) return 'warning'
  return 'critical'
}

/** Evaluate fan speed status (must be within healthy range) */
export function getFanStatus(speed: number): HealthStatus {
  if (speed >= POWER_THRESHOLDS.FAN_HEALTHY_MIN && speed <= POWER_THRESHOLDS.FAN_HEALTHY_MAX) return 'healthy'
  if (speed >= POWER_THRESHOLDS.FAN_WARNING_MIN) return 'warning'
  return 'critical'
}

/** Get human-readable charging status label */
export function getChargingStatusLabel(avgBattery: number): string {
  if (avgBattery >= POWER_THRESHOLDS.BATTERY_FULL_SOC) return 'Full'
  if (avgBattery >= POWER_THRESHOLDS.BATTERY_CHARGING_SOC) return 'Charging'
  return 'Low'
}

/** Get human-readable cooling status label */
export function getCoolingStatusLabel(avgFanSpeed: number): string {
  if (avgFanSpeed >= POWER_THRESHOLDS.FAN_HIGH_SPEED) return 'High'
  if (avgFanSpeed >= POWER_THRESHOLDS.FAN_ACTIVE_SPEED) return 'Active'
  return 'Idle'
}

// ============================================================================
// 5G Dashboard Status Evaluation
// ============================================================================

/** Thresholds for 5G-related metrics */
export const FIVEG_THRESHOLDS = {
  RSRP_HEALTHY: -85,
  RSRP_WARNING: -100,
  SINR_HEALTHY: 10,
  SINR_WARNING: 5,
  LATENCY_HEALTHY: 15,
  LATENCY_WARNING: 30,
  THROUGHPUT_HEALTHY_RATIO: 0.5,
  THROUGHPUT_WARNING_RATIO: 0.25,
  HEALTH_RATIO_HEALTHY: 0.9,
  HEALTH_RATIO_WARNING: 0.7,
} as const

/** Evaluate RSRP signal strength status (higher is better) */
export function getSignalStatus(rsrp: number): HealthStatus {
  if (rsrp >= FIVEG_THRESHOLDS.RSRP_HEALTHY) return 'healthy'
  if (rsrp >= FIVEG_THRESHOLDS.RSRP_WARNING) return 'warning'
  return 'critical'
}

/** Evaluate SINR status (higher is better) */
export function getSinrStatus(sinr: number): HealthStatus {
  if (sinr >= FIVEG_THRESHOLDS.SINR_HEALTHY) return 'healthy'
  if (sinr >= FIVEG_THRESHOLDS.SINR_WARNING) return 'warning'
  return 'critical'
}

/** Evaluate latency status (lower is better) */
export function getLatencyStatus(latency: number): HealthStatus {
  if (latency <= FIVEG_THRESHOLDS.LATENCY_HEALTHY) return 'healthy'
  if (latency <= FIVEG_THRESHOLDS.LATENCY_WARNING) return 'warning'
  return 'critical'
}

/** Evaluate throughput status based on ratio to max */
export function getThroughputStatus(value: number, max: number): HealthStatus {
  const ratio = value / max
  if (ratio >= FIVEG_THRESHOLDS.THROUGHPUT_HEALTHY_RATIO) return 'healthy'
  if (ratio >= FIVEG_THRESHOLDS.THROUGHPUT_WARNING_RATIO) return 'warning'
  return 'critical'
}

/** Evaluate health ratio status (0-1 range) */
export function getHealthRatioStatus(healthRatio: number): HealthStatus {
  if (healthRatio >= FIVEG_THRESHOLDS.HEALTH_RATIO_HEALTHY) return 'healthy'
  if (healthRatio >= FIVEG_THRESHOLDS.HEALTH_RATIO_WARNING) return 'warning'
  return 'critical'
}
