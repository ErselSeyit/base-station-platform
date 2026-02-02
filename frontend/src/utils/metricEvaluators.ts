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
