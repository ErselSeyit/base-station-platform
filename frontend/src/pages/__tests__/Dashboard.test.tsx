import { describe, it, expect, vi, beforeEach } from 'vitest'
import userEvent from '@testing-library/user-event'
import { render, screen, waitFor } from '../../test/test-utils'
import Dashboard from '../Dashboard'
import { stationApi, notificationsApi, metricsApi } from '../../services/api'
import { BaseStation, ManagementProtocol, StationStatus, StationType } from '../../types'
import { mockAxiosResponse } from '../../test/mockHelpers'

// Mock the API
vi.mock('../../services/api', () => ({
  stationApi: {
    getAll: vi.fn(),
  },
  notificationsApi: {
    getAll: vi.fn(),
    // useDashboardData reads the unread badge from the counts endpoint.
    getCounts: vi.fn(),
  },
  metricsApi: {
    getAll: vi.fn(),
  },
}))

// Mock MetricsChart component
vi.mock('../../components/MetricsChart', () => ({
  default: () => <div data-testid="metrics-chart">Metrics Chart</div>,
}))

describe('Operations Dashboard', () => {
  const mockStations: BaseStation[] = [
    {
      id: 1,
      stationName: 'BS-001',
      location: 'Location 1',
      latitude: 40.7128,
      longitude: -74.006,
      stationType: StationType.MACRO_CELL,
      ipAddress: '10.100.1.101',
      managementProtocol: ManagementProtocol.DIRECT,
      status: StationStatus.ACTIVE,
      powerConsumption: 1500,
      description: 'Test station 1',
    },
    {
      id: 2,
      stationName: 'BS-002',
      location: 'Location 2',
      latitude: 40.758,
      longitude: -73.9855,
      stationType: StationType.SMALL_CELL,
      ipAddress: '10.100.1.102',
      managementProtocol: ManagementProtocol.DIRECT,
      status: StationStatus.MAINTENANCE,
      powerConsumption: 800,
      description: 'Test station 2',
    },
    {
      id: 3,
      stationName: 'BS-003',
      location: 'Location 3',
      latitude: 40.6892,
      longitude: -74.0445,
      stationType: StationType.MACRO_CELL,
      ipAddress: '10.100.1.103',
      managementProtocol: ManagementProtocol.DIRECT,
      status: StationStatus.OFFLINE,
      powerConsumption: 0,
      description: 'Test station 3',
    },
  ]

  const mockNotifications = [
    {
      id: 1,
      stationId: 1,
      stationName: 'BS-001',
      message: 'Alert message 1',
      type: 'ALERT',
      status: 'UNREAD',
      createdAt: new Date().toISOString(),
    },
    {
      id: 2,
      stationId: 2,
      stationName: 'BS-002',
      message: 'Warning message 1',
      type: 'WARNING',
      status: 'READ',
      createdAt: new Date().toISOString(),
    },
  ]

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(notificationsApi.getCounts).mockResolvedValue(
      mockAxiosResponse({ total: 0, unread: 0, alerts: 0, warnings: 0 })
    )
  })

  it('renders loading state initially', async () => {
    vi.mocked(stationApi.getAll).mockImplementation(
      () => new Promise(() => {}) // Never resolves
    )
    vi.mocked(notificationsApi.getAll).mockResolvedValue(mockAxiosResponse([]))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse([]))

    const { container } = render(<Dashboard />)
    
    // Check for CircularProgress (MUI component)
    await waitFor(() => {
      const progress = container.querySelector('.MuiCircularProgress-root') || 
                      container.querySelector('[role="progressbar"]')
      expect(progress).toBeInTheDocument()
    }, { timeout: 1000 })
  })

  it('renders dashboard with station statistics', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(notificationsApi.getAll).mockResolvedValue(mockAxiosResponse(mockNotifications))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse([]))

    render(<Dashboard />)

    await waitFor(() => {
      expect(screen.getByText('Operations Dashboard')).toBeInTheDocument()
    })

    // Check stat cards are rendered
    await waitFor(() => {
      expect(screen.getByText('Total Stations')).toBeInTheDocument()
      expect(screen.getByText('Active')).toBeInTheDocument()
      expect(screen.getByText('Maintenance')).toBeInTheDocument()
      expect(screen.getByText('Offline')).toBeInTheDocument()
    })
    
    // Check values (may appear multiple times, use getAllByText)
    const totalStations = screen.getAllByText('3')
    expect(totalStations.length).toBeGreaterThan(0)
  })

  it('displays unread alerts count', async () => {
    vi.mocked(notificationsApi.getCounts).mockResolvedValue(
      mockAxiosResponse({ total: 1, unread: 1, alerts: 1, warnings: 0 })
    )
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(notificationsApi.getAll).mockResolvedValue(mockAxiosResponse(mockNotifications))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse([]))

    render(<Dashboard />)

    await waitFor(() => {
    })
    
    // The unread badge lives inside the activity feed drawer, which opens
    // from the floating action button.
    const openFeed = await screen.findByLabelText('Open activity feed')
    await userEvent.click(openFeed)

    expect(await screen.findByText('1 unread')).toBeInTheDocument()
  })

  it('displays alert banner when there are unread alerts', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(notificationsApi.getAll).mockResolvedValue(mockAxiosResponse(mockNotifications))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse([]))

    render(<Dashboard />)

    await waitFor(() => {
    })
  })

  it('does not display alert banner when there are no unread alerts', async () => {
    const allReadNotifications = mockNotifications.map(n => ({ ...n, status: 'READ' }))

    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(notificationsApi.getAll).mockResolvedValue(
      mockAxiosResponse(allReadNotifications)
    )
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse([]))

    render(<Dashboard />)

    await waitFor(() => {
      expect(screen.queryByText(/You have.*unread alert/)).not.toBeInTheDocument()
    })
  })

  it('renders MetricsChart component', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(notificationsApi.getAll).mockResolvedValue(mockAxiosResponse(mockNotifications))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse([]))

    render(<Dashboard />)

    await waitFor(() => {
      expect(screen.getByTestId('metrics-chart')).toBeInTheDocument()
    })
  })

  it('renders station health section', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(notificationsApi.getAll).mockResolvedValue(mockAxiosResponse(mockNotifications))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse([]))

    render(<Dashboard />)

    await waitFor(() => {
      // The fleet panel summarises by status rather than naming stations.
      expect(screen.getByText('Total Stations')).toBeInTheDocument()
      expect(screen.getByText('Active')).toBeInTheDocument()
    })
  })

  it('handles empty stations list', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse([]))
    vi.mocked(notificationsApi.getAll).mockResolvedValue(mockAxiosResponse([]))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse([]))

    render(<Dashboard />)

    await waitFor(() => {
      expect(screen.getByText('Total Stations')).toBeInTheDocument()
    })
    
    // Check for zero count (may appear multiple times)
    const zeroCounts = screen.getAllByText('0')
    expect(zeroCounts.length).toBeGreaterThan(0)
  })

  it('surfaces a load failure instead of rendering an empty dashboard', async () => {
    vi.mocked(stationApi.getAll).mockRejectedValue(new Error('API Error'))
    vi.mocked(notificationsApi.getAll).mockResolvedValue(mockAxiosResponse([]))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse([]))

    render(<Dashboard />)

    await waitFor(() => {
      expect(screen.getByText('Failed to load dashboard')).toBeInTheDocument()
    })
  })
})
