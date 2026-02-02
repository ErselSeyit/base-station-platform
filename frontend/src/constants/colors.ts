/**
 * Shared color constants for charts and data visualization.
 *
 * These colors are designed to work with both light and dark themes
 * and provide consistent visual language across the application.
 */

// Chart line colors with semantic meaning
export const CHART_COLORS = {
  // System metrics
  cpuUsage: 'var(--status-active)',       // Green - active/healthy
  memoryUsage: 'var(--status-maintenance)', // Yellow - warning level

  // Environmental metrics
  temperature: 'var(--color-amber-600)',    // Orange - heat/warning
  fanSpeed: 'var(--color-amber-600)',       // Orange - related to temperature

  // Signal/network metrics
  signalStrength: 'var(--color-purple-500)', // Purple - radio/signal
  rsrp: 'var(--color-purple-500)',           // Purple - signal quality
  sinr: 'var(--color-amber-500)',            // Amber - signal ratio

  // Throughput metrics
  downloadThroughput: 'var(--status-active)',  // Green - download
  uploadThroughput: 'var(--accent-info)',      // Cyan - upload
  dataThroughput: 'var(--status-active)',

  // 5G specific
  nr3500Download: 'var(--status-active)',   // Green
  nr3500Upload: 'var(--accent-info)',       // Cyan
  nr700Download: 'var(--color-emerald-500)', // Emerald
  nr700Upload: 'var(--accent-info)',         // Cyan light

  // Quality metrics
  latency: 'var(--color-violet-500)',        // Pink/violet - timing
  txImbalance: 'var(--color-amber-600)',     // Orange - imbalance/warning

  // General purpose
  primary: 'var(--status-active)',
  secondary: 'var(--status-maintenance)',
  tertiary: 'var(--status-info)',
  error: 'var(--status-offline)',
} as const

// Color palette for multi-line charts (when you need distinct colors)
export const CHART_PALETTE = [
  'var(--status-active)',       // Green
  'var(--status-maintenance)',  // Yellow
  'var(--color-purple-500)',    // Purple
  'var(--color-amber-600)',     // Orange
  'var(--accent-info)',         // Cyan
  'var(--color-violet-500)',    // Pink/violet
  'var(--color-emerald-500)',   // Emerald
  'var(--color-amber-500)',     // Amber
] as const

export type ChartColorKey = keyof typeof CHART_COLORS
