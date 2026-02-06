/**
 * Design System Constants
 *
 * Centralized styling patterns for the Base Station Platform.
 * All colors use CSS variables for dark mode compatibility.
 *
 * Usage:
 * - Import the specific constant you need
 * - Use CSS variables (colorVar) for text/icon colors
 * - Use rgba values (bg, borderColor) for backgrounds/borders
 */

// Re-export HealthStatus for backwards compatibility
export type { HealthStatus } from '../utils/metricEvaluators'

// Import for local use in HEALTH_STATUS_STYLES
import type { HealthStatus } from '../utils/metricEvaluators'

// ============================================================================
// Base Colors - CSS Variables (adapt to light/dark mode automatically)
// ============================================================================

export const CSS_VARS = {
  // Status colors
  statusActive: 'var(--status-active)',        // #16a34a - Green
  statusMaintenance: 'var(--status-maintenance)', // #ea580c - Orange
  statusOffline: 'var(--status-offline)',      // #dc2626 - Red
  statusInfo: 'var(--status-info)',            // #737373 - Gray

  // Status backgrounds with opacity (auto-adapt for dark mode)
  statusActiveBg: 'var(--status-active-bg)',
  statusActiveBgSubtle: 'var(--status-active-bg-subtle)',
  statusActiveBorder: 'var(--status-active-border)',
  statusActiveShadow: 'var(--status-active-shadow)',

  statusWarningBg: 'var(--status-warning-bg)',
  statusWarningBgSubtle: 'var(--status-warning-bg-subtle)',
  statusWarningBorder: 'var(--status-warning-border)',
  statusWarningShadow: 'var(--status-warning-shadow)',

  statusErrorBg: 'var(--status-error-bg)',
  statusErrorBgSubtle: 'var(--status-error-bg-subtle)',
  statusErrorBorder: 'var(--status-error-border)',
  statusErrorShadow: 'var(--status-error-shadow)',

  statusInfoBg: 'var(--status-info-bg)',
  statusInfoBgSubtle: 'var(--status-info-bg-subtle)',
  statusInfoBorder: 'var(--status-info-border)',
  statusInfoShadow: 'var(--status-info-shadow)',

  // Accent colors
  accentPrimary: 'var(--accent-primary)',      // #2563eb - Blue
  accentSuccess: 'var(--accent-success)',      // #16a34a - Green
  accentWarning: 'var(--accent-warning)',      // #ea580c - Orange
  accentError: 'var(--accent-error)',          // #dc2626 - Red
  accentInfo: 'var(--accent-info)',            // #0891b2 - Cyan

  // Extended color palette (for charts, metrics, icons)
  colorBlue500: 'var(--color-blue-500)',       // #3b82f6
  colorEmerald500: 'var(--color-emerald-500)', // #10b981
  colorEmerald600: 'var(--color-emerald-600)', // #059669
  colorAmber500: 'var(--color-amber-500)',     // #f59e0b
  colorAmber600: 'var(--color-amber-600)',     // #d97706
  colorRed500: 'var(--color-red-500)',         // #ef4444
  colorPurple500: 'var(--color-purple-500)',   // #8b5cf6

  // Extended color backgrounds (auto-adapt for dark mode)
  colorBlueBg: 'var(--color-blue-bg)',
  colorBlueBorder: 'var(--color-blue-border)',
  colorEmeraldBg: 'var(--color-emerald-bg)',
  colorEmeraldBorder: 'var(--color-emerald-border)',
  colorAmberBg: 'var(--color-amber-bg)',
  colorAmberBorder: 'var(--color-amber-border)',
  colorPurpleBg: 'var(--color-purple-bg)',
  colorPurpleBorder: 'var(--color-purple-border)',
  colorVioletBg: 'var(--color-violet-bg)',
  colorVioletBorder: 'var(--color-violet-border)',

  // Gradients
  gradientAi: 'var(--gradient-ai)',
  gradientAiHover: 'var(--gradient-ai-hover)',
  gradientSuccess: 'var(--gradient-success)',
  gradientBlue: 'var(--gradient-blue)',
  gradientAmber: 'var(--gradient-amber)',

  // Surface colors
  surfaceBase: 'var(--surface-base)',
  surfaceElevated: 'var(--surface-elevated)',
  surfaceBorder: 'var(--surface-border)',
  surfaceSubtle: 'var(--surface-subtle)',
  surfaceHover: 'var(--surface-hover)',

  // Mono scale (adapts in dark mode)
  mono50: 'var(--mono-50)',
  mono100: 'var(--mono-100)',
  mono200: 'var(--mono-200)',
  mono300: 'var(--mono-300)',
  mono400: 'var(--mono-400)',
  mono500: 'var(--mono-500)',
  mono600: 'var(--mono-600)',
  mono700: 'var(--mono-700)',
  mono800: 'var(--mono-800)',
  mono900: 'var(--mono-900)',
  mono950: 'var(--mono-950)',
} as const

