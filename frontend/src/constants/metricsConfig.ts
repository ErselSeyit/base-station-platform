/**
 * Metrics Configuration
 *
 * SINGLE SOURCE OF TRUTH for all metric types including thresholds,
 * display settings, and status evaluation functions.
 *
 * This file consolidates configurations previously scattered across:
 * - metricsConfig.ts (original)
 * - useDashboardData.ts (METRICS_CONFIG)
 * - NR5GQuickStatus.tsx (SSV_CONFIG)
 * - NR5GMetricsCard.tsx (SSV_THRESHOLDS)
 */

import { CHART_COLORS } from './colors'
import {
  type HealthStatus,
  type MetricStatus,
  evalLower,
  evalHigher,
  healthToMetricStatus,
} from '../utils/metricEvaluators'

// Re-export types and functions for convenience
export type { HealthStatus, MetricStatus } from '../utils/metricEvaluators'
export {
  evalLower,
  evalHigher,
  healthToMetricStatus,
  getWorstHealthStatus,
  getWorstMetricStatus,
  countHealthStatuses,
  countMetricStatuses,
} from '../utils/metricEvaluators'

// ============================================================================
// Types
// ============================================================================

export type MetricCategory = 'system' | '5g-n78' | '5g-n28' | 'quality'

export interface MetricConfig {
  // Display
  label: string
  fullLabel: string
  description: string
  unit: string
  category: MetricCategory

  // Chart configuration
  color: string
  domain: [number, number]

  // Status evaluation
  getStatus: (value: number) => HealthStatus
  getMetricStatus: (value: number) => MetricStatus

  // Threshold info for display
  thresholds: {
    good: string
    warn: string
    critical: string
  }
  passThreshold: string // e.g., "≥1000 Mbps" or "≤15 ms"

  // Dashboard-specific
  showProgress?: boolean
  progressMax?: number

  // 5G SSV specific
  band?: string // e.g., "n78 (3.5GHz)"
  bandShort?: string // e.g., "3.5G"

  // Value formatting
  format: (value: number) => string
}

// ============================================================================
// Metrics Configuration
// ============================================================================

export const METRICS_CONFIG: Record<string, MetricConfig> = {
  // -------------------------------------------------------------------------
  // System Performance
  // -------------------------------------------------------------------------
  CPU_USAGE: {
    label: 'CPU',
    fullLabel: 'CPU Usage',
    description: 'Percentage of CPU capacity being used. High values indicate heavy processing load.',
    unit: '%',
    category: 'system',
    color: CHART_COLORS.cpuUsage,
    domain: [0, 100],
    getStatus: (v) => evalLower(v, 70, 85),
    getMetricStatus: (v) => healthToMetricStatus(evalLower(v, 70, 85)),
    thresholds: { good: '< 70%', warn: '70-85%', critical: '> 85%' },
    passThreshold: '≤70%',
    showProgress: true,
    progressMax: 100,
    format: (v) => v.toFixed(1),
  },

  MEMORY_USAGE: {
    label: 'Memory',
    fullLabel: 'Memory Usage',
    description: 'Percentage of available memory in use. High usage may cause performance degradation.',
    unit: '%',
    category: 'system',
    color: CHART_COLORS.memoryUsage,
    domain: [0, 100],
    getStatus: (v) => evalLower(v, 75, 90),
    getMetricStatus: (v) => healthToMetricStatus(evalLower(v, 75, 90)),
    thresholds: { good: '< 75%', warn: '75-90%', critical: '> 90%' },
    passThreshold: '≤75%',
    showProgress: true,
    progressMax: 100,
    format: (v) => v.toFixed(1),
  },

  TEMPERATURE: {
    label: 'Temperature',
    fullLabel: 'System Temperature',
    description: 'Internal hardware temperature. Excessive heat can damage components and reduce lifespan.',
    unit: '°C',
    category: 'system',
    color: CHART_COLORS.temperature,
    domain: [0, 100],
    getStatus: (v) => evalLower(v, 65, 80),
    getMetricStatus: (v) => healthToMetricStatus(evalLower(v, 65, 80)),
    thresholds: { good: '< 65°C', warn: '65-80°C', critical: '> 80°C' },
    passThreshold: '≤65°C',
    format: (v) => v.toFixed(1),
  },

  POWER_CONSUMPTION: {
    label: 'Power',
    fullLabel: 'Power Consumption',
    description: 'Current electrical power draw. Monitor for efficiency and cost optimization.',
    unit: 'kW',
    category: 'system',
    color: CHART_COLORS.tertiary,
    domain: [0, 10],
    // API returns Watts, convert to kW for thresholds (5kW good, 8kW warning)
    getStatus: (v) => evalLower(v / 1000, 5, 8),
    getMetricStatus: (v) => healthToMetricStatus(evalLower(v / 1000, 5, 8)),
    thresholds: { good: '< 5 kW', warn: '5-8 kW', critical: '> 8 kW' },
    passThreshold: '≤5 kW',
    format: (v) => (v / 1000).toFixed(2),
  },

  // -------------------------------------------------------------------------
  // 5G NR n78 (3.5 GHz) - High Capacity Band
  // -------------------------------------------------------------------------
  DL_THROUGHPUT_NR3500: {
    label: 'DL Throughput',
    fullLabel: 'n78 Downlink Throughput',
    description: 'Data rate from tower to devices on 3.5 GHz band. Higher is better for user experience.',
    unit: 'Mbps',
    category: '5g-n78',
    color: CHART_COLORS.nr3500Download,
    domain: [0, 2000],
    getStatus: (v) => evalHigher(v, 1000, 500),
    getMetricStatus: (v) => healthToMetricStatus(evalHigher(v, 1000, 500)),
    thresholds: { good: '> 1000 Mbps', warn: '500-1000 Mbps', critical: '< 500 Mbps' },
    passThreshold: '≥1000 Mbps',
    band: 'n78 (3.5GHz)',
    bandShort: '3.5G',
    format: (v) => v.toFixed(0),
  },

  UL_THROUGHPUT_NR3500: {
    label: 'UL Throughput',
    fullLabel: 'n78 Uplink Throughput',
    description: 'Data rate from devices to tower on 3.5 GHz band. Important for video calls and uploads.',
    unit: 'Mbps',
    category: '5g-n78',
    color: CHART_COLORS.nr3500Upload,
    domain: [0, 200],
    getStatus: (v) => evalHigher(v, 75, 40),
    getMetricStatus: (v) => healthToMetricStatus(evalHigher(v, 75, 40)),
    thresholds: { good: '> 75 Mbps', warn: '40-75 Mbps', critical: '< 40 Mbps' },
    passThreshold: '≥75 Mbps',
    band: 'n78 (3.5GHz)',
    bandShort: '3.5G',
    format: (v) => v.toFixed(0),
  },

  RSRP_NR3500: {
    label: 'RSRP',
    fullLabel: 'n78 Reference Signal Power',
    description: 'Received signal power level. Indicates coverage strength at device locations.',
    unit: 'dBm',
    category: '5g-n78',
    color: CHART_COLORS.rsrp,
    domain: [-120, -60],
    getStatus: (v) => evalHigher(v, -85, -100),
    getMetricStatus: (v) => healthToMetricStatus(evalHigher(v, -85, -100)),
    thresholds: { good: '> -85 dBm', warn: '-100 to -85 dBm', critical: '< -100 dBm' },
    passThreshold: '≥-85 dBm',
    band: 'n78 (3.5GHz)',
    bandShort: '3.5G',
    format: (v) => v.toFixed(0),
  },

  SINR_NR3500: {
    label: 'SINR',
    fullLabel: 'n78 Signal-to-Noise Ratio',
    description: 'Ratio of signal to interference/noise. Higher values mean cleaner signal.',
    unit: 'dB',
    category: '5g-n78',
    color: CHART_COLORS.sinr,
    domain: [-10, 40],
    getStatus: (v) => evalHigher(v, 10, 5),
    getMetricStatus: (v) => healthToMetricStatus(evalHigher(v, 10, 5)),
    thresholds: { good: '> 10 dB', warn: '5-10 dB', critical: '< 5 dB' },
    passThreshold: '≥10 dB',
    band: 'n78 (3.5GHz)',
    bandShort: '3.5G',
    format: (v) => v.toFixed(1),
  },

  // -------------------------------------------------------------------------
  // 5G NR n28 (700 MHz) - Extended Coverage Band
  // -------------------------------------------------------------------------
  DL_THROUGHPUT_NR700: {
    label: 'DL Throughput',
    fullLabel: 'n28 Downlink Throughput',
    description: 'Data rate on 700 MHz coverage band. Lower speed but better penetration.',
    unit: 'Mbps',
    category: '5g-n28',
    color: CHART_COLORS.nr700Download,
    domain: [0, 150],
    getStatus: (v) => evalHigher(v, 50, 25),
    getMetricStatus: (v) => healthToMetricStatus(evalHigher(v, 50, 25)),
    thresholds: { good: '> 50 Mbps', warn: '25-50 Mbps', critical: '< 25 Mbps' },
    passThreshold: '≥50 Mbps',
    band: 'n28 (700MHz)',
    bandShort: '700M',
    format: (v) => v.toFixed(0),
  },

  UL_THROUGHPUT_NR700: {
    label: 'UL Throughput',
    fullLabel: 'n28 Uplink Throughput',
    description: 'Upload data rate on 700 MHz band. Suitable for IoT and basic uploads.',
    unit: 'Mbps',
    category: '5g-n28',
    color: CHART_COLORS.nr700Upload,
    domain: [0, 50],
    getStatus: (v) => evalHigher(v, 20, 10),
    getMetricStatus: (v) => healthToMetricStatus(evalHigher(v, 20, 10)),
    thresholds: { good: '> 20 Mbps', warn: '10-20 Mbps', critical: '< 10 Mbps' },
    passThreshold: '≥20 Mbps',
    band: 'n28 (700MHz)',
    bandShort: '700M',
    format: (v) => v.toFixed(0),
  },

  RSRP_NR700: {
    label: 'RSRP',
    fullLabel: 'n28 Reference Signal Power',
    description: 'Received signal power on 700 MHz. Better indoor penetration than 3.5 GHz.',
    unit: 'dBm',
    category: '5g-n28',
    color: CHART_COLORS.rsrp,
    domain: [-120, -60],
    getStatus: (v) => evalHigher(v, -90, -105),
    getMetricStatus: (v) => healthToMetricStatus(evalHigher(v, -90, -105)),
    thresholds: { good: '> -90 dBm', warn: '-105 to -90 dBm', critical: '< -105 dBm' },
    passThreshold: '≥-90 dBm',
    band: 'n28 (700MHz)',
    bandShort: '700M',
    format: (v) => v.toFixed(0),
  },

  SINR_NR700: {
    label: 'SINR',
    fullLabel: 'n28 Signal-to-Noise Ratio',
    description: 'Signal quality on 700 MHz band. Important for reliable coverage.',
    unit: 'dB',
    category: '5g-n28',
    color: CHART_COLORS.sinr,
    domain: [-10, 30],
    getStatus: (v) => evalHigher(v, 8, 3),
    getMetricStatus: (v) => healthToMetricStatus(evalHigher(v, 8, 3)),
    thresholds: { good: '> 8 dB', warn: '3-8 dB', critical: '< 3 dB' },
    passThreshold: '≥8 dB',
    band: 'n28 (700MHz)',
    bandShort: '700M',
    format: (v) => v.toFixed(1),
  },

  // -------------------------------------------------------------------------
  // Network Quality
  // -------------------------------------------------------------------------
  LATENCY_PING: {
    label: 'Latency',
    fullLabel: 'Round-Trip Time',
    description: 'Time for data to travel to server and back. Critical for real-time applications.',
    unit: 'ms',
    category: 'quality',
    color: CHART_COLORS.latency,
    domain: [0, 50],
    getStatus: (v) => evalLower(v, 15, 30),
    getMetricStatus: (v) => healthToMetricStatus(evalLower(v, 15, 30)),
    thresholds: { good: '< 15 ms', warn: '15-30 ms', critical: '> 30 ms' },
    passThreshold: '≤15 ms',
    format: (v) => v.toFixed(1),
  },

  TX_IMBALANCE: {
    label: 'TX Balance',
    fullLabel: 'Transmitter Imbalance',
    description: 'Power difference between transmit paths. Should be minimized for optimal MIMO.',
    unit: 'dB',
    category: 'quality',
    color: CHART_COLORS.txImbalance,
    domain: [0, 10],
    getStatus: (v) => evalLower(Math.abs(v), 4, 6),
    getMetricStatus: (v) => healthToMetricStatus(evalLower(Math.abs(v), 4, 6)),
    thresholds: { good: '< 4 dB', warn: '4-6 dB', critical: '> 6 dB' },
    passThreshold: '≤4 dB',
    format: (v) => Math.abs(v).toFixed(1),
  },

  SIGNAL_STRENGTH: {
    label: 'Signal',
    fullLabel: 'Signal Strength',
    description: 'Overall received signal strength. Basic indicator of connection quality.',
    unit: 'dBm',
    category: 'quality',
    color: CHART_COLORS.signalStrength,
    domain: [-100, -40],
    getStatus: (v) => evalHigher(v, -70, -85),
    getMetricStatus: (v) => healthToMetricStatus(evalHigher(v, -70, -85)),
    thresholds: { good: '> -70 dBm', warn: '-85 to -70 dBm', critical: '< -85 dBm' },
    passThreshold: '≥-70 dBm',
    format: (v) => v.toFixed(0),
  },

  DATA_THROUGHPUT: {
    label: 'Throughput',
    fullLabel: 'Network Throughput',
    description: 'Overall data transfer rate across the network.',
    unit: 'Mbps',
    category: 'quality',
    color: CHART_COLORS.dataThroughput,
    domain: [0, 500],
    getStatus: (v) => evalHigher(v, 200, 100),
    getMetricStatus: (v) => healthToMetricStatus(evalHigher(v, 200, 100)),
    thresholds: { good: '> 200 Mbps', warn: '100-200 Mbps', critical: '< 100 Mbps' },
    passThreshold: '≥200 Mbps',
    format: (v) => v.toFixed(0),
  },
}

// ============================================================================
// Category Configuration
// ============================================================================

export const CATEGORY_CONFIG = {
  system: {
    title: 'System Performance',
    subtitle: 'Hardware utilization and health metrics',
  },
  '5g-n78': {
    title: '5G NR n78 (3.5 GHz)',
    subtitle: 'High-speed band for capacity',
  },
  '5g-n28': {
    title: '5G NR n28 (700 MHz)',
    subtitle: 'Coverage band for wide area',
  },
  quality: {
    title: 'Network Quality',
    subtitle: 'Latency and signal quality indicators',
  },
} as const

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Get metric config by type. Returns undefined if not found.
 */
export function getMetricConfig(metricType: string): MetricConfig | undefined {
  return METRICS_CONFIG[metricType]
}

/**
 * Get all metrics for a specific category.
 */
export function getMetricsByCategory(category: MetricCategory): Record<string, MetricConfig> {
  return Object.fromEntries(
    Object.entries(METRICS_CONFIG).filter(([, config]) => config.category === category)
  )
}

/**
 * Get all 5G NR metrics (both n78 and n28).
 */
export function get5GMetrics(): Record<string, MetricConfig> {
  return Object.fromEntries(
    Object.entries(METRICS_CONFIG).filter(
      ([, config]) => config.category === '5g-n78' || config.category === '5g-n28'
    )
  )
}

