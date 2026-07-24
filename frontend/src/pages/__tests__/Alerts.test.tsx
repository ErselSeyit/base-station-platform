import { describe, it, expect, vi, beforeEach } from 'vitest'
import userEvent from '@testing-library/user-event'
import { render, screen, waitFor } from '../../test/test-utils'
import { mockAxiosResponse } from '../../test/mockHelpers'
import Alerts from '../Alerts'
import { notificationsApi } from '../../services/api/notifications'
import type {
  Notification,
  NotificationPage,
  NotificationCounts,
} from '../../services/api/notifications'

// Alerts reads notificationsApi from services/api/notifications, not from the
// services/api barrel, and loads through useInfiniteQuery -> getPaged with a
// separate getCounts for the stat boxes.
vi.mock('../../services/api/notifications', () => ({
  notificationsApi: {
    getPaged: vi.fn(),
    getCounts: vi.fn(),
    deleteNotification: vi.fn(),
    clearAllUnread: vi.fn(),
  },
}))

const notification = (over: Partial<Notification> = {}): Notification => ({
  id: 1,
  stationId: 1,
  stationName: 'BS-001',
  message: 'High CPU usage detected',
  type: 'ALERT',
  status: 'UNREAD',
  createdAt: '2026-07-23T10:00:00.000Z',
  ...over,
})

const pageOf = (items: Notification[], over: Partial<NotificationPage> = {}) =>
  mockAxiosResponse<NotificationPage>({
    content: items,
    totalElements: items.length,
    totalPages: 1,
    number: 0,
    size: 20,
    last: true,
    ...over,
  })

const countsOf = (items: Notification[]) =>
  mockAxiosResponse<NotificationCounts>({
    total: items.length,
    unread: items.filter((n) => n.status === 'UNREAD').length,
    alerts: items.filter((n) => n.type === 'ALERT').length,
    warnings: items.filter((n) => n.type === 'WARNING').length,
  })

const givenNotifications = (items: Notification[]) => {
  vi.mocked(notificationsApi.getPaged).mockResolvedValue(pageOf(items))
  vi.mocked(notificationsApi.getCounts).mockResolvedValue(countsOf(items))
}

describe('Alerts', () => {
  const unreadAlert = notification({ id: 1, message: 'High CPU usage detected' })
  const readWarning = notification({
    id: 2,
    stationId: 2,
    stationName: 'BS-002',
    message: 'Temperature approaching limit',
    type: 'WARNING',
    status: 'READ',
  })

  beforeEach(() => {
    vi.clearAllMocks()
    givenNotifications([unreadAlert, readWarning])
    vi.mocked(notificationsApi.deleteNotification).mockResolvedValue(mockAxiosResponse({}))
    vi.mocked(notificationsApi.clearAllUnread).mockResolvedValue(
      mockAxiosResponse({ status: 'ok', deleted: 1 })
    )
  })

  describe('header', () => {
    it('renders the page title straight away, before data arrives', () => {
      render(<Alerts />)
      expect(screen.getByText('Alerts')).toBeInTheDocument()
    })

    it('reports the loaded and total counts once data arrives', async () => {
      render(<Alerts />)
      expect(await screen.findByText(/2 total/)).toBeInTheDocument()
      expect(screen.getByText(/2 loaded/)).toBeInTheDocument()
    })

    it('shows a loading message while the first page is in flight', () => {
      vi.mocked(notificationsApi.getPaged).mockReturnValue(new Promise(() => {}))
      render(<Alerts />)
      expect(screen.getByText('Loading notifications...')).toBeInTheDocument()
    })
  })

  describe('stat boxes', () => {
    it('takes its figures from the counts endpoint, not from the loaded page', async () => {
      vi.mocked(notificationsApi.getCounts).mockResolvedValue(
        mockAxiosResponse<NotificationCounts>({ total: 99, unread: 7, alerts: 5, warnings: 3 })
      )
      render(<Alerts />)

      // Counts come from getCounts so the stats stay accurate without having
      // to load every page.
      expect(await screen.findByText('5')).toBeInTheDocument()
      expect(screen.getByText('3')).toBeInTheDocument()
      expect(screen.getByText('7')).toBeInTheDocument()
      expect(screen.getByText('Critical')).toBeInTheDocument()
      expect(screen.getByText('Warnings')).toBeInTheDocument()
      expect(screen.getByText('Unread')).toBeInTheDocument()
    })
  })

  describe('notification rows', () => {
    it('renders a row per notification with its message and station', async () => {
      render(<Alerts />)

      expect(await screen.findByText('High CPU usage detected')).toBeInTheDocument()
      expect(screen.getByText('Temperature approaching limit')).toBeInTheDocument()
      expect(screen.getByText('BS-001')).toBeInTheDocument()
      expect(screen.getByText('BS-002')).toBeInTheDocument()
    })

    it('offers "mark as read" only for unread notifications', async () => {
      render(<Alerts />)
      await screen.findByText('High CPU usage detected')

      // Only one of the two fixtures is unread.
      const actions = await screen.findAllByLabelText(/mark as read/i)
      expect(actions).toHaveLength(1)
    })

    it('shows an empty state when there is nothing to display', async () => {
      givenNotifications([])
      render(<Alerts />)

      expect(await screen.findByText('No alerts or notifications')).toBeInTheDocument()
    })
  })

  describe('actions', () => {
    it('marks a notification as read and refetches the list', async () => {
      render(<Alerts />)
      await screen.findByText('High CPU usage detected')

      const [action] = await screen.findAllByLabelText(/mark as read/i)
      await userEvent.click(action)

      // React Query v5 invokes mutationFn with (variables, context), so assert
      // on the id rather than the whole argument list.
      await waitFor(() => expect(notificationsApi.deleteNotification).toHaveBeenCalled())
      expect(vi.mocked(notificationsApi.deleteNotification).mock.calls[0][0]).toBe(unreadAlert.id)
      await waitFor(() =>
        expect(vi.mocked(notificationsApi.getPaged).mock.calls.length).toBeGreaterThan(1)
      )
    })

    it('clears all unread notifications', async () => {
      render(<Alerts />)
      const clearAll = await screen.findByRole('button', { name: /clear all/i })
      // The button stays disabled until the counts query reports unread items.
      await waitFor(() => expect(clearAll).toBeEnabled())

      await userEvent.click(clearAll)

      await waitFor(() => expect(notificationsApi.clearAllUnread).toHaveBeenCalled())
    })

    it('disables "clear all" when nothing is unread', async () => {
      givenNotifications([readWarning])
      render(<Alerts />)

      const clearAll = await screen.findByRole('button', { name: /clear all/i })
      await waitFor(() => expect(clearAll).toBeDisabled())
    })
  })

  describe('failure handling', () => {
    it('shows an error state when the page request fails', async () => {
      vi.mocked(notificationsApi.getPaged).mockRejectedValue(new Error('API Error'))
      render(<Alerts />)

      expect(await screen.findByText('Failed to load alerts')).toBeInTheDocument()
    })
  })

  describe('pagination', () => {
    it('requests the first page with the configured page size', async () => {
      render(<Alerts />)
      await waitFor(() => expect(notificationsApi.getPaged).toHaveBeenCalled())

      expect(notificationsApi.getPaged).toHaveBeenCalledWith(0, expect.any(Number))
    })
  })
})
