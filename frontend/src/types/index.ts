export enum StationType {
  MACRO_CELL = 'MACRO_CELL',
  MICRO_CELL = 'MICRO_CELL',
  SMALL_CELL = 'SMALL_CELL',
  FEMTO_CELL = 'FEMTO_CELL',
}

export enum StationStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
  MAINTENANCE = 'MAINTENANCE',
  OFFLINE = 'OFFLINE',
  ERROR = 'ERROR',
}

export enum ManagementProtocol {
  DIRECT = 'DIRECT',
  EDGE_BRIDGE = 'EDGE_BRIDGE',
}

export enum MetricType {
  CPU_USAGE = 'CPU_USAGE',
  MEMORY_USAGE = 'MEMORY_USAGE',
  POWER_CONSUMPTION = 'POWER_CONSUMPTION',
  TEMPERATURE = 'TEMPERATURE',
  SIGNAL_STRENGTH = 'SIGNAL_STRENGTH',
  UPTIME = 'UPTIME',
  CONNECTION_COUNT = 'CONNECTION_COUNT',
  DATA_THROUGHPUT = 'DATA_THROUGHPUT',
  FAN_SPEED = 'FAN_SPEED',
  // 5G NR Metrics
  DL_THROUGHPUT_NR3500 = 'DL_THROUGHPUT_NR3500',
  UL_THROUGHPUT_NR3500 = 'UL_THROUGHPUT_NR3500',
  RSRP_NR3500 = 'RSRP_NR3500',
  SINR_NR3500 = 'SINR_NR3500',
  DL_THROUGHPUT_NR700 = 'DL_THROUGHPUT_NR700',
  UL_THROUGHPUT_NR700 = 'UL_THROUGHPUT_NR700',
  LATENCY_PING = 'LATENCY_PING',
  TX_IMBALANCE = 'TX_IMBALANCE',
}

export enum NotificationType {
  ALERT = 'ALERT',
  WARNING = 'WARNING',
  INFO = 'INFO',
}

export interface BaseStation {
  id?: number
  stationName: string
  location: string
  latitude: number
  longitude: number
  stationType: StationType
  status?: StationStatus // Derived from metrics/heartbeats
  ipAddress: string // Required for connectivity
  port?: number // Optional, defaults to protocol-specific port
  managementProtocol: ManagementProtocol
  powerConsumption?: number // Read from metrics
  description?: string
  lastContactAt?: string
  edgeBridgeId?: number
  createdAt?: string
  updatedAt?: string
}

export interface MetricData {
  id?: string
  stationId: number
  stationName: string
  metricType: MetricType
  value: number
  unit: string
  timestamp?: string
}

export interface Notification {
  id?: number
  stationId: number
  stationName?: string
  message: string
  type: NotificationType
  severity?: string
  status?: 'UNREAD' | 'READ' | 'PENDING'
  createdAt?: string
  readAt?: string
}

