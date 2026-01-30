/**
 * Stations API - Base station CRUD operations.
 */
import { BaseStation } from '../../types'
import { api } from './client'

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