// ============================================================================
// RGBA Colors - For backgrounds and borders (work in both modes)
// ============================================================================

export const RGBA = {
  // Active/Success (green)
  activeLight: 'rgba(22, 163, 74, 0.08)',
  activeMedium: 'rgba(22, 163, 74, 0.15)',
  activeBorder: 'rgba(22, 163, 74, 0.3)',

  // Maintenance/Warning (orange)
  maintenanceLight: 'rgba(234, 88, 12, 0.08)',
  maintenanceMedium: 'rgba(234, 88, 12, 0.15)',
  maintenanceBorder: 'rgba(234, 88, 12, 0.3)',

  // Offline/Error (red)
  offlineLight: 'rgba(220, 38, 38, 0.08)',
  offlineMedium: 'rgba(220, 38, 38, 0.15)',
  offlineBorder: 'rgba(220, 38, 38, 0.3)',

  // Info/Neutral (gray)
  infoLight: 'rgba(115, 115, 115, 0.08)',
  infoMedium: 'rgba(115, 115, 115, 0.15)',
  infoBorder: 'rgba(115, 115, 115, 0.3)',

  // Primary (blue)
  primaryLight: 'rgba(37, 99, 235, 0.08)',
  primaryMedium: 'rgba(37, 99, 235, 0.15)',
  primaryBorder: 'rgba(37, 99, 235, 0.3)',

  // Cyan (for executing/in-progress)
  cyanLight: 'rgba(8, 145, 178, 0.08)',
  cyanMedium: 'rgba(8, 145, 178, 0.15)',
  cyanBorder: 'rgba(8, 145, 178, 0.3)',
} as const

// ============================================================================
// Health Status Styles - For system health indicators
// Used in: Dashboard, Metrics, StationDetail
// ============================================================================

export interface HealthStatusStyle {
  colorVar: string
  bg: string
  bgGradient: string
  borderColor: string
  label: string
}

export const HEALTH_STATUS_STYLES: Record<HealthStatus, HealthStatusStyle> = {
  healthy: {
    colorVar: CSS_VARS.statusActive,
    bg: RGBA.activeMedium,
    bgGradient: `linear-gradient(135deg, ${RGBA.activeLight} 0%, rgba(22, 163, 74, 0.04) 100%)`,
    borderColor: RGBA.activeBorder,
    label: 'Healthy',
  },
  warning: {
    colorVar: CSS_VARS.statusMaintenance,
    bg: RGBA.maintenanceMedium,
    bgGradient: `linear-gradient(135deg, ${RGBA.maintenanceLight} 0%, rgba(234, 88, 12, 0.04) 100%)`,
    borderColor: RGBA.maintenanceBorder,
    label: 'Warning',
  },
  critical: {
    colorVar: CSS_VARS.statusOffline,
    bg: RGBA.offlineMedium,
    bgGradient: `linear-gradient(135deg, ${RGBA.offlineLight} 0%, rgba(220, 38, 38, 0.04) 100%)`,
    borderColor: RGBA.offlineBorder,
    label: 'Critical',
  },
} as const

// ============================================================================
// Card Status Styles - For stat cards, metric cards (uses CSS vars for dark mode)
// Used in: Dashboard, Metrics, FiveGDashboard, PowerDashboard
// ============================================================================

export type CardStatus = 'healthy' | 'warning' | 'critical' | 'info'

export interface CardStatusStyle {
  color: string
  bg: string
  bgSubtle: string
  border: string
  shadow: string
  label: string
}

export const CARD_STATUS_STYLES: Record<CardStatus, CardStatusStyle> = {
  healthy: {
    color: CSS_VARS.statusActive,
    bg: CSS_VARS.statusActiveBg,
    bgSubtle: CSS_VARS.statusActiveBgSubtle,
    border: CSS_VARS.statusActiveBorder,
    shadow: CSS_VARS.statusActiveShadow,
    label: 'Healthy',
  },
  warning: {
    color: CSS_VARS.statusMaintenance,
    bg: CSS_VARS.statusWarningBg,
    bgSubtle: CSS_VARS.statusWarningBgSubtle,
    border: CSS_VARS.statusWarningBorder,
    shadow: CSS_VARS.statusWarningShadow,
    label: 'Warning',
  },
  critical: {
    color: CSS_VARS.statusOffline,
    bg: CSS_VARS.statusErrorBg,
    bgSubtle: CSS_VARS.statusErrorBgSubtle,
    border: CSS_VARS.statusErrorBorder,
    shadow: CSS_VARS.statusErrorShadow,
    label: 'Critical',
  },
  info: {
    color: CSS_VARS.statusInfo,
    bg: CSS_VARS.statusInfoBg,
    bgSubtle: CSS_VARS.statusInfoBgSubtle,
    border: CSS_VARS.statusInfoBorder,
    shadow: CSS_VARS.statusInfoShadow,
    label: 'Info',
  },
} as const

/** Helper to get card status from health metrics */
export function getCardStatus(value: number, thresholds: { good: number; warn: number }): CardStatus {
  if (value <= thresholds.good) return 'healthy'
  if (value <= thresholds.warn) return 'warning'
  return 'critical'
}

// ============================================================================
// Metric Status Styles - For SSV/compliance metrics (pass/warning/fail)
// Used in: NR5GQuickStatus, NR5GMetricsCard
// ============================================================================

export type MetricStatusType = 'pass' | 'warning' | 'fail'

export interface MetricStatusStyle {
  color: string      // Primary color for text/icons
  bg: string         // Background color (medium opacity)
  bgSubtle: string   // Subtle background (low opacity)
  border: string     // Border color
  shadow: string     // Shadow/glow color
  label: string      // Human-readable label
}

export const METRIC_STATUS_STYLES: Record<MetricStatusType, MetricStatusStyle> = {
  pass: {
    color: CSS_VARS.statusActive,
    bg: CSS_VARS.statusActiveBg,
    bgSubtle: CSS_VARS.statusActiveBgSubtle,
    border: CSS_VARS.statusActiveBorder,
    shadow: CSS_VARS.statusActiveShadow,
    label: 'Pass',
  },
  warning: {
    color: CSS_VARS.statusMaintenance,
    bg: CSS_VARS.statusWarningBg,
    bgSubtle: CSS_VARS.statusWarningBgSubtle,
    border: CSS_VARS.statusWarningBorder,
    shadow: CSS_VARS.statusWarningShadow,
    label: 'Warning',
  },
  fail: {
    color: CSS_VARS.statusOffline,
    bg: CSS_VARS.statusErrorBg,
    bgSubtle: CSS_VARS.statusErrorBgSubtle,
    border: CSS_VARS.statusErrorBorder,
    shadow: CSS_VARS.statusErrorShadow,
    label: 'Fail',
  },
} as const

// ============================================================================
// Station Status Styles - For base station states
// Used in: Stations, MapView, StationDetail
// ============================================================================

export type StationStatusType = 'ACTIVE' | 'MAINTENANCE' | 'OFFLINE'

export interface StationStatusStyle {
  colorVar: string
  bg: string
  borderColor: string
  label: string
}

export const STATION_STATUS_STYLES: Record<StationStatusType, StationStatusStyle> = {
  ACTIVE: {
    colorVar: CSS_VARS.statusActive,
    bg: RGBA.activeMedium,
    borderColor: RGBA.activeBorder,
    label: 'Active',
  },
  MAINTENANCE: {
    colorVar: CSS_VARS.statusMaintenance,
    bg: RGBA.maintenanceMedium,
    borderColor: RGBA.maintenanceBorder,
    label: 'Maintenance',
  },
  OFFLINE: {
    colorVar: CSS_VARS.statusOffline,
    bg: RGBA.offlineMedium,
    borderColor: RGBA.offlineBorder,
    label: 'Offline',
  },
} as const

/**
 * Get the CSS variable color for a station status.
 * Returns the offline color as fallback for unknown statuses.
 */
export function getStationStatusColor(status: string): string {
  const style = STATION_STATUS_STYLES[status as StationStatusType]
  return style?.colorVar ?? STATION_STATUS_STYLES.OFFLINE.colorVar
}

// ============================================================================
// Workflow Status Styles - For approval workflows (SON, etc.)
// Used in: SONRecommendations, and any approval-based features
// ============================================================================

export type WorkflowStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'EXECUTING'
  | 'EXECUTED'
  | 'FAILED'
  | 'ROLLED_BACK'
  | 'EXPIRED'

export interface WorkflowStatusStyle {
  colorVar: string
  bg: string
  borderColor: string
  label: string
}

export const WORKFLOW_STATUS_STYLES: Record<WorkflowStatus, WorkflowStatusStyle> = {
  PENDING: {
    colorVar: CSS_VARS.statusMaintenance,
    bg: RGBA.maintenanceMedium,
    borderColor: RGBA.maintenanceBorder,
    label: 'Pending',
  },
  APPROVED: {
    colorVar: CSS_VARS.accentPrimary,
    bg: RGBA.primaryMedium,
    borderColor: RGBA.primaryBorder,
    label: 'Approved',
  },
  REJECTED: {
    colorVar: CSS_VARS.statusInfo,
    bg: RGBA.infoMedium,
    borderColor: RGBA.infoBorder,
    label: 'Rejected',
  },
  EXECUTING: {
    colorVar: CSS_VARS.accentInfo,
    bg: RGBA.cyanMedium,
    borderColor: RGBA.cyanBorder,
    label: 'Executing',
  },
  EXECUTED: {
    colorVar: CSS_VARS.statusActive,
    bg: RGBA.activeMedium,
    borderColor: RGBA.activeBorder,
    label: 'Executed',
  },
  FAILED: {
    colorVar: CSS_VARS.statusOffline,
    bg: RGBA.offlineMedium,
    borderColor: RGBA.offlineBorder,
    label: 'Failed',
  },
  ROLLED_BACK: {
    colorVar: CSS_VARS.statusMaintenance,
    bg: RGBA.maintenanceMedium,
    borderColor: RGBA.maintenanceBorder,
    label: 'Rolled Back',
  },
  EXPIRED: {
    colorVar: CSS_VARS.statusInfo,
    bg: RGBA.infoMedium,
    borderColor: RGBA.infoBorder,
    label: 'Expired',
  },
} as const

// ============================================================================
// Alert/Notification Severity Styles
// Used in: Alerts, StationDetail, Notifications
// ============================================================================

export type AlertSeverity = 'ALERT' | 'CRITICAL' | 'WARNING' | 'INFO'

export interface AlertSeverityStyle {
  colorVar: string
  bg: string
  borderColor: string
  label: string
}

export const ALERT_SEVERITY_STYLES: Record<AlertSeverity, AlertSeverityStyle> = {
  ALERT: {
    colorVar: CSS_VARS.statusOffline,
    bg: RGBA.offlineMedium,
    borderColor: RGBA.offlineBorder,
    label: 'Alert',
  },
  CRITICAL: {
    colorVar: CSS_VARS.statusOffline,
    bg: RGBA.offlineMedium,
    borderColor: RGBA.offlineBorder,
    label: 'Critical',
  },
  WARNING: {
    colorVar: CSS_VARS.statusMaintenance,
    bg: RGBA.maintenanceMedium,
    borderColor: RGBA.maintenanceBorder,
    label: 'Warning',
  },
  INFO: {
    colorVar: CSS_VARS.statusInfo,
    bg: RGBA.infoMedium,
    borderColor: RGBA.infoBorder,
    label: 'Info',
  },
} as const

/**
 * Get severity color CSS variable from a severity string.
 * Handles both ALERT and CRITICAL as equivalent (for different data sources).
 */
export function getSeverityColorVar(severity?: string): string {
  if (!severity) return CSS_VARS.statusInfo
  const key = severity.toUpperCase() as AlertSeverity
  return ALERT_SEVERITY_STYLES[key]?.colorVar ?? CSS_VARS.statusInfo
}

// ============================================================================
// AI Diagnostic Status Styles
// Used in: AIDiagnostics
// ============================================================================

export type DiagnosticStatus = 'RESOLVED' | 'DIAGNOSED' | 'DETECTED'

export interface DiagnosticStatusStyle {
  colorVar: string
  bg: string
  borderColor: string
  label: string
}

export const DIAGNOSTIC_STATUS_STYLES: Record<DiagnosticStatus, DiagnosticStatusStyle> = {
  RESOLVED: {
    colorVar: CSS_VARS.statusActive,
    bg: RGBA.activeMedium,
    borderColor: RGBA.activeBorder,
    label: 'Resolved',
  },
  DIAGNOSED: {
    colorVar: CSS_VARS.statusMaintenance,
    bg: RGBA.maintenanceMedium,
    borderColor: RGBA.maintenanceBorder,
    label: 'Diagnosed',
  },
  DETECTED: {
    colorVar: CSS_VARS.statusOffline,
    bg: RGBA.offlineMedium,
    borderColor: RGBA.offlineBorder,
    label: 'Detected',
  },
} as const

// ============================================================================
// Confidence/Progress Level Colors
// Used in: SONRecommendations, Metrics
// ============================================================================

export function getConfidenceColor(confidence: number): string {
  if (confidence > 0.8) return CSS_VARS.statusActive
  if (confidence > 0.6) return CSS_VARS.statusMaintenance
  return CSS_VARS.statusOffline
}

export function getProgressColor(value: number, thresholds: { good: number; warn: number }): string {
  if (value <= thresholds.good) return CSS_VARS.statusActive
  if (value <= thresholds.warn) return CSS_VARS.statusMaintenance
  return CSS_VARS.statusOffline
}

// ============================================================================
// Common Component Style Helpers
// ============================================================================

/** Get styles for a status chip */
export function getChipStyles(style: { colorVar: string; bg: string; borderColor: string }) {
  return {
    backgroundColor: style.bg,
    color: style.colorVar,
    fontWeight: 600,
    fontSize: '0.75rem',
    border: `1px solid ${style.borderColor}`,
    '& .MuiChip-icon': { color: `${style.colorVar} !important` },
  }
}

/** Get styles for a stat card icon box */
export function getIconBoxStyles(style: { colorVar: string; bg: string }) {
  return {
    p: 1,
    borderRadius: '8px',
    background: style.bg,
    color: style.colorVar,
    '& svg': { fontSize: 20 },
  }
}

/** Get styles for an action button */
export function getActionButtonStyles(style: { colorVar: string; bg: string }) {
  return {
    color: style.colorVar,
    '&:hover': { backgroundColor: style.bg },
  }
}

/** Get styles for a status banner */
export function getBannerStyles(style: HealthStatusStyle) {
  return {
    background: style.bgGradient,
    border: `2px solid ${style.borderColor}`,
    borderRadius: '16px',
  }
}

// ============================================================================
// Form Select Component Styles
// Used in: Metrics, StationFormDialog
// ============================================================================

/** Styles for InputLabel in Select/FormControl */
export const INPUT_LABEL_SX = {
  color: 'var(--mono-500)',
  '&.Mui-focused': { color: 'var(--mono-700)' },
} as const

/** Styles for Select input field */
export const SELECT_INPUT_SX = {
  '& .MuiOutlinedInput-notchedOutline': {
    borderColor: 'var(--surface-border)',
  },
  '&:hover .MuiOutlinedInput-notchedOutline': {
    borderColor: 'var(--mono-400)',
  },
  '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
    borderColor: 'var(--mono-600)',
  },
  '& .MuiSelect-select': {
    color: 'var(--mono-950)',
  },
} as const

/** MenuProps for Select dropdown styling */
export const SELECT_MENU_PROPS = {
  PaperProps: {
    sx: {
      background: 'var(--surface-base)',
      border: '1px solid var(--surface-border)',
      borderRadius: '8px',
      boxShadow: 'var(--shadow-lg)',
      '& .MuiMenuItem-root': {
        color: 'var(--mono-950)',
        '&:hover': { background: 'var(--surface-hover)' },
        '&.Mui-selected': {
          background: 'var(--surface-subtle)',
          '&:hover': { background: 'var(--surface-hover)' },
        },
      },
    },
  },
} as const

/** Combined FormControl sx for filter-style selects (flex layout) */
export const FILTER_FORM_CONTROL_SX = {
  flex: '1 1 200px',
  '& .MuiOutlinedInput-root': {
    background: 'var(--surface-base)',
    borderRadius: '8px',
    color: 'var(--mono-950)',
    '& fieldset': { borderColor: 'var(--surface-border)' },
    '&:hover fieldset': { borderColor: 'var(--mono-400)' },
  },
  '& .MuiInputLabel-root': {
    color: 'var(--mono-600)',
    '&.Mui-focused': { color: 'var(--mono-950)' },
  },
  '& .MuiSelect-icon': { color: 'var(--mono-600)' },
} as const

// ============================================================================
// Grid Layout Styles
// Used in: NR5GMetricsCard, MapView, StationDetail, Metrics
// ============================================================================

/** Auto-fit responsive grid with 200px minimum column width */
export const GRID_AUTO_FIT_SX = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
  gap: '16px',
} as const

/** Responsive 1-2 column grid (xs: 1, sm: 2) */
export const GRID_1_2_COL_SX = {
  display: 'grid',
  gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' },
  gap: '20px',
} as const

/** Responsive 1-2-4 column grid for metric cards */
export const GRID_METRIC_CARDS_SX = {
  display: 'grid',
  gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' },
  gap: '16px',
} as const

/** Main content + sidebar layout (2:1 ratio on large screens) */
export const GRID_CONTENT_SIDEBAR_SX = {
  display: 'grid',
  gridTemplateColumns: { xs: '1fr', lg: '2fr 1fr' },
  gap: '16px',
} as const

// ============================================================================
// Polling Intervals - For React Query refetchInterval
// ============================================================================

/** Polling interval constants (in milliseconds) - Optimized for performance */
export const POLLING_INTERVALS = {
  /** Fast polling for real-time critical data (15 seconds) */
  FAST: 15_000,
  /** Standard polling for live dashboards (30 seconds) */
  STANDARD: 30_000,
  /** Normal polling for most data (60 seconds) */
  NORMAL: 60_000,
  /** Slow polling for less critical data (2 minutes) */
  SLOW: 120_000,
} as const

// ============================================================================
// Chart Constants - For MetricsChart and similar components
// ============================================================================

/** Chart time range and caching constants */
export const CHART_CONFIG = {
  /** Default time range for historical charts (7 days in milliseconds) */
  DEFAULT_TIME_RANGE_MS: 7 * 24 * 60 * 60 * 1000,
  /** Default data point limit for charts */
  DEFAULT_DATA_LIMIT: 10_000,
  /** Default stale time for chart data (5 minutes in milliseconds) */
  DEFAULT_STALE_TIME_MS: 5 * 60 * 1000,
  /** Default chart height in pixels */
  DEFAULT_HEIGHT: 300,
} as const
