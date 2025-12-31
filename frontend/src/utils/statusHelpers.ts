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
 */
export function formatTimestamp(timestamp?: string | Date): string {
  if (!timestamp) return 'N/A'
  return new Date(timestamp).toLocaleString()
}
