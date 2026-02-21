import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  getStatusColor,
  getNotificationSeverity,
  getStatusLabel,
  formatTimestamp,
  getErrorMessage,
  getDateFormat,
} from '../statusHelpers'
import { StationStatus, NotificationType } from '../../types'

// ============================================================================
// getStatusColor
// ============================================================================

describe('getStatusColor', () => {
  it('returns "success" for ACTIVE status', () => {
    expect(getStatusColor(StationStatus.ACTIVE)).toBe('success')
  })

  it('returns "warning" for MAINTENANCE status', () => {
    expect(getStatusColor(StationStatus.MAINTENANCE)).toBe('warning')
  })

  it('returns "error" for OFFLINE status', () => {
    expect(getStatusColor(StationStatus.OFFLINE)).toBe('error')
  })

  it('returns "default" for INACTIVE status', () => {
    expect(getStatusColor(StationStatus.INACTIVE as StationStatus)).toBe('default')
  })

  it('returns "default" for ERROR status', () => {
    expect(getStatusColor(StationStatus.ERROR as StationStatus)).toBe('default')
  })

  it('returns "default" for unknown status', () => {
    expect(getStatusColor('UNKNOWN' as StationStatus)).toBe('default')
  })
})

// ============================================================================
// getNotificationSeverity
// ============================================================================

describe('getNotificationSeverity', () => {
  it('returns "error" for ALERT type', () => {
    expect(getNotificationSeverity(NotificationType.ALERT)).toBe('error')
  })

  it('returns "warning" for WARNING type', () => {
    expect(getNotificationSeverity(NotificationType.WARNING)).toBe('warning')
  })

  it('returns "info" for INFO type', () => {
    expect(getNotificationSeverity(NotificationType.INFO)).toBe('info')
  })

  it('returns "info" for unknown type', () => {
    expect(getNotificationSeverity('MAINTENANCE' as NotificationType)).toBe('info')
  })
})

// ============================================================================
// getStatusLabel
// ============================================================================

describe('getStatusLabel', () => {
  it('returns "Active" for ACTIVE', () => {
    expect(getStatusLabel(StationStatus.ACTIVE)).toBe('Active')
  })

  it('returns "Maintenance" for MAINTENANCE', () => {
    expect(getStatusLabel(StationStatus.MAINTENANCE)).toBe('Maintenance')
  })

  it('returns "Offline" for OFFLINE', () => {
    expect(getStatusLabel(StationStatus.OFFLINE)).toBe('Offline')
  })

  it('returns "Unknown" for unrecognized status', () => {
    expect(getStatusLabel('SOMETHING_ELSE' as StationStatus)).toBe('Unknown')
  })
})

// ============================================================================
// getErrorMessage
// ============================================================================

describe('getErrorMessage', () => {
  it('extracts message from Error object', () => {
    expect(getErrorMessage(new Error('Something went wrong'))).toBe('Something went wrong')
  })

  it('returns default message for string error', () => {
    expect(getErrorMessage('string error')).toBe('Failed to fetch')
  })

  it('returns default message for null', () => {
    expect(getErrorMessage(null)).toBe('Failed to fetch')
  })

  it('returns default message for undefined', () => {
    expect(getErrorMessage(undefined)).toBe('Failed to fetch')
  })

  it('returns default message for number', () => {
    expect(getErrorMessage(404)).toBe('Failed to fetch')
  })

  it('extracts message from Error subclass', () => {
    expect(getErrorMessage(new TypeError('Type issue'))).toBe('Type issue')
  })
})

// ============================================================================
// formatTimestamp
// ============================================================================

describe('formatTimestamp', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2025-06-15T12:00:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('returns "N/A" for undefined', () => {
    expect(formatTimestamp(undefined)).toBe('N/A')
  })

  it('returns "N/A" for null', () => {
    expect(formatTimestamp(null as unknown as undefined)).toBe('N/A')
  })

  it('returns "N/A" for empty string', () => {
    expect(formatTimestamp('')).toBe('N/A')
  })

  it('returns "just now" for very recent timestamp', () => {
    expect(formatTimestamp('2025-06-15T12:00:00Z')).toBe('just now')
  })

  it('returns minutes ago for recent timestamps', () => {
    expect(formatTimestamp('2025-06-15T11:55:00Z')).toBe('5 min ago')
  })

  it('returns singular hour ago', () => {
    expect(formatTimestamp('2025-06-15T11:00:00Z')).toBe('1 hour ago')
  })

  it('returns plural hours ago', () => {
    expect(formatTimestamp('2025-06-15T09:00:00Z')).toBe('3 hours ago')
  })

  it('returns singular day ago', () => {
    expect(formatTimestamp('2025-06-14T12:00:00Z')).toBe('1 day ago')
  })

  it('returns plural days ago', () => {
    expect(formatTimestamp('2025-06-12T12:00:00Z')).toBe('3 days ago')
  })

  it('returns full date for timestamps older than 7 days', () => {
    const result = formatTimestamp('2025-01-01T00:00:00Z')
    // Should be a locale-formatted date string, not a relative time
    expect(result).not.toContain('ago')
    expect(result).not.toBe('N/A')
  })

  it('appends Z to timestamps without timezone info', () => {
    // Timestamp without Z should be treated as UTC
    expect(formatTimestamp('2025-06-15T11:50:00')).toBe('10 min ago')
  })

  it('does not append Z to timestamps with timezone offset', () => {
    // Timestamp with + offset should not get Z appended
    expect(formatTimestamp('2025-06-15T12:00:00+00:00')).toBe('just now')
  })

  it('accepts Date objects', () => {
    const date = new Date('2025-06-15T11:45:00Z')
    expect(formatTimestamp(date)).toBe('15 min ago')
  })
})

// ============================================================================
// getDateFormat
// ============================================================================

describe('getDateFormat', () => {
  it('returns "MMM dd" for 1 day', () => {
    expect(getDateFormat(1)).toBe('MMM dd')
  })

  it('returns "MMM dd" for exactly 7 days', () => {
    expect(getDateFormat(7)).toBe('MMM dd')
  })

  it('returns "MM/dd" for 8 days', () => {
    expect(getDateFormat(8)).toBe('MM/dd')
  })

  it('returns "MM/dd" for exactly 30 days', () => {
    expect(getDateFormat(30)).toBe('MM/dd')
  })

  it('returns "M/d" for more than 30 days', () => {
    expect(getDateFormat(31)).toBe('M/d')
  })

  it('returns "M/d" for 365 days', () => {
    expect(getDateFormat(365)).toBe('M/d')
  })
})
