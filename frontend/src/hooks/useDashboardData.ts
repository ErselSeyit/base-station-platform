/**
 * useDashboardData - Custom hook for Dashboard data fetching and processing.
 *
 * Encapsulates all data fetching, processing, and status calculation logic
 * to keep the Dashboard component focused on presentation.
 */
import { useQuery } from '@tanstack/react-query'
import { metricsApi, notificationsApi, stationApi } from '../services/api'
import { BaseStation, MetricData, StationStatus } from '../types'
import { ensureArray } from '../utils/arrayUtils'
import {
  type HealthStatus,
  type MetricConfig,
  METRICS_CONFIG,
  getWorstHealthStatus,
} from '../constants/metricsConfig'

// ============================================================================
// Types
// ============================================================================
export type { HealthStatus } from '../constants/metricsConfig'

export interface ProcessedMetric {
  type: string
  value: number
  config: MetricConfig
}

export interface DashboardData {
  // Loading and error states
  isLoading: boolean
  error: Error | null

  // Station data
  stations: BaseStation[]
  activeCount: number
  maintenanceStations: BaseStation[]
  offlineStations: BaseStation[]

  // Notifications (counts only for performance)
  unreadAlerts: number

  // Metrics
  metrics: MetricData[]
  systemMetrics: ProcessedMetric[]
  nr78Metrics: ProcessedMetric[]
  nr28Metrics: ProcessedMetric[]
  qualityMetrics: ProcessedMetric[]

  // Status calculations
  systemStatus: HealthStatus
  nr78Status: HealthStatus
  nr28Status: HealthStatus
  qualityStatus: HealthStatus
  infraStatus: HealthStatus
  overallStatus: HealthStatus
  issueCount: number
}

// ============================================================================
// Constants
// ============================================================================
const THRESHOLDS = {
  ACTIVE_RATIO_GOOD: 0.9,
  ACTIVE_RATIO_WARN: 0.7,
  MAINTENANCE_WARN: 2,
  METRICS_REFRESH_MS: 30000,
  METRICS_TIMERANGE_MS: 3600000, // 1 hour
} as const

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Get status for active stations based on active/total ratio.
 */
function getActiveStationStatus(activeCount: number, totalCount: number): HealthStatus {
  const ratio = totalCount > 0 ? activeCount / totalCount : 0
  if (ratio >= THRESHOLDS.ACTIVE_RATIO_GOOD) return 'healthy'
  if (ratio >= THRESHOLDS.ACTIVE_RATIO_WARN) return 'warning'
  return 'critical'
}

/**
 * Get status for maintenance stations based on count.
 */
function getMaintenanceStatus(count: number): HealthStatus {
  if (count === 0) return 'healthy'
  if (count <= THRESHOLDS.MAINTENANCE_WARN) return 'warning'
  return 'critical'
}

/**
 * Get status for offline stations - any offline is critical.
 */
function getOfflineStatus(count: number): HealthStatus {
  return count === 0 ? 'healthy' : 'critical'
}

// Calculate section status using Set for efficient lookup
function getSectionStatus(items: ProcessedMetric[]): HealthStatus {
  const statuses = items.map(m => m.config.getStatus(m.value))
  return getWorstHealthStatus(statuses)
}

// Get status with fallback for empty arrays
function getStatusOrDefault(items: ProcessedMetric[]): HealthStatus {
  return items.length > 0 ? getSectionStatus(items) : 'healthy'
}

// Process raw metrics into categorized arrays
function processMetrics(metrics: MetricData[]): {
  systemMetrics: ProcessedMetric[]
  nr78Metrics: ProcessedMetric[]
  nr28Metrics: ProcessedMetric[]
  qualityMetrics: ProcessedMetric[]
} {
  const metricsMap = new Map<string, { sum: number; count: number }>()
  metrics.forEach((m: MetricData) => {
    const existing = metricsMap.get(m.metricType) || { sum: 0, count: 0 }
    metricsMap.set(m.metricType, { sum: existing.sum + m.value, count: existing.count + 1 })
  })

  const processedMetrics: ProcessedMetric[] = Array.from(metricsMap.entries())
    .map(([type, data]) => ({
      type,
      value: data.sum / data.count,
      config: METRICS_CONFIG[type],
    }))
    .filter((m): m is ProcessedMetric => m.config !== undefined)

  return {
    systemMetrics: processedMetrics.filter(m => m.config.category === 'system'),
    nr78Metrics: processedMetrics.filter(m => m.config.category === '5g-n78'),
    nr28Metrics: processedMetrics.filter(m => m.config.category === '5g-n28'),
    qualityMetrics: processedMetrics.filter(m => m.config.category === 'quality'),
  }
}

// ============================================================================
// Hook
// ============================================================================
export function useDashboardData(): DashboardData {
  // Fetch stations
  const { data: stationsData, isLoading: stationsLoading, error: stationsError } = useQuery({
    queryKey: ['stations'],
    queryFn: async () => {
      const response = await stationApi.getAll()
      return response.data
    },
  })

  // Fetch notification counts (lightweight endpoint)
  const { data: notificationCounts, error: notificationsError } = useQuery({
    queryKey: ['notification-counts'],
    queryFn: async () => {
      const response = await notificationsApi.getCounts()
      return response.data
    },
    staleTime: 30000, // Cache for 30s to reduce API calls
  })

  // Fetch metrics
  const { data: metricsData, error: metricsError } = useQuery({
    queryKey: ['metrics-latest'],
    queryFn: async () => {
      const response = await metricsApi.getAll({
        startTime: new Date(Date.now() - THRESHOLDS.METRICS_TIMERANGE_MS).toISOString(),
      })
      return response.data
    },
    refetchInterval: THRESHOLDS.METRICS_REFRESH_MS,
  })

  // Combine errors - stations error is most critical
  const error = stationsError || metricsError || notificationsError || null

  // Process station data
  const stations = ensureArray(stationsData as BaseStation[])
  const activeCount = stations.filter((s) => s.status === StationStatus.ACTIVE).length
  const maintenanceStations = stations.filter((s) => s.status === StationStatus.MAINTENANCE)
  const offlineStations = stations.filter((s) => s.status === StationStatus.OFFLINE)

  // Get unread count from lightweight counts endpoint
  const unreadAlerts = notificationCounts?.unread ?? 0

  // Process metrics
  const metrics = ensureArray(metricsData as MetricData[])
  const { systemMetrics, nr78Metrics, nr28Metrics, qualityMetrics } = processMetrics(metrics)

  // Calculate statuses
  const systemStatus = getStatusOrDefault(systemMetrics)
  const nr78Status = getStatusOrDefault(nr78Metrics)
  const nr28Status = getStatusOrDefault(nr28Metrics)
  const qualityStatus = getStatusOrDefault(qualityMetrics)

  // Infrastructure status - prioritize offline over maintenance
  const infraStatus = offlineStations.length > 0
    ? getOfflineStatus(offlineStations.length)
    : getMaintenanceStatus(maintenanceStations.length)

  // Overall status
  const allStatuses: HealthStatus[] = [systemStatus, nr78Status, nr28Status, qualityStatus, infraStatus]
  const overallStatus = getWorstHealthStatus(allStatuses)
  const issueCount = allStatuses.filter(s => s !== 'healthy').length

  return {
    isLoading: stationsLoading,
    error,
    stations,
    activeCount,
    maintenanceStations,
    offlineStations,
    unreadAlerts,
    metrics,
    systemMetrics,
    nr78Metrics,
    nr28Metrics,
    qualityMetrics,
    systemStatus,
    nr78Status,
    nr28Status,
    qualityStatus,
    infraStatus,
    overallStatus,
    issueCount,
  }
}

// Re-export utility functions for components that need them
export { getActiveStationStatus, getMaintenanceStatus, getOfflineStatus }
