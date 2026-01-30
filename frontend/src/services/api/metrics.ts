/**
 * Metrics API - Performance metrics operations.
 */
import { MetricData } from '../../types'
import { api } from './client'

export const metricsApi = {
  getAll: (params?: { startTime?: string; endTime?: string; limit?: number; sort?: 'asc' | 'desc' }) =>
    api.get('/metrics', { params: { ...params, limit: params?.limit ?? 5000 } }),

  getByStation: (stationId: number) => api.get(`/metrics/station/${stationId}`),

  create: (data: MetricData) => api.post('/metrics', data),
}