/**
 * SSV (Single Site Verification) metric type identifiers.
 * These are the 5G metrics used for compliance verification.
 */
export const SSV_METRIC_TYPES = new Set([
  'DL_THROUGHPUT_NR3500',
  'UL_THROUGHPUT_NR3500',
  'RSRP_NR3500',
  'SINR_NR3500',
  'DL_THROUGHPUT_NR700',
  'UL_THROUGHPUT_NR700',
  'LATENCY_PING',
  'TX_IMBALANCE',
])

/**
 * Get all SSV (Single Site Verification) metrics.
 * These are the 5G metrics plus quality metrics used for compliance.
 */
export function getSSVMetrics(): Record<string, MetricConfig> {
  return Object.fromEntries(
    Object.entries(METRICS_CONFIG).filter(([key]) => SSV_METRIC_TYPES.has(key))
  )
}

/**
 * Evaluate a metric value and return its health status.
 */
export function evaluateMetric(metricType: string, value: number): HealthStatus | undefined {
  const config = METRICS_CONFIG[metricType]
  return config?.getStatus(value)
}

/**
 * Evaluate a metric value and return its metric status (pass/warn/fail).
 */
export function evaluateMetricStatus(metricType: string, value: number): MetricStatus | undefined {
  const config = METRICS_CONFIG[metricType]
  return config?.getMetricStatus(value)
}

/**
 * Format a metric value according to its configuration.
 */
export function formatMetricValue(metricType: string, value: number): string {
  const config = METRICS_CONFIG[metricType]
  return config?.format(value) ?? value.toString()
}
