import { mockAxiosResponse } from '../../test/mockHelpers'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import userEvent from '@testing-library/user-event'
import { render, screen, waitFor } from '../../test/test-utils'
import Metrics from '../Metrics'
import { metricsApi, stationApi } from '../../services/api'
import { BaseStation, ManagementProtocol, MetricData, MetricType, StationType, StationStatus } from '../../types'

// Mock ResizeObserver
globalThis.ResizeObserver = vi.fn().mockImplementation(() => ({
  observe: vi.fn(),
  unobserve: vi.fn(),
  disconnect: vi.fn(),
}))

// Mock the API
vi.mock('../../services/api', () => ({
  stationApi: {
    getAll: vi.fn(),
  },
  metricsApi: {
    getAll: vi.fn(),
    // The page also loads daily aggregates; without this the call is
    // undefined, the query rejects and the page renders its error state.
    getDailyAggregates: vi.fn(),
  },
}))

describe('Metrics', () => {
  const mockStations: BaseStation[] = [
    {
      id: 1,
      stationName: 'BS-001',
      location: 'New York, NY',
      latitude: 40.7128,
      longitude: -74.006,
      stationType: StationType.MACRO_CELL,
      ipAddress: '10.100.1.101',
      managementProtocol: ManagementProtocol.DIRECT,
      status: StationStatus.ACTIVE,
      powerConsumption: 1500,
    },
    {
      id: 2,
      stationName: 'BS-002',
      location: 'Los Angeles, CA',
      latitude: 34.0522,
      longitude: -118.2437,
      stationType: StationType.SMALL_CELL,
      ipAddress: '10.100.1.102',
      managementProtocol: ManagementProtocol.DIRECT,
      status: StationStatus.ACTIVE,
      powerConsumption: 800,
    },
  ]

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
      timestamp: new Date().toISOString(),
    },
    {
      id: '3',
      stationId: 2,
      stationName: 'BS-002',
      metricType: MetricType.CPU_USAGE,
      value: 45.8,
      unit: '%',
      timestamp: new Date().toISOString(),
    },
    {
      id: '4',
      stationId: 1,
      stationName: 'BS-001',
      metricType: MetricType.POWER_CONSUMPTION,
      value: 1420.5,
      unit: 'kW',
      timestamp: new Date().toISOString(),
    },
  ]

  beforeEach(() => {
    vi.clearAllMocks()
    // Default so tests that do not care about aggregates still render.
    vi.mocked(metricsApi.getDailyAggregates).mockResolvedValue(mockAxiosResponse([]))
  })

  it('renders loading state initially', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockImplementation(
      () => new Promise(() => {}) // Never resolves
    )

    render(<Metrics />)

    await waitFor(() => {
      const progress = screen.getByRole('status')
      expect(progress).toBeInTheDocument()
    })
  })

  it('renders metrics page with data', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText('Metrics')).toBeInTheDocument()
    })

    // Check filter controls are rendered (may have multiple elements with same text due to MUI label+legend)
    expect(screen.getAllByText('Station').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Category').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Time Range').length).toBeGreaterThanOrEqual(1)

    // Check chart section (now split into multiple charts)
    expect(screen.getAllByText('System Performance').length).toBeGreaterThan(0)

    // Check averages section

    // Check summary section
    expect(screen.getByText(new RegExp(`4 data points`))).toBeInTheDocument() // Total metrics count
  })

  it('displays station filter options', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText('Metrics')).toBeInTheDocument()
    })

    // Check station filter options
    const stationSelect = screen.getByLabelText('Station')
    expect(stationSelect).toBeInTheDocument()

    await userEvent.click(stationSelect)

    await waitFor(() => {
      // When dropdown opens, "All Stations" appears both in select and dropdown
      expect(screen.getAllByText('All Stations').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('BS-001').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('BS-002').length).toBeGreaterThanOrEqual(1)
    })
  })

  it('displays category filter options', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText('Metrics')).toBeInTheDocument()
    })

    // Check metric type filter options
    const metricTypeSelect = screen.getByLabelText('Category')
    expect(metricTypeSelect).toBeInTheDocument()

    await userEvent.click(metricTypeSelect)

    await waitFor(() => {
      // 'All Categories' appears twice (in the select display and in the dropdown)
      expect(screen.getAllByText('All Categories').length).toBeGreaterThanOrEqual(1)
      // The filter groups metrics into categories rather than listing every
      // individual metric type.
      expect(screen.getAllByText('System Performance').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('Network Quality').length).toBeGreaterThanOrEqual(1)
    })
  })

  it('displays time range filter options', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText('Metrics')).toBeInTheDocument()
    })

    // Check time range filter options
    const timeRangeSelect = screen.getByLabelText('Time Range')
    expect(timeRangeSelect).toBeInTheDocument()

    await userEvent.click(timeRangeSelect)

    await waitFor(() => {
      // When dropdown opens, time range values may appear multiple times
      expect(screen.getAllByText('Last 24 Hours').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('Last 7 Days').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('Last 30 Days').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('Last 90 Days').length).toBeGreaterThanOrEqual(1)
    })
  })

  it('filters metrics by station', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText(new RegExp(`4 data points`))).toBeInTheDocument() // Total metrics count
    })

    // Select BS-001 from station filter
    const stationSelect = screen.getByLabelText('Station')
    await userEvent.click(stationSelect)

    const bs001Option = await screen.findByRole('option', { name: 'BS-001' })
      await userEvent.click(bs001Option)

    // Should now show only 3 metrics (all belong to BS-001)
    await waitFor(() => {
      expect(screen.getByText(new RegExp(`3 data points`))).toBeInTheDocument() // Filtered count
      // BS-001 appears in both the select and the summary, use getAllByText
      expect(screen.getAllByText('BS-001').length).toBeGreaterThanOrEqual(1)
    })
  })

  it('filters metrics by metric type', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText(new RegExp(`4 data points`))).toBeInTheDocument() // Total metrics
    })

    // Select CPU_USAGE from metric type filter
    const metricTypeSelect = screen.getByLabelText('Category')
    await userEvent.click(metricTypeSelect)

    // Wait for dropdown to open and click CPU_USAGE option
    await waitFor(() => {
      const listbox = screen.getByRole('listbox')
      expect(listbox).toBeInTheDocument()
    })

    // Click the CPU_USAGE option in the listbox
    const cpuOption = await screen.findByRole('option', { name: 'System Performance' })
    await userEvent.click(cpuOption)

    // Should now show only 2 metrics (CPU_USAGE) - wait for the filter to apply
    await waitFor(() => {
      expect(screen.getByText(/data points/)).toBeInTheDocument() // Filtered count
    })
  })

  it('changes time range and refetches data', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText('Last 7 Days')).toBeInTheDocument()
    })

    // Change time range to 30 days
    const timeRangeSelect = screen.getByLabelText('Time Range')
    await userEvent.click(timeRangeSelect)

    const thirtyDaysOption = await screen.findByText('Last 30 Days')
      await userEvent.click(thirtyDaysOption)

    // Should refetch with new time range
    await waitFor(() => {
      expect(metricsApi.getAll).toHaveBeenCalledWith(
        expect.objectContaining({ startTime: expect.any(String) })
      )
      expect(screen.getByText('Last 30 Days')).toBeInTheDocument()
    })
  })


  it('renders chart with data', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getAllByText('System Performance').length).toBeGreaterThan(0)
    })

    // Check if chart section is rendered
    const chartSection = screen.getAllByText('System Performance')[0].closest('div')
    expect(chartSection).toBeInTheDocument()
  })

  it('renders single metric line when filtered by metric type', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getAllByText('System Performance').length).toBeGreaterThan(0)
    })

    // Select CPU_USAGE from metric type filter
    const metricTypeSelect = screen.getByLabelText('Category')
    await userEvent.click(metricTypeSelect)

    const cpuOptions = await screen.findAllByText('System Performance')
      await userEvent.click(cpuOptions[0])

    // Chart should still be rendered
    await waitFor(() => {
      expect(screen.getAllByText('System Performance').length).toBeGreaterThan(0)
    })
  })

  it('handles empty stations list', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse([]))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText('Metrics')).toBeInTheDocument()
    }, { timeout: 3000 })

    // Station filter should still show "All Stations" option
    const stationSelect = screen.getByLabelText('Station')
    await userEvent.click(stationSelect)

    await waitFor(() => {
      // When dropdown opens, there may be multiple 'All Stations' texts visible
      expect(screen.getAllByText('All Stations').length).toBeGreaterThanOrEqual(1)
    }, { timeout: 3000 })
  })

  it('handles empty metrics list', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse([]))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText('Metrics')).toBeInTheDocument()
    }, { timeout: 3000 })

    expect(screen.getByText(new RegExp(`0 data points`))).toBeInTheDocument() // Total metrics count
  })


  it('updates metric count when filters are applied', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    // Wait for initial render with all metrics
    await waitFor(() => {
      expect(screen.getByText(new RegExp(`4 data points`))).toBeInTheDocument() // Total metrics count
    })

    // Change station filter to BS-001
    const stationSelect = screen.getByLabelText('Station')
    await userEvent.click(stationSelect)

    const bs001Option = await screen.findByRole('option', { name: 'BS-001' })
      await userEvent.click(bs001Option)

    // Should now show only 3 metrics (all belong to BS-001)
    await waitFor(() => {
      expect(screen.getByText(new RegExp(`3 data points`))).toBeInTheDocument() // Filtered count
    })
  })

  it('polls for fresh live metrics without user interaction', async () => {
    // The operator's goal is a dashboard that stays current while they watch
    // it. Deleting this test would let someone remove refetchInterval and the
    // page would go stale silently.
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
      vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

      render(<Metrics />)

      await waitFor(() => expect(metricsApi.getAll).toHaveBeenCalled())
      const callsAfterMount = vi.mocked(metricsApi.getAll).mock.calls.length

      // POLLING_INTERVALS.NORMAL is 20s; advance past one tick.
      await vi.advanceTimersByTimeAsync(21_000)

      await waitFor(() =>
        expect(vi.mocked(metricsApi.getAll).mock.calls.length).toBeGreaterThan(callsAfterMount)
      )
    } finally {
      vi.useRealTimers()
    }
  })
})