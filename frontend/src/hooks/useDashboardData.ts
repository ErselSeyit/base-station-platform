/**
 * useDashboardData - Custom hook for Dashboard data fetching and processing.
 *
 * Encapsulates all data fetching, processing, and status calculation logic
 * to keep the Dashboard component focused on presentation.
 */
import { useQuery } from '@tanstack/react-query'
import { metricsApi, notificationsApi, stationApi } from '../services/api'
import { BaseStation, MetricData, Notification, StationStatus } from '../types'

// ============================================================================
// Types
// ============================================================================
export type HealthStatus = 'healthy' | 'warning' | 'critical'

export interface ProcessedMetric {
  type: string
  value: number
  config: MetricConfig
}

export interface MetricConfig {
  label: string
  fullLabel: string
  unit: string
  category: 'system' | 'network' | '5g-n78' | '5g-n28' | '5g-quality'
  getStatus: (v: number) => HealthStatus
  format: (v: number) => string
  showProgress?: boolean
  progressMax?: number
}

export interface DashboardData {
  // Loading states
  isLoading: boolean

  // Station data
  stations: BaseStation[]
  activeCount: number
  maintenanceStations: BaseStation[]
  offlineStations: BaseStation[]

  // Notifications
  notifications: Notification[]
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

// Evaluate thresholds (higher is better)
function evalHigher(v: number, goodThreshold: number, warnThreshold: number): HealthStatus {
  if (v >= goodThreshold) return 'healthy'
  if (v >= warnThreshold) return 'warning'
  return 'critical'
}

// Evaluate thresholds (lower is better)
function evalLower(v: number, goodThreshold: number, warnThreshold: number): HealthStatus {
  if (v <= goodThreshold) return 'healthy'
  if (v <= warnThreshold) return 'warning'
  return 'critical'
}

// Metric configuration
const METRICS_CONFIG: Record<string, MetricConfig> = {
  // System Performance
  CPU_USAGE: {
    label: 'CPU',
    fullLabel: 'CPU Usage',
    unit: '%',
    category: 'system',
    getStatus: (v) => evalLower(v, 70, 85),
    format: (v) => v.toFixed(1),
    showProgress: true,
    progressMax: 100,
  },
  MEMORY_USAGE: {
    label: 'Memory',
    fullLabel: 'Memory Usage',
    unit: '%',
    category: 'system',
    getStatus: (v) => evalLower(v, 75, 90),
    format: (v) => v.toFixed(1),
    showProgress: true,
    progressMax: 100,
  },
  TEMPERATURE: {
    label: 'Temperature',
    fullLabel: 'System Temperature',
    unit: '°C',
    category: 'system',
    getStatus: (v) => evalLower(v, 65, 80),
    format: (v) => v.toFixed(1),
  },
  POWER_CONSUMPTION: {
    label: 'Power',
    fullLabel: 'Power Draw',
    unit: 'kW',
    category: 'system',
    getStatus: (v) => evalLower(v, 5, 8),
    format: (v) => v.toFixed(2),
  },
  // Network
  DATA_THROUGHPUT: {
    label: 'Throughput',
    fullLabel: 'Network Throughput',
    unit: 'Mbps',
    category: 'network',
    getStatus: (v) => evalHigher(v, 200, 100),
    format: (v) => v.toFixed(0),
  },
  // 5G NR n78 (3.5GHz - high speed)
  DL_THROUGHPUT_NR3500: {
    label: 'Download',
    fullLabel: 'Download Speed',
    unit: 'Mbps',
    category: '5g-n78',
    getStatus: (v) => evalHigher(v, 1000, 500),
    format: (v) => v.toFixed(0),
  },
  UL_THROUGHPUT_NR3500: {
    label: 'Upload',
    fullLabel: 'Upload Speed',
    unit: 'Mbps',
    category: '5g-n78',
    getStatus: (v) => evalHigher(v, 75, 40),
    format: (v) => v.toFixed(0),
  },
  RSRP_NR3500: {
    label: 'Signal Power',
    fullLabel: 'Reference Signal Power',
    unit: 'dBm',
    category: '5g-n78',
    getStatus: (v) => evalHigher(v, -85, -100),
    format: (v) => v.toFixed(0),
  },
  SINR_NR3500: {
    label: 'Signal Quality',
    fullLabel: 'Signal-to-Noise Ratio',
    unit: 'dB',
    category: '5g-n78',
    getStatus: (v) => evalHigher(v, 10, 5),
    format: (v) => v.toFixed(1),
  },
  // 5G NR n28 (700MHz - coverage)
  DL_THROUGHPUT_NR700: {
    label: 'Download',
    fullLabel: 'Download Speed',
    unit: 'Mbps',
    category: '5g-n28',
    getStatus: (v) => evalHigher(v, 50, 25),
    format: (v) => v.toFixed(0),
  },
  UL_THROUGHPUT_NR700: {
    label: 'Upload',
    fullLabel: 'Upload Speed',
    unit: 'Mbps',
    category: '5g-n28',
    getStatus: (v) => evalHigher(v, 20, 10),
    format: (v) => v.toFixed(0),
  },
  // 5G Quality
  LATENCY_PING: {
    label: 'Latency',
    fullLabel: 'Network Latency',
    unit: 'ms',
    category: '5g-quality',
    getStatus: (v) => evalLower(v, 15, 30),
    format: (v) => v.toFixed(1),
  },
  TX_IMBALANCE: {
    label: 'TX Balance',
    fullLabel: 'Transmitter Balance',
    unit: 'dB',
    category: '5g-quality',
    getStatus: (v) => evalLower(Math.abs(v), 4, 6),
    format: (v) => Math.abs(v).toFixed(1),
  },
}

// Get infrastructure status
function getInfraStatus(
  activeCount: number,
  totalCount: number,
  type: 'active' | 'maintenance' | 'offline',
  count?: number
): HealthStatus {
  if (type === 'active') {
    const ratio = totalCount > 0 ? activeCount / totalCount : 0
    if (ratio >= THRESHOLDS.ACTIVE_RATIO_GOOD) return 'healthy'
    if (ratio >= THRESHOLDS.ACTIVE_RATIO_WARN) return 'warning'
    return 'critical'
  }
  if (type === 'maintenance') {
    if (count === 0) return 'healthy'
    if (count !== undefined && count <= THRESHOLDS.MAINTENANCE_WARN) return 'warning'
    return 'critical'
  }
  // offline
  return count === 0 ? 'healthy' : 'critical'
}

// Calculate section status using Set for efficient lookup
function getSectionStatus(items: ProcessedMetric[]): HealthStatus {
  const statuses = new Set(items.map(m => m.config.getStatus(m.value)))
  if (statuses.has('critical')) return 'critical'
  if (statuses.has('warning')) return 'warning'
  return 'healthy'
}

// Get status with fallback for empty arrays
function getStatusOrDefault(items: ProcessedMetric[]): HealthStatus {
  return items.length > 0 ? getSectionStatus(items) : 'healthy'
}

// Determine overall status from array of statuses
function determineOverallStatus(statuses: HealthStatus[]): HealthStatus {
  if (statuses.includes('critical')) return 'critical'
  if (statuses.includes('warning')) return 'warning'
  return 'healthy'
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
    qualityMetrics: processedMetrics.filter(m => m.config.category === '5g-quality'),
  }
}

// ============================================================================
// Hook
// ============================================================================
export function useDashboardData(): DashboardData {
  // Fetch stations
  const { data: stationsData, isLoading: stationsLoading } = useQuery({
    queryKey: ['stations'],
    queryFn: async () => {
      const response = await stationApi.getAll()
      return response.data
    },
  })

  // Fetch notifications
  const { data: notificationsData } = useQuery({
    queryKey: ['recent-notifications'],
    queryFn: async () => {
      const response = await notificationsApi.getAll()
      return response.data
    },
  })

  // Fetch metrics
  const { data: metricsData } = useQuery({
    queryKey: ['metrics-latest'],
    queryFn: async () => {
      const response = await metricsApi.getAll({
        startTime: new Date(Date.now() - THRESHOLDS.METRICS_TIMERANGE_MS).toISOString(),
      })
      return response.data
    },
    refetchInterval: THRESHOLDS.METRICS_REFRESH_MS,
  })

  // Process station data
  const stations = Array.isArray(stationsData) ? stationsData : []
  const activeCount = stations.filter((s: BaseStation) => s.status === StationStatus.ACTIVE).length
  const maintenanceStations = stations.filter((s: BaseStation) => s.status === StationStatus.MAINTENANCE)
  const offlineStations = stations.filter((s: BaseStation) => s.status === StationStatus.OFFLINE)

  // Process notifications
  const notifications = Array.isArray(notificationsData) ? notificationsData : []
  const unreadAlerts = notifications.filter((n: Notification) => n.status === 'UNREAD').length

  // Process metrics
  const metrics = Array.isArray(metricsData) ? metricsData : []
  const { systemMetrics, nr78Metrics, nr28Metrics, qualityMetrics } = processMetrics(metrics)

  // Calculate statuses
  const systemStatus = getStatusOrDefault(systemMetrics)
  const nr78Status = getStatusOrDefault(nr78Metrics)
  const nr28Status = getStatusOrDefault(nr28Metrics)
  const qualityStatus = getStatusOrDefault(qualityMetrics)

  // Infrastructure status
  const infraStatusType = offlineStations.length > 0 ? 'offline' : 'maintenance'
  const infraStatusCount = offlineStations.length > 0 ? offlineStations.length : maintenanceStations.length
  const infraStatus = getInfraStatus(activeCount, stations.length, infraStatusType, infraStatusCount)

  // Overall status
  const allStatuses: HealthStatus[] = [systemStatus, nr78Status, nr28Status, qualityStatus, infraStatus]
  const overallStatus = determineOverallStatus(allStatuses)
  const issueCount = allStatuses.filter(s => s !== 'healthy').length

  return {
    isLoading: stationsLoading,
    stations,
    activeCount,
    maintenanceStations,
    offlineStations,
    notifications,
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

// Re-export utility function for components that need it
export { getInfraStatus }
