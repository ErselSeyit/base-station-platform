/**
 * Metrics API - Performance metrics operations.
 */
import { MetricData } from '../../types'
import { api } from './client'

/** Daily aggregated metrics for chart display */
export interface DailyMetricAggregate {
  date: string
  averages: Record<string, number>
  counts: Record<string, number>
}

export const metricsApi = {
  getAll: (params?: { startTime?: string; endTime?: string; limit?: number; sort?: 'asc' | 'desc' }) =>
    api.get('/metrics', { params: { ...params, limit: params?.limit ?? 5000 } }),

  getByStation: (stationId: number) => api.get(`/metrics/station/${stationId}`),

  create: (data: MetricData) => api.post('/metrics', data),

  /** Get daily aggregated metrics for chart display - more efficient than raw metrics */
  getDailyAggregates: (params: { startTime: string; endTime: string }) =>
    api.get<DailyMetricAggregate[]>('/metrics/daily', { params: { start: params.startTime, end: params.endTime } }),
}
