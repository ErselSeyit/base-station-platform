/**
 * Edge Bridge API - Edge-bridge instance management.
 */
import { api } from './client'

export interface EdgeBridgeInstance {
  id: number
  bridgeId: string
  name?: string
  hostname?: string
  ipAddress?: string
  version?: string
  status: 'ONLINE' | 'OFFLINE' | 'DEGRADED' | 'STARTING' | 'STOPPED'
  lastHeartbeatAt?: string
  capabilities?: string
  managedStationIds?: number[]
  location?: string
  latitude?: number
  longitude?: number
  registeredAt?: string
  updatedAt?: string
}

export interface BridgeStats {
  total: number
  online: number
  offline: number
  degraded: number
}

export const edgeBridgeApi = {
  getAll: () => api.get<EdgeBridgeInstance[]>('/edge-bridges'),

  getById: (bridgeId: string) => api.get<EdgeBridgeInstance>(`/edge-bridges/${bridgeId}`),

  getOnline: () => api.get<EdgeBridgeInstance[]>('/edge-bridges/online'),

  getByStatus: (status: string) => api.get<EdgeBridgeInstance[]>(`/edge-bridges/status/${status}`),

  getForStation: (stationId: number) => api.get<EdgeBridgeInstance>(`/edge-bridges/station/${stationId}`),

  getStats: () => api.get<BridgeStats>('/edge-bridges/stats'),

  markOffline: (bridgeId: string) => api.post<EdgeBridgeInstance>(`/edge-bridges/${bridgeId}/offline`),
}
