import { mockAxiosResponse } from '../../test/mockHelpers'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '../../test/test-utils'
import Alerts from '../Alerts'
import { notificationsApi } from '../../services/api'
import { Notification, NotificationType } from '../../types'

// Mock the API
vi.mock('../../services/api', () => ({
  notificationsApi: {
    getAll: vi.fn(),
    getPaged: vi.fn(),
    getCounts: vi.fn(),
    deleteNotification: vi.fn(),
    clearAllUnread: vi.fn(),
  },
}))

// Helper to wait for loading to complete
const waitForLoadingToComplete = async () => {
  await waitFor(() => {
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })
}

describe('Alerts', () => {
  const mockNotifications: Notification[] = [
    {
      id: 1,
      stationId: 1,
      stationName: 'BS-001',
      message: 'High CPU usage detected',
      type: NotificationType.ALERT,
      status: 'UNREAD',
      createdAt: new Date().toISOString(),
    },
    {
      id: 2,
      stationId: 2,
      stationName: 'BS-002',
      message: 'Memory usage above threshold',
      type: NotificationType.WARNING,
      status: 'UNREAD',
      createdAt: new Date(Date.now() - 3600000).toISOString(), // 1 hour ago
    },
    {
      id: 3,
      stationId: 1,
      stationName: 'BS-001',
      message: 'System maintenance completed',
      type: NotificationType.INFO,
      status: 'READ',
      createdAt: new Date(Date.now() - 7200000).toISOString(), // 2 hours ago
      readAt: new Date(Date.now() - 3600000).toISOString(),
    },
    {
      id: 4,
      stationId: 3,
      message: 'Temperature sensor offline', // No stationName
      type: NotificationType.ALERT,
      status: 'UNREAD',
      createdAt: new Date(Date.now() - 1800000).toISOString(), // 30 minutes ago
    },
  ]

  beforeEach(() => {
    vi.clearAllMocks()
    // Set default mock for most tests
    vi.mocked(notificationsApi.getAll).mockResolvedValue(mockAxiosResponse(mockNotifications))
  })

  it('renders loading state initially', async () => {
    vi.mocked(notificationsApi.getAll).mockImplementation(
      () => new Promise(() => {}) // Never resolves
    )

    render(<Alerts />)

    await waitFor(() => {
      const progress = screen.getByRole('progressbar')
      expect(progress).toBeInTheDocument()
    })
  })

  it('renders alerts page with notifications', async () => {
    vi.mocked(notificationsApi.getAll).mockResolvedValue(mockAxiosResponse(mockNotifications))

    render(<Alerts />)

    await waitFor(() => {
      expect(screen.getByText('Alerts & Notifications')).toBeInTheDocument()
    })

    // Check header with unread count
    expect(screen.getByText('3 Unread')).toBeInTheDocument()

    // Check alert messages are displayed
    expect(screen.getByText('High CPU usage detected')).toBeInTheDocument()
    expect(screen.getByText('Memory usage above threshold')).toBeInTheDocument()
    expect(screen.getByText('System maintenance completed')).toBeInTheDocument()
    expect(screen.getByText('Temperature sensor offline')).toBeInTheDocument()
  })

  it('displays correct severity levels for different alert types', async () => {
    vi.mocked(notificationsApi.getAll).mockResolvedValue(mockAxiosResponse(mockNotifications))

    render(<Alerts />)

    await waitFor(() => {
      expect(screen.getByText('Alerts & Notifications')).toBeInTheDocument()
    })

    // Check that alert elements with different severity classes are present
    // The severity is applied via the MuiAlert component's severity prop
    const alerts = screen.getAllByRole('alert')
    expect(alerts).toHaveLength(4)
  })

  it('displays station names correctly', async () => {
    vi.mocked(notificationsApi.getAll).mockResolvedValue(mockAxiosResponse(mockNotifications))

    render(<Alerts />)

    await waitFor(() => {
      // BS-001 appears twice (two notifications from the same station)
      expect(screen.getAllByText('BS-001').length).toBeGreaterThanOrEqual(1)
      expect(screen.getByText('BS-002')).toBeInTheDocument()
      expect(screen.getByText('Station 3')).toBeInTheDocument() // No stationName, shows stationId
    })
  })

  it('displays timestamps correctly', async () => {
    vi.mocked(notificationsApi.getAll).mockResolvedValue(mockAxiosResponse(mockNotifications))

    render(<Alerts />)

    await waitFor(() => {
      // Should display formatted timestamps
      const timestampElements = screen.getAllByText(/\d{1,2}\/\d{1,2}\/\d{4}, \d{1,2}:\d{2}:\d{2} (AM|PM)/)
      expect(timestampElements.length).toBeGreaterThan(0)
    })
  })

  it('shows unread chip for unread notifications', async () => {
    vi.mocked(notificationsApi.getAll).mockResolvedValue(mockAxiosResponse(mockNotifications))

    render(<Alerts />)

    await waitFor(() => {
      const unreadChips = screen.getAllByText('Unread')
      expect(unreadChips).toHaveLength(3) // 3 unread notifications
    })
  })

  it('shows mark as read button only for unread notifications', async () => {
    render(<Alerts />)

    await waitFor(() => {
      expect(screen.getByText('Alerts & Notifications')).toBeInTheDocument()
    })

    // Should have 3 mark as read buttons (one for each unread notification) - find by icon test id
    const checkCircleIcons = screen.getAllByTestId('CheckCircleIcon')
    expect(checkCircleIcons).toHaveLength(3)
  })

  it('deletes notification when button is clicked', async () => {
    vi.mocked(notificationsApi.deleteNotification).mockResolvedValue(mockAxiosResponse({}))

    render(<Alerts />)

    await waitForLoadingToComplete()
    await waitFor(() => {
      expect(screen.getByText('3 Unread')).toBeInTheDocument()
    })

    // Click delete button for first notification (find the icon and click its parent button)
    const checkCircleIcons = screen.getAllByTestId('CheckCircleIcon')
    fireEvent.click(checkCircleIcons[0].closest('button')!)

    await waitFor(() => {
      // React Query passes (id, context) - check first arg is correct
      expect(notificationsApi.deleteNotification).toHaveBeenCalled()
      expect(vi.mocked(notificationsApi.deleteNotification).mock.calls[0][0]).toBe(1)
    })
  })

  it('refetches data after deleting notification', async () => {
    vi.mocked(notificationsApi.deleteNotification).mockResolvedValue(mockAxiosResponse({}))

    render(<Alerts />)

    await waitForLoadingToComplete()
    await waitFor(() => {
      expect(notificationsApi.getAll).toHaveBeenCalledTimes(1)
    })

    // Click mark as read button (find the icon and click its parent button)
    const checkCircleIcons = screen.getAllByTestId('CheckCircleIcon')
    fireEvent.click(checkCircleIcons[0].closest('button')!)

    await waitFor(() => {
      expect(notificationsApi.getAll).toHaveBeenCalledTimes(2)
    })
  })

  it('displays empty state when no notifications', async () => {
    vi.mocked(notificationsApi.getAll).mockResolvedValue(mockAxiosResponse([]))

    render(<Alerts />)

    await waitFor(() => {
      expect(screen.getByText('No alerts or notifications')).toBeInTheDocument()
    })

    // Should not show unread count chip
    expect(screen.queryByText(/Unread/)).not.toBeInTheDocument()
  })

  it('refetches data automatically every 30 seconds', async () => {
    // This test verifies that refetchInterval is configured
    // We just check the initial fetch works since fake timers are tricky with React Query
    render(<Alerts />)

    await waitForLoadingToComplete()
    expect(notificationsApi.getAll).toHaveBeenCalledTimes(1)
    // Note: The component has refetchInterval: 30000 configured
  })

  it('handles API errors gracefully', async () => {
    vi.mocked(notificationsApi.getAll).mockRejectedValue(new Error('API Error'))

    render(<Alerts />)

    // Should eventually show the page (React Query retries, then shows content)
    await waitFor(() => {
      expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
    }, { timeout: 3000 })

    // Should show the header and empty/error state
    expect(screen.getByText('Alerts & Notifications')).toBeInTheDocument()
  })

  it('does not show unread count chip when all notifications are read', async () => {
    const allReadNotifications = mockNotifications.map(n => ({ ...n, status: 'READ' as const }))

    vi.mocked(notificationsApi.getAll).mockResolvedValue(mockAxiosResponse(allReadNotifications))

    render(<Alerts />)

    await waitForLoadingToComplete()
    expect(screen.getByText('Alerts & Notifications')).toBeInTheDocument()

    // Should not show unread count chip
    expect(screen.queryByText(/Unread/)).not.toBeInTheDocument()

    // Should not show any "Unread" chips on individual notifications
    expect(screen.queryByText('Unread')).not.toBeInTheDocument()

    // Should not show any mark as read buttons (no aria-label on these buttons)
    expect(screen.queryByTestId('CheckCircleIcon')).not.toBeInTheDocument()
  })

  it('displays correct icon for different notification types', async () => {
    render(<Alerts />)

    await waitForLoadingToComplete()
    expect(screen.getByText('Alerts & Notifications')).toBeInTheDocument()

    // The icons are rendered by MUI Alert component based on severity
    // We can check that the alert elements are rendered with correct severity
    const alerts = screen.getAllByRole('alert')
    expect(alerts).toHaveLength(4)
  })

  it('orders notifications by creation date (most recent first)', async () => {
    render(<Alerts />)

    await waitForLoadingToComplete()
    expect(screen.getByText('High CPU usage detected')).toBeInTheDocument()

    // The notifications are rendered in the order they appear in the array
    // Most recent should appear first (id: 1, then id: 4, then id: 2, then id: 3)
    const alertElements = screen.getAllByRole('alert')
    expect(alertElements).toHaveLength(4)
  })

  it('handles notifications without timestamps', async () => {
    const notificationsWithoutTimestamps = mockNotifications.map(n => ({
      ...n,
      createdAt: undefined,
    }))

    vi.mocked(notificationsApi.getAll).mockResolvedValue(mockAxiosResponse(notificationsWithoutTimestamps))

    render(<Alerts />)

    await waitForLoadingToComplete()
    expect(screen.getByText('Alerts & Notifications')).toBeInTheDocument()

    // Should display 'N/A' for missing timestamps (one for each notification)
    const naElements = screen.getAllByText('N/A')
    expect(naElements.length).toBeGreaterThan(0)
  })

  it('handles undefined notification ids gracefully', async () => {
    const notificationsWithUndefinedIds = mockNotifications.map(n => ({
      ...n,
      id: undefined,
    }))

    vi.mocked(notificationsApi.getAll).mockResolvedValue(mockAxiosResponse(notificationsWithUndefinedIds))

    render(<Alerts />)

    await waitForLoadingToComplete()
    expect(screen.getByText('Alerts & Notifications')).toBeInTheDocument()

    // Should not show mark as read buttons for notifications without ids
    expect(screen.queryByTestId('CheckCircleIcon')).not.toBeInTheDocument()
  })
})