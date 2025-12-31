import { mockAxiosResponse } from '../../test/mockHelpers'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '../../test/test-utils'
import Metrics from '../Metrics'
import { metricsApi, stationApi } from '../../services/api'
import { BaseStation, MetricData, MetricType, StationType, StationStatus } from '../../types'

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
  })

  it('renders loading state initially', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockImplementation(
      () => new Promise(() => {}) // Never resolves
    )

    render(<Metrics />)

    await waitFor(() => {
      const progress = screen.getByRole('progressbar')
      expect(progress).toBeInTheDocument()
    })
  })

  it('renders metrics page with data', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText('Metrics & Analytics')).toBeInTheDocument()
    })

    // Check filter controls are rendered (may have multiple elements with same text due to MUI label+legend)
    expect(screen.getAllByText('Station').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Metric Type').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Time Range').length).toBeGreaterThanOrEqual(1)

    // Check chart section (now split into multiple charts)
    expect(screen.getByText('Performance Metrics (CPU, Memory, Temperature, Uptime)')).toBeInTheDocument()

    // Check averages section
    expect(screen.getByText('Average Values')).toBeInTheDocument()

    // Check summary section
    expect(screen.getByText('Summary')).toBeInTheDocument()
    expect(screen.getByText('4')).toBeInTheDocument() // Total metrics count
  })

  it('displays station filter options', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText('Metrics & Analytics')).toBeInTheDocument()
    })

    // Check station filter options
    const stationSelect = screen.getByRole('combobox', { name: /station/i })
    expect(stationSelect).toBeInTheDocument()

    fireEvent.mouseDown(stationSelect)

    await waitFor(() => {
      // When dropdown opens, "All Stations" appears both in select and dropdown
      expect(screen.getAllByText('All Stations').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('BS-001').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('BS-002').length).toBeGreaterThanOrEqual(1)
    })
  })

  it('displays metric type filter options', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText('Metrics & Analytics')).toBeInTheDocument()
    })

    // Check metric type filter options
    const metricTypeSelect = screen.getByRole('combobox', { name: /metric type/i })
    expect(metricTypeSelect).toBeInTheDocument()

    fireEvent.mouseDown(metricTypeSelect)

    await waitFor(() => {
      // 'All Metrics' appears twice (in the select display and in the dropdown)
      expect(screen.getAllByText('All Metrics').length).toBeGreaterThanOrEqual(1)
      // These may appear multiple times (dropdown and table), check at least one exists
      expect(screen.getAllByText('CPU_USAGE').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('MEMORY_USAGE').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('POWER_CONSUMPTION').length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText('TEMPERATURE').length).toBeGreaterThanOrEqual(1)
    })
  })

  it('displays time range filter options', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText('Metrics & Analytics')).toBeInTheDocument()
    })

    // Check time range filter options
    const timeRangeSelect = screen.getByRole('combobox', { name: /time range/i })
    expect(timeRangeSelect).toBeInTheDocument()

    fireEvent.mouseDown(timeRangeSelect)

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
      expect(screen.getByText('4')).toBeInTheDocument() // Total metrics count
    })

    // Select BS-001 from station filter
    const stationSelect = screen.getByRole('combobox', { name: /station/i })
    fireEvent.mouseDown(stationSelect)

    await waitFor(() => {
      // Use getByRole to select from the dropdown options
      const bs001Option = screen.getByRole('option', { name: 'BS-001' })
      fireEvent.click(bs001Option)
    })

    // Should now show only 3 metrics (all belong to BS-001)
    await waitFor(() => {
      expect(screen.getByText('3')).toBeInTheDocument() // Filtered count
      // BS-001 appears in both the select and the summary, use getAllByText
      expect(screen.getAllByText('BS-001').length).toBeGreaterThanOrEqual(1)
    })
  })

  it('filters metrics by metric type', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText('4')).toBeInTheDocument() // Total metrics
    })

    // Select CPU_USAGE from metric type filter
    const metricTypeSelect = screen.getByRole('combobox', { name: /metric type/i })
    fireEvent.mouseDown(metricTypeSelect)

    // Wait for dropdown to open and click CPU_USAGE option
    await waitFor(() => {
      const listbox = screen.getByRole('listbox')
      expect(listbox).toBeInTheDocument()
    })

    // Click the CPU_USAGE option in the listbox
    const cpuOption = screen.getByRole('option', { name: 'CPU_USAGE' })
    fireEvent.click(cpuOption)

    // Should now show only 2 metrics (CPU_USAGE) - wait for the filter to apply
    await waitFor(() => {
      expect(screen.getByText('2')).toBeInTheDocument() // Filtered count
    })
  })

  it('changes time range and refetches data', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText('Last 7 days')).toBeInTheDocument()
    })

    // Change time range to 30 days
    const timeRangeSelect = screen.getByRole('combobox', { name: /time range/i })
    fireEvent.mouseDown(timeRangeSelect)

    await waitFor(() => {
      const thirtyDaysOption = screen.getByText('Last 30 Days')
      fireEvent.click(thirtyDaysOption)
    })

    // Should refetch with new time range
    await waitFor(() => {
      expect(metricsApi.getAll).toHaveBeenCalledWith({
        startTime: expect.any(String),
      })
      expect(screen.getByText('Last 30 days')).toBeInTheDocument()
    })
  })

  it('calculates and displays average values', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText('Average Values')).toBeInTheDocument()
    })

    // Check average calculations - now display as lowercase with units
    expect(screen.getByText('cpu usage')).toBeInTheDocument()
    expect(screen.getByText('60.65%')).toBeInTheDocument() // (75.5 + 45.8) / 2

    expect(screen.getByText('memory usage')).toBeInTheDocument()
    expect(screen.getByText('60.20%')).toBeInTheDocument() // 60.2 / 1

    expect(screen.getByText('power consumption')).toBeInTheDocument()
    expect(screen.getByText('1.42 kW')).toBeInTheDocument() // 1420.5 W -> 1.42 kW
  })

  it('displays no data message when no averages available', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse([]))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText('No data available')).toBeInTheDocument()
    })
  })

  it('renders chart with data', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText('Performance Metrics (CPU, Memory, Temperature, Uptime)')).toBeInTheDocument()
    })

    // Check if chart section is rendered
    const chartSection = screen.getByText('Performance Metrics (CPU, Memory, Temperature, Uptime)').closest('div')
    expect(chartSection).toBeInTheDocument()
  })

  it('renders single metric line when filtered by metric type', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText('Performance Metrics (CPU, Memory, Temperature, Uptime)')).toBeInTheDocument()
    })

    // Select CPU_USAGE from metric type filter
    const metricTypeSelect = screen.getByRole('combobox', { name: /metric type/i })
    fireEvent.mouseDown(metricTypeSelect)

    await waitFor(() => {
      const cpuOptions = screen.getAllByText('CPU_USAGE')
      fireEvent.click(cpuOptions[0])
    })

    // Chart should still be rendered
    await waitFor(() => {
      expect(screen.getByText('Performance Metrics (CPU, Memory, Temperature, Uptime)')).toBeInTheDocument()
    })
  })

  it('handles empty stations list', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse([]))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText('Metrics & Analytics')).toBeInTheDocument()
    }, { timeout: 3000 })

    // Station filter should still show "All Stations" option
    const stationSelect = screen.getByRole('combobox', { name: /station/i })
    fireEvent.mouseDown(stationSelect)

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
      expect(screen.getByText('Metrics & Analytics')).toBeInTheDocument()
    }, { timeout: 3000 })

    expect(screen.getByText('0')).toBeInTheDocument() // Total metrics count
  })

  it.skip('refetches data automatically every 30 seconds', async () => {
    vi.useFakeTimers()
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(metricsApi.getAll).toHaveBeenCalledTimes(1)
    })

    // Advance time by 30 seconds
    vi.advanceTimersByTime(30000)

    // Wait for refetch to happen
    await new Promise(resolve => setTimeout(resolve, 100))

    expect(metricsApi.getAll).toHaveBeenCalledTimes(2)

    vi.useRealTimers()
  })

  it.skip('updates summary information correctly', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(metricsApi.getAll).mockResolvedValue(mockAxiosResponse(mockMetrics))

    render(<Metrics />)

    await waitFor(() => {
      expect(screen.getByText('Time Range: Last 7 days')).toBeInTheDocument()
    })

    // Change filters
    const stationSelect = screen.getByRole('combobox', { name: /station/i })
    fireEvent.mouseDown(stationSelect)

    await waitFor(() => {
      const bs001Option = screen.getByText('BS-001')
      fireEvent.click(bs001Option)
    })

    await waitFor(() => {
      expect(screen.getByText('Station: BS-001')).toBeInTheDocument()
      expect(screen.getByText('Total Metrics: 3')).toBeInTheDocument()
    })
  })
})