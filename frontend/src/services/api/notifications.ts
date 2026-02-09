/**
 * Notifications API - Alert and notification operations.
 */
import { api } from './client'

export interface NotificationPage {
  content: Notification[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  last: boolean
}

export interface Notification {
  id: number
  stationId: number
  stationName?: string
  message: string
  type: 'ALERT' | 'WARNING' | 'INFO'
  status: 'UNREAD' | 'READ' | 'SENT' | 'PENDING' | 'RESOLVED'
  createdAt: string
  problemId?: string
  resolvedAt?: string
}

export interface NotificationCounts {
  total: number
  unread: number
  alerts: number
  warnings: number
}

export const notificationsApi = {
  getAll: () => api.get('/notifications'),

  // Paginated endpoint for better performance with large datasets
  getPaged: (page = 0, size = 20, status?: 'UNREAD' | 'READ') => {
    const statusParam = status ? `&status=${status}` : ''
    return api.get<NotificationPage>(`/notifications/page?page=${page}&size=${size}&sort=createdAt,desc${statusParam}`)
  },

  // Lightweight counts endpoint - no data transfer, just numbers
  getCounts: () => api.get<NotificationCounts>('/notifications/counts'),

  // Recent notifications (last 10) for activity feeds
  getRecent: () => api.get<Notification[]>('/notifications/recent'),

  getByStation: (stationId: number) => api.get(`/notifications/station/${stationId}`),

  create: (params: { stationId: number; message: string; type: string }) =>
    api.post('/notifications', null, { params }),

  deleteNotification: (id: number) => api.delete(`/notifications/${id}`),

  clearAllUnread: () => api.delete<{ status: string; deleted: number }>('/notifications/unread'),
}
