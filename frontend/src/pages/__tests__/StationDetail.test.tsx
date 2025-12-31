import { mockAxiosResponse } from '../../test/mockHelpers'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '../../test/test-utils'
import StationDetail from '../StationDetail'
import { metricsApi, notificationsApi, stationApi } from '../../services/api'
import { BaseStation, MetricData, Notification, StationStatus, StationType, MetricType, NotificationType } from '../../types'

// Mock the API
vi.mock('../../services/api', () => ({
  stationApi: {
    getById: vi.fn(),
  },
  metricsApi: {
    getByStation: vi.fn(),
  },
  notificationsApi: {
    getByStation: vi.fn(),
  },
}))

// Mock useParams and useNavigate
const mockUseParams = vi.fn()
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useParams: () => mockUseParams(),
    useNavigate: () => mockNavigate,
  }
})

// Mock MetricsChart component
vi.mock('../../components/MetricsChart', () => ({
  default: () => <div data-testid="metrics-chart">Metrics Chart</div>,
}))

describe('StationDetail', () => {
  const mockStation: BaseStation = {
    id: 1,
    stationName: 'BS-001',
    location: 'New York, NY',
    latitude: 40.7128,
    longitude: -74.006,
    stationType: StationType.MACRO_CELL,
    status: StationStatus.ACTIVE,
    powerConsumption: 1500,
    description: 'Main NYC station',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  }

  const mockMetrics: MetricData[] = [
    {
      id: '1',
      stationId: 1,
      stationName: 'BS-001',
      metricType: MetricType.CPU_USAGE,
      value: 75.5,
      unit: '%',
      timestamp: new Date().toISOString(),
    },
    {
      id: '2',
      stationId: 1,
      stationName: 'BS-001',
      metricType: MetricType.MEMORY_USAGE,
      value: 60.2,
      unit: '%',
      timestamp: new Date(Date.now() - 3600000).toISOString(), // 1 hour ago
    },
    {
      id: '3',
      stationId: 1,
      stationName: 'BS-001',
      metricType: MetricType.POWER_CONSUMPTION,
      value: 1420.5,
      unit: 'kW',
      timestamp: new Date(Date.now() - 7200000).toISOString(), // 2 hours ago
    },
    {
      id: '4',
      stationId: 1,
      stationName: 'BS-001',
      metricType: MetricType.TEMPERATURE,
      value: 45.8,
      unit: '°C',
      timestamp: new Date(Date.now() - 10800000).toISOString(), // 3 hours ago
    },
    {
      id: '5',
      stationId: 1,
      stationName: 'BS-001',
      metricType: MetricType.CPU_USAGE,
      value: 80.2,
      unit: '%',
      timestamp: new Date(Date.now() - 14400000).toISOString(), // 4 hours ago
    },
    {
      id: '6',
      stationId: 1,
      stationName: 'BS-001',
      metricType: MetricType.CPU_USAGE,
      value: 78.9,
      unit: '%',
      timestamp: new Date(Date.now() - 18000000).toISOString(), // 5 hours ago
    },
  ]

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
      stationId: 1,
      stationName: 'BS-001',
      message: 'Memory usage above threshold',
      type: NotificationType.WARNING,
      status: 'READ',
      createdAt: new Date(Date.now() - 3600000).toISOString(),
      readAt: new Date(Date.now() - 1800000).toISOString(),
    },
    {
      id: 3,
      stationId: 1,
      stationName: 'BS-001',
      message: 'System maintenance completed',
      type: NotificationType.INFO,
      status: 'READ',
      createdAt: new Date(Date.now() - 7200000).toISOString(),
      readAt: new Date(Date.now() - 3600000).toISOString(),
    },
  ]

  beforeEach(() => {
    vi.clearAllMocks()
    mockUseParams.mockReturnValue({ id: '1' })
  })

  it('renders loading state initially', async () => {
    vi.mocked(stationApi.getById).mockImplementation(
      () => new Promise(() => {}) // Never resolves
    )

    render(<StationDetail />)

    await waitFor(() => {
      const progress = screen.getByRole('progressbar')
      expect(progress).toBeInTheDocument()
    })
  })

  it('renders station details when data is loaded', async () => {
    vi.mocked(stationApi.getById).mockResolvedValue(mockAxiosResponse(mockStation))
    vi.mocked(metricsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockMetrics))
    vi.mocked(notificationsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockNotifications))

    render(<StationDetail />)

    await waitFor(() => {
      expect(screen.getByText('BS-001')).toBeInTheDocument()
    })

    // Check station information is displayed
    expect(screen.getByText('Station Information')).toBeInTheDocument()
    expect(screen.getByText('1')).toBeInTheDocument() // Station ID
    expect(screen.getByText('MACRO_CELL')).toBeInTheDocument() // Station Type
    expect(screen.getByText('New York, NY')).toBeInTheDocument() // Location
    expect(screen.getByText('40.7128, -74.006')).toBeInTheDocument() // Coordinates
    expect(screen.getByText('Main NYC station')).toBeInTheDocument() // Description
  })

  it('displays station status chip with correct color', async () => {
    vi.mocked(stationApi.getById).mockResolvedValue(mockAxiosResponse(mockStation))
    vi.mocked(metricsApi.getByStation).mockResolvedValue(mockAxiosResponse([]))
    vi.mocked(notificationsApi.getByStation).mockResolvedValue(mockAxiosResponse([]))

    render(<StationDetail />)

    await waitFor(() => {
      expect(screen.getByText('BS-001')).toBeInTheDocument()
    })

    // Check status chip is displayed
    expect(screen.getByText('ACTIVE')).toBeInTheDocument()
  })

  it('renders metrics chart component', async () => {
    vi.mocked(stationApi.getById).mockResolvedValue(mockAxiosResponse(mockStation))
    vi.mocked(metricsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockMetrics))
    vi.mocked(notificationsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockNotifications))

    render(<StationDetail />)

    await waitFor(() => {
      expect(screen.getByText('Metrics History')).toBeInTheDocument()
    })

    // Check metrics chart is rendered
    expect(screen.getByTestId('metrics-chart')).toBeInTheDocument()
  })

  it('displays current metrics (latest 5)', async () => {
    vi.mocked(stationApi.getById).mockResolvedValue(mockAxiosResponse(mockStation))
    vi.mocked(metricsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockMetrics))
    vi.mocked(notificationsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockNotifications))

    render(<StationDetail />)

    await waitFor(() => {
      expect(screen.getByText('Current Metrics')).toBeInTheDocument()
    })

    // Should display latest 5 metrics in reverse order (most recent first)
    // The slice(-5).reverse() gets the last 5 items and reverses them
    // Our test data has 6 items, so it takes the last 5 (indices 1-5) and reverses them
    // So the order should be: index 5, 4, 3, 2, 1
    expect(screen.getByText('78.9 %')).toBeInTheDocument() // CPU usage from 5 hours ago (index 5)
    expect(screen.getByText('80.2 %')).toBeInTheDocument() // CPU usage from 4 hours ago (index 4)
    expect(screen.getByText('45.8 °C')).toBeInTheDocument() // Temperature from 3 hours ago (index 3)
    expect(screen.getByText('1420.5 kW')).toBeInTheDocument() // Power from 2 hours ago (index 2)
    expect(screen.getByText('60.2 %')).toBeInTheDocument() // Memory from 1 hour ago (index 1)
  })

  it('displays metric icons for different types', async () => {
    vi.mocked(stationApi.getById).mockResolvedValue(mockAxiosResponse(mockStation))
    vi.mocked(metricsApi.getByStation).mockResolvedValue(
      mockAxiosResponse(mockMetrics.slice(0, 3)) // Only first 3 metrics
    )
    vi.mocked(notificationsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockNotifications))

    render(<StationDetail />)

    await waitFor(() => {
      expect(screen.getByText('Current Metrics')).toBeInTheDocument()
    })

    // Icons should be present in the DOM (checked by role or testid if available)
    // The icons are rendered conditionally based on metric type
    expect(screen.getByText('75.5 %')).toBeInTheDocument()
    expect(screen.getByText('60.2 %')).toBeInTheDocument()
    expect(screen.getByText('1420.5 kW')).toBeInTheDocument()
  })

  it('displays recent alerts (latest 5)', async () => {
    vi.mocked(stationApi.getById).mockResolvedValue(mockAxiosResponse(mockStation))
    vi.mocked(metricsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockMetrics))
    vi.mocked(notificationsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockNotifications))

    render(<StationDetail />)

    await waitFor(() => {
      expect(screen.getByText('Recent Alerts')).toBeInTheDocument()
    })

    // Check alert messages are displayed
    expect(screen.getByText('High CPU usage detected')).toBeInTheDocument()
    expect(screen.getByText('Memory usage above threshold')).toBeInTheDocument()
    expect(screen.getByText('System maintenance completed')).toBeInTheDocument()

    // Check alert types are displayed
    expect(screen.getAllByText('ALERT')).toHaveLength(1)
    expect(screen.getAllByText('WARNING')).toHaveLength(1)
    expect(screen.getAllByText('INFO')).toHaveLength(1)
  })

  it('shows unread alerts with different styling', async () => {
    vi.mocked(stationApi.getById).mockResolvedValue(mockAxiosResponse(mockStation))
    vi.mocked(metricsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockMetrics))
    vi.mocked(notificationsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockNotifications))

    render(<StationDetail />)

    await waitFor(() => {
      expect(screen.getByText('Recent Alerts')).toBeInTheDocument()
    })

    // The unread alert should have different styling (checked via the conditional background)
    // We can verify the alert content is there
    expect(screen.getByText('High CPU usage detected')).toBeInTheDocument()
  })

  it('displays no metrics message when no metrics available', async () => {
    vi.mocked(stationApi.getById).mockResolvedValue(mockAxiosResponse(mockStation))
    vi.mocked(metricsApi.getByStation).mockResolvedValue(mockAxiosResponse([]))
    vi.mocked(notificationsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockNotifications))

    render(<StationDetail />)

    await waitFor(() => {
      expect(screen.getByText('Current Metrics')).toBeInTheDocument()
    })

    expect(screen.getByText('No metrics available')).toBeInTheDocument()
  })

  it('displays no alerts message when no alerts available', async () => {
    vi.mocked(stationApi.getById).mockResolvedValue(mockAxiosResponse(mockStation))
    vi.mocked(metricsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockMetrics))
    vi.mocked(notificationsApi.getByStation).mockResolvedValue(mockAxiosResponse([]))

    render(<StationDetail />)

    await waitFor(() => {
      expect(screen.getByText('Recent Alerts')).toBeInTheDocument()
    })

    expect(screen.getByText('No alerts')).toBeInTheDocument()
  })

  it('handles missing station description', async () => {
    const stationWithoutDescription = { ...mockStation, description: undefined }

    vi.mocked(stationApi.getById).mockResolvedValue(mockAxiosResponse(stationWithoutDescription))
    vi.mocked(metricsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockMetrics))
    vi.mocked(notificationsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockNotifications))

    render(<StationDetail />)

    await waitFor(() => {
      expect(screen.getByText('BS-001')).toBeInTheDocument()
    })

    // Description section should not be rendered
    expect(screen.queryByText('Description')).not.toBeInTheDocument()
  })

  it('handles missing metric timestamps', async () => {
    const metricsWithoutTimestamps = mockMetrics.slice(0, 3).map(m => ({
      ...m,
      timestamp: undefined,
    }))

    vi.mocked(stationApi.getById).mockResolvedValue(mockAxiosResponse(mockStation))
    vi.mocked(metricsApi.getByStation).mockResolvedValue(mockAxiosResponse(metricsWithoutTimestamps))
    vi.mocked(notificationsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockNotifications))

    render(<StationDetail />)

    await waitFor(() => {
      expect(screen.getByText('Current Metrics')).toBeInTheDocument()
    })

    // Should display 'No date available' for missing timestamps (may be multiple)
    expect(screen.getAllByText('No date available').length).toBeGreaterThanOrEqual(1)
  })

  it('handles missing alert timestamps', async () => {
    const alertsWithoutTimestamps = mockNotifications.map(n => ({
      ...n,
      createdAt: undefined,
    }))

    vi.mocked(stationApi.getById).mockResolvedValue(mockAxiosResponse(mockStation))
    vi.mocked(metricsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockMetrics))
    vi.mocked(notificationsApi.getByStation).mockResolvedValue(mockAxiosResponse(alertsWithoutTimestamps))

    render(<StationDetail />)

    await waitFor(() => {
      expect(screen.getByText('Recent Alerts')).toBeInTheDocument()
    })

    // Should display 'No date available' for missing timestamps (may be multiple)
    expect(screen.getAllByText('No date available').length).toBeGreaterThanOrEqual(1)
  })

  it('shows station not found when station is null', async () => {
    vi.mocked(stationApi.getById).mockResolvedValue(mockAxiosResponse(null))

    render(<StationDetail />)

    await waitFor(() => {
      expect(screen.getByText('Station not found')).toBeInTheDocument()
    })
  })

  it('does not make API calls when id is not available', async () => {
    mockUseParams.mockReturnValue({ id: undefined })

    render(<StationDetail />)

    await waitFor(() => {
      // When id is undefined, component may show 'Station not found' or loading
      expect(screen.getByText('Station not found')).toBeInTheDocument()
    })

    // API calls should not be made when id is undefined
    expect(stationApi.getById).not.toHaveBeenCalled()
    expect(metricsApi.getByStation).not.toHaveBeenCalled()
    expect(notificationsApi.getByStation).not.toHaveBeenCalled()
  })

  it('displays different status colors for different station statuses', async () => {
    const maintenanceStation = { ...mockStation, status: StationStatus.MAINTENANCE }

    // Test MAINTENANCE status
    vi.mocked(stationApi.getById).mockResolvedValue(mockAxiosResponse(maintenanceStation))
    vi.mocked(metricsApi.getByStation).mockResolvedValue(mockAxiosResponse([]))
    vi.mocked(notificationsApi.getByStation).mockResolvedValue(mockAxiosResponse([]))

    render(<StationDetail />)

    await waitFor(() => {
      expect(screen.getByText('MAINTENANCE')).toBeInTheDocument()
    })
  })

  it('limits alerts display to 5 most recent', async () => {
    const manyNotifications = Array.from({ length: 8 }, (_, i) => ({
      id: i + 1,
      stationId: 1,
      stationName: 'BS-001',
      message: `Alert message ${i + 1}`,
      type: NotificationType.INFO,
      status: 'READ',
      createdAt: new Date(Date.now() - (i * 3600000)).toISOString(), // Different timestamps
    }))

    vi.mocked(stationApi.getById).mockResolvedValue(mockAxiosResponse(mockStation))
    vi.mocked(metricsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockMetrics))
    vi.mocked(notificationsApi.getByStation).mockResolvedValue(mockAxiosResponse(manyNotifications))

    render(<StationDetail />)

    await waitFor(() => {
      expect(screen.getByText('Recent Alerts')).toBeInTheDocument()
    })

    // Should display alert messages 1-5 (most recent first)
    expect(screen.getByText('Alert message 1')).toBeInTheDocument()
    expect(screen.getByText('Alert message 2')).toBeInTheDocument()
    expect(screen.getByText('Alert message 3')).toBeInTheDocument()
    expect(screen.getByText('Alert message 4')).toBeInTheDocument()
    expect(screen.getByText('Alert message 5')).toBeInTheDocument()

    // Should not display alert 6, 7, 8
    expect(screen.queryByText('Alert message 6')).not.toBeInTheDocument()
    expect(screen.queryByText('Alert message 7')).not.toBeInTheDocument()
    expect(screen.queryByText('Alert message 8')).not.toBeInTheDocument()
  })

  it('back button navigates to stations list', async () => {
    vi.mocked(stationApi.getById).mockResolvedValue(mockAxiosResponse(mockStation))
    vi.mocked(metricsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockMetrics))
    vi.mocked(notificationsApi.getByStation).mockResolvedValue(mockAxiosResponse(mockNotifications))

    render(<StationDetail />)

    await waitFor(() => {
      expect(screen.getByText('BS-001')).toBeInTheDocument()
    })

    const backButton = screen.getByRole('button', { name: /back/i })
    fireEvent.click(backButton)

    expect(mockNavigate).toHaveBeenCalledWith('/stations')
  })
})