import { StationStatus, NotificationType } from '../types'

/**
 * Get MUI chip color based on station status.
 */
export function getStatusColor(status: StationStatus): 'success' | 'warning' | 'error' | 'default' {
  switch (status) {
    case StationStatus.ACTIVE:
      return 'success'
    case StationStatus.MAINTENANCE:
      return 'warning'
    case StationStatus.OFFLINE:
      return 'error'
    default:
      return 'default'
  }
}

/**
 * Get MUI alert severity based on notification type.
 */
export function getNotificationSeverity(
  type: NotificationType
): 'error' | 'warning' | 'info' {
  switch (type) {
    case NotificationType.ALERT:
      return 'error'
    case NotificationType.WARNING:
      return 'warning'
    default:
      return 'info'
  }
}

/**
 * Get human-readable status label.
 */
export function getStatusLabel(status: StationStatus): string {
  switch (status) {
    case StationStatus.ACTIVE:
      return 'Active'
    case StationStatus.MAINTENANCE:
      return 'Maintenance'
    case StationStatus.OFFLINE:
      return 'Offline'
    default:
      return 'Unknown'
  }
}

/**
 * Format a timestamp for display.
 * Returns 'N/A' for undefined/null timestamps.
 * Handles UTC timestamps from backend (which may lack 'Z' suffix).
 */
export function formatTimestamp(timestamp?: string | Date): string {
  if (!timestamp) return 'N/A'

  // If it's a string without timezone info, treat as UTC by appending 'Z'
  if (typeof timestamp === 'string' && !timestamp.endsWith('Z') && !timestamp.includes('+')) {
    timestamp = timestamp + 'Z'
  }

  const date = new Date(timestamp)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  // Relative time for recent timestamps
  if (diffMins < 1) return 'just now'
  if (diffMins < 60) return `${diffMins} min ago`
  if (diffHours < 24) return `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`
  if (diffDays < 7) return `${diffDays} day${diffDays > 1 ? 's' : ''} ago`

  // Full date for older timestamps
  return date.toLocaleString()
}

/**
 * Extract error message from unknown error type.
 * Safely handles Error instances and unknown values.
 */
export function getErrorMessage(error: unknown): string {
  if (error instanceof Error) return error.message
  return 'Failed to fetch'
}

/**
 * Get appropriate date format based on time range.
 * Uses shorter formats for longer time ranges.
 *
 * @param days - Number of days in the time range
 * @returns date-fns format string
 */
export function getDateFormat(days: number): string {
  if (days <= 7) return 'MMM dd'
  if (days <= 30) return 'MM/dd'
  return 'M/d'
}
