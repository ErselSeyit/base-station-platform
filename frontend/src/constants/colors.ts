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
  temperature: '#f97316',    // Orange - heat/warning
  fanSpeed: '#f97316',       // Orange - related to temperature

  // Signal/network metrics
  signalStrength: '#8b5cf6', // Purple - radio/signal
  rsrp: '#8b5cf6',           // Purple - signal quality
  sinr: '#f59e0b',           // Amber - signal ratio

  // Throughput metrics
  downloadThroughput: '#16a34a',  // Green - download
  uploadThroughput: '#0891b2',    // Cyan - upload
  dataThroughput: 'var(--status-active)',

  // 5G specific
  nr3500Download: '#16a34a',  // Green
  nr3500Upload: '#0891b2',    // Cyan
  nr700Download: '#10b981',   // Emerald
  nr700Upload: '#06b6d4',     // Cyan light

  // Quality metrics
  latency: '#ec4899',         // Pink - timing
  txImbalance: '#f97316',     // Orange - imbalance/warning

  // General purpose
  primary: 'var(--status-active)',
  secondary: 'var(--status-maintenance)',
  tertiary: 'var(--status-info)',
  error: 'var(--status-offline)',
} as const

// Semantic status colors (reference CSS variables)
export const STATUS_COLORS = {
  active: 'var(--status-active)',
  maintenance: 'var(--status-maintenance)',
  offline: 'var(--status-offline)',
  info: 'var(--status-info)',
} as const

// Color palette for multi-line charts (when you need distinct colors)
export const CHART_PALETTE = [
  'var(--status-active)',      // Green
  'var(--status-maintenance)', // Yellow
  '#8b5cf6',                   // Purple
  '#f97316',                   // Orange
  '#0891b2',                   // Cyan
  '#ec4899',                   // Pink
  '#10b981',                   // Emerald
  '#f59e0b',                   // Amber
] as const

export type ChartColorKey = keyof typeof CHART_COLORS
export type StatusColorKey = keyof typeof STATUS_COLORS
