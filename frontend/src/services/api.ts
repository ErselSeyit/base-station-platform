import axios from 'axios'
import { BaseStation, MetricData } from '../types'

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1'

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Base Station API
export const stationApi = {
  getAll: () => api.get('/stations'),
  getById: (id: number) => api.get(`/stations/${id}`),
  create: (data: BaseStation) => api.post('/stations', data),
  update: (id: number, data: Partial<BaseStation>) => api.put(`/stations/${id}`, data),
  delete: (id: number) => api.delete(`/stations/${id}`),
  searchByLocation: (latitude: number, longitude: number, radius: number = 10) =>
    api.get(`/stations/search?latitude=${latitude}&longitude=${longitude}&radius=${radius}`),
  getByStatus: (status: string) => api.get(`/stations?status=${status}`),
}

// Metrics API
export const metricsApi = {
  getAll: (params?: { startTime?: string; endTime?: string }) =>
    api.get('/metrics', { params }),
  getByStation: (stationId: number) => api.get(`/metrics/station/${stationId}`),
  create: (data: MetricData) => api.post('/metrics', data),
}

// Notifications API
export const notificationsApi = {
  getAll: () => api.get('/notifications'),
  getByStation: (stationId: number) => api.get(`/notifications/station/${stationId}`),
  create: (params: { stationId: number; message: string; type: string }) =>
    api.post('/notifications', null, { params }),
  markAsRead: (id: number) => api.put(`/notifications/${id}/read`),
}

export default api

