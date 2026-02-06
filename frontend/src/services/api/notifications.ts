/**
 * Notifications API - Alert and notification operations.
 */
import { api } from './client'

export const notificationsApi = {
  getAll: () => api.get('/notifications'),

  getByStation: (stationId: number) => api.get(`/notifications/station/${stationId}`),

  create: (params: { stationId: number; message: string; type: string }) =>
    api.post('/notifications', null, { params }),

  markAsRead: (id: number) => api.put(`/notifications/${id}/read`),
}
