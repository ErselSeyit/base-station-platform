import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '../../test/test-utils'
import MapView from '../MapView'
import { stationApi } from '../../services/api'
import { BaseStation, ManagementProtocol, StationStatus, StationType } from '../../types'
import { mockAxiosResponse } from '../../test/mockHelpers'

// Mock the API
vi.mock('../../services/api', () => ({
  stationApi: {
    getAll: vi.fn(),
  },
}))

// Mock useNavigate
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

// Mock react-leaflet components
vi.mock('react-leaflet', () => ({
  MapContainer: ({ children, ...props }: { children?: React.ReactNode; [key: string]: unknown }) => (
    <div data-testid="map-container" {...props}>
      {children}
    </div>
  ),
  Marker: ({ children, eventHandlers, ...props }: { children?: React.ReactNode; eventHandlers?: { click?: () => void }; position?: [number, number]; [key: string]: unknown }) => (
    <button
      type="button"
      data-testid="map-marker"
      data-position={`${(props.position as [number, number])?.[0]},${(props.position as [number, number])?.[1]}`}
      onClick={eventHandlers?.click}
      {...props}
    >
      {children}
    </button>
  ),
  Popup: ({ children }: { children?: React.ReactNode }) => (
    <div data-testid="map-popup">{children}</div>
  ),
  TileLayer: () => <div data-testid="tile-layer" />,
  useMap: () => ({
    setView: vi.fn(),
    setMaxBounds: vi.fn(),
    setMinZoom: vi.fn(),
    setMaxZoom: vi.fn(),
    fitBounds: vi.fn(),
    options: {},
  }),
}))

// Mock leaflet
vi.mock('leaflet', () => {
  const createMockBounds = () => ({
    getSouthWest: () => ({ lat: 0, lng: 0 }),
    getNorthEast: () => ({ lat: 1, lng: 1 }),
    contains: () => true,
    extend: function() { return this },
    pad: function() { return this },
    isValid: () => true,
  })
  
  return {
    default: {
      Icon: {
        Default: {
          prototype: {},
          mergeOptions: vi.fn(),
        },
      },
      latLng: (lat: number, lng: number) => ({ lat, lng }),
      latLngBounds: () => createMockBounds(),
    },
  }
})

describe('MapView', () => {
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
      status: StationStatus.MAINTENANCE,
      powerConsumption: 800,
    },
    {
      id: 3,
      stationName: 'BS-003',
      location: 'Invalid Location',
      latitude: 200, // Invalid latitude (> 90)
      longitude: -74.006,
      stationType: StationType.MACRO_CELL,
      ipAddress: '10.100.1.103',
      managementProtocol: ManagementProtocol.DIRECT,
      status: StationStatus.OFFLINE,
      powerConsumption: 0,
    },
    {
      id: 4,
      stationName: 'BS-004',
      location: 'Another Invalid',
      latitude: 40.7128,
      longitude: 200, // Invalid longitude (> 180)
      stationType: StationType.MICRO_CELL,
      ipAddress: '10.100.1.104',
      managementProtocol: ManagementProtocol.DIRECT,
      status: StationStatus.ACTIVE,
      powerConsumption: 1200,
    },
  ]

  beforeEach(() => {
    vi.clearAllMocks()
    mockNavigate.mockClear()
  })

  it('renders loading state initially', async () => {
    vi.mocked(stationApi.getAll).mockImplementation(
      () => new Promise(() => {}) // Never resolves
    )

    render(<MapView />)

    await waitFor(() => {
      const progress = screen.getByRole('progressbar')
      expect(progress).toBeInTheDocument()
    })
  })

  it('renders map view with valid stations', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<MapView />)

    await waitFor(() => {
      expect(screen.getByText('Map View')).toBeInTheDocument()
      expect(screen.getByText('Interactive map showing all base station locations')).toBeInTheDocument()
    })

    // Should render map container
    expect(screen.getByTestId('map-container')).toBeInTheDocument()

    // Should render markers only for valid stations (2 out of 4)
    const markers = screen.getAllByTestId('map-marker')
    expect(markers).toHaveLength(2)
  })

  it('shows warning alert for invalid coordinate stations', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<MapView />)

    await waitFor(() => {
      expect(screen.getByText('Map View')).toBeInTheDocument()
    })

    // Should show warning alert for 2 invalid stations
    expect(screen.getByText('2 station(s) have invalid coordinates and cannot be displayed on the map:')).toBeInTheDocument()

    // Should show details of invalid stations
    expect(screen.getByText('BS-003 (Invalid Location)')).toBeInTheDocument()
    expect(screen.getByText('BS-004 (Another Invalid)')).toBeInTheDocument()
  })

  it('shows edit button for invalid coordinate stations', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<MapView />)

    await waitFor(() => {
      expect(screen.getByText('Map View')).toBeInTheDocument()
    })

    // Should show edit buttons for invalid stations
    const editButtons = screen.getAllByRole('button', { name: /edit/i })
    expect(editButtons).toHaveLength(2)
  })

  it('navigates to station edit page when edit button is clicked', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<MapView />)

    await waitFor(() => {
      expect(screen.getByText('Map View')).toBeInTheDocument()
    })

    // Click edit button for first invalid station
    const editButtons = screen.getAllByRole('button', { name: /edit/i })
    fireEvent.click(editButtons[0])

    expect(mockNavigate).toHaveBeenCalledWith('/stations/3')
  })

  it('shows helpful hint for Istanbul locations', async () => {
    const istanbulStations = mockStations.map(s =>
      s.id === 3 ? { ...s, location: 'Istanbul, Turkey' } : s
    )

    vi.mocked(stationApi.getAll).mockResolvedValue(
      mockAxiosResponse(istanbulStations)
    )

    render(<MapView />)

    await waitFor(() => {
      expect(screen.getByText('Map View')).toBeInTheDocument()
    })

    // Should show Istanbul hint
    expect(screen.getByText('💡 For Istanbul, use: Lat 41.0064, Lng 28.9759')).toBeInTheDocument()
  })

  it('displays popup with station details when marker is clicked', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(
      mockAxiosResponse([mockStations[0]]) // Only valid station
    )

    render(<MapView />)

    await waitFor(() => {
      expect(screen.getByText('Map View')).toBeInTheDocument()
    })

    // Click on marker
    const marker = screen.getByTestId('map-marker')
    fireEvent.click(marker)

    // Should show selected station details
    await waitFor(() => {
      expect(screen.getByText('Selected Station: BS-001')).toBeInTheDocument()
      expect(screen.getByText('Location: New York, NY')).toBeInTheDocument()
      expect(screen.getByText('Coordinates: 40.7128, -74.006')).toBeInTheDocument()
      expect(screen.getByText('Type: MACRO_CELL')).toBeInTheDocument()
      expect(screen.getByText('Status: ACTIVE')).toBeInTheDocument()
      expect(screen.getByText('Power Consumption: 1500.0 kW')).toBeInTheDocument()
    })
  })

  it('calculates center point correctly for valid stations', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(
      mockAxiosResponse([mockStations[0], mockStations[1]]) // Only valid stations
    )

    render(<MapView />)

    await waitFor(() => {
      expect(screen.getByText('Map View')).toBeInTheDocument()
    })

    // Center should be average of valid stations
    // (40.7128 + 34.0522) / 2 = 37.3825 for latitude
    // (-74.0060 + -118.2437) / 2 = -96.12485 for longitude
    const mapContainer = screen.getByTestId('map-container')
    const centerAttr = mapContainer.getAttribute('center')
    expect(centerAttr).toMatch(/^37\.3825,-96\.1248/)
    expect(mapContainer).toHaveAttribute('zoom', '10')
  })

  it('uses default Istanbul center when no valid stations', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(
      mockAxiosResponse([mockStations[2], mockStations[3]]) // Only invalid stations
    )

    render(<MapView />)

    await waitFor(() => {
      expect(screen.getByText('Map View')).toBeInTheDocument()
    })

    // Should use default Istanbul coordinates
    const mapContainer = screen.getByTestId('map-container')
    expect(mapContainer).toHaveAttribute('center', '41.0064,28.9759')
    expect(mapContainer).toHaveAttribute('zoom', '4')
  })

  it('filters out stations with NaN coordinates', async () => {
    const stationsWithNaN = [
      ...mockStations,
      {
        id: 5,
        stationName: 'BS-005',
        location: 'NaN Location',
        latitude: Number.NaN,
        longitude: -74.006,
        stationType: StationType.MACRO_CELL,
        status: StationStatus.ACTIVE,
        powerConsumption: 1000,
      },
    ]

    vi.mocked(stationApi.getAll).mockResolvedValue(
      mockAxiosResponse(stationsWithNaN)
    )

    render(<MapView />)

    await waitFor(() => {
      expect(screen.getByText('Map View')).toBeInTheDocument()
    })

    // Should show warning for 3 invalid stations (2 original + 1 NaN)
    expect(screen.getByText('3 station(s) have invalid coordinates and cannot be displayed on the map:')).toBeInTheDocument()

    // Should still render markers for 2 valid stations
    const markers = screen.getAllByTestId('map-marker')
    expect(markers).toHaveLength(2)
  })

  it('handles empty stations list', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(
      mockAxiosResponse<BaseStation[]>([])
    )

    render(<MapView />)

    await waitFor(() => {
      expect(screen.getByText('Map View')).toBeInTheDocument()
    })

    // Should not show warning alert
    expect(screen.queryByText(/station\(s\) have invalid coordinates/)).not.toBeInTheDocument()

    // Should use default center
    const mapContainer = screen.getByTestId('map-container')
    expect(mapContainer).toHaveAttribute('center', '41.0064,28.9759')
    expect(mapContainer).toHaveAttribute('zoom', '4')

    // Should not render any markers
    expect(screen.queryByTestId('map-marker')).not.toBeInTheDocument()
  })

  it('renders tile layer', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<MapView />)

    await waitFor(() => {
      expect(screen.getByTestId('tile-layer')).toBeInTheDocument()
    })
  })

  it('does not show selected station card initially', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<MapView />)

    await waitFor(() => {
      expect(screen.getByText('Map View')).toBeInTheDocument()
    })

    // Should not show selected station card initially
    expect(screen.queryByText(/Selected Station:/)).not.toBeInTheDocument()
  })

  it('clears selected station when clicking different marker', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(
      mockAxiosResponse([mockStations[0], mockStations[1]]) // Only valid stations
    )

    render(<MapView />)

    await waitFor(() => {
      expect(screen.getByText('Map View')).toBeInTheDocument()
    })

    // Click first marker
    const markers = screen.getAllByTestId('map-marker')
    fireEvent.click(markers[0])

    await waitFor(() => {
      expect(screen.getByText('Selected Station: BS-001')).toBeInTheDocument()
    })

    // Click second marker
    fireEvent.click(markers[1])

    await waitFor(() => {
      expect(screen.getByText('Selected Station: BS-002')).toBeInTheDocument()
    })
  })

  it('displays correct status colors in selected station card', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(
      mockAxiosResponse([mockStations[1]]) // Station with MAINTENANCE status
    )

    render(<MapView />)

    await waitFor(() => {
      expect(screen.getByText('Map View')).toBeInTheDocument()
    })

    // Click marker
    const marker = screen.getByTestId('map-marker')
    fireEvent.click(marker)

    await waitFor(() => {
      expect(screen.getByText('Status: MAINTENANCE')).toBeInTheDocument()
    })
  })
})