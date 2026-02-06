import { mockAxiosResponse } from '../../test/mockHelpers'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '../../test/test-utils'
import Stations from '../Stations'
import { stationApi } from '../../services/api'
import { BaseStation, ManagementProtocol, StationStatus, StationType } from '../../types'

// Mock the API
vi.mock('../../services/api', () => ({
  stationApi: {
    getAll: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
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

// Mock window.confirm
const mockConfirm = vi.fn()
globalThis.confirm = mockConfirm

describe('Stations', () => {
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
      description: 'Main NYC station',
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
      description: 'LA backup station',
    },
    {
      id: 3,
      stationName: 'BS-003',
      location: 'Chicago, IL',
      latitude: 41.8781,
      longitude: -87.6298,
      stationType: StationType.MACRO_CELL,
      ipAddress: '10.100.1.103',
      managementProtocol: ManagementProtocol.DIRECT,
      status: StationStatus.OFFLINE,
      powerConsumption: 0,
      description: 'Chicago station',
    },
  ]

  beforeEach(() => {
    vi.clearAllMocks()
    mockNavigate.mockClear()
    mockConfirm.mockClear()
  })

  it('renders loading state initially', async () => {
    vi.mocked(stationApi.getAll).mockImplementation(
      () => new Promise(() => {}) // Never resolves
    )

    render(<Stations />)

    await waitFor(() => {
      const progress = screen.getByRole('progressbar')
      expect(progress).toBeInTheDocument()
    })
  })

  it('renders stations table with data', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getByText('Base Stations')).toBeInTheDocument()
    })

    // Check table headers
    expect(screen.getByText('ID')).toBeInTheDocument()
    expect(screen.getByText('Station Name')).toBeInTheDocument()
    expect(screen.getByText('Location')).toBeInTheDocument()
    expect(screen.getByText('Type')).toBeInTheDocument()
    expect(screen.getByText('Status')).toBeInTheDocument()
    expect(screen.getByText('Power (kW)')).toBeInTheDocument()
    expect(screen.getByText('Actions')).toBeInTheDocument()

    // Check station data is displayed
    expect(screen.getByText('BS-001')).toBeInTheDocument()
    expect(screen.getByText('New York, NY')).toBeInTheDocument()
    expect(screen.getAllByText('MACRO_CELL')).toHaveLength(2) // Two macro cells
    expect(screen.getByText('ACTIVE')).toBeInTheDocument()
    expect(screen.getByText('1500.0')).toBeInTheDocument()

    expect(screen.getByText('BS-002')).toBeInTheDocument()
    expect(screen.getByText('Los Angeles, CA')).toBeInTheDocument()
    expect(screen.getByText('SMALL_CELL')).toBeInTheDocument()
    expect(screen.getByText('MAINTENANCE')).toBeInTheDocument()
    expect(screen.getByText('800.0')).toBeInTheDocument()

    expect(screen.getByText('BS-003')).toBeInTheDocument()
    expect(screen.getByText('Chicago, IL')).toBeInTheDocument()
    expect(screen.getByText('OFFLINE')).toBeInTheDocument()
    expect(screen.getByText('0.0')).toBeInTheDocument()
  })

  it('displays empty state when no stations', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse([]))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getByText('No stations found. Click "Add Station" to create one.')).toBeInTheDocument()
    })
  })

  it('displays error message when API fails', async () => {
    const errorMessage = 'Failed to load stations'
    vi.mocked(stationApi.getAll).mockRejectedValue(new Error(errorMessage))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getByText(`Error loading stations: ${errorMessage}`)).toBeInTheDocument()
    })
  })

  it('opens create station dialog when Add Station button is clicked', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getByText('Base Stations')).toBeInTheDocument()
    })

    const addButton = screen.getByRole('button', { name: /add station/i })
    fireEvent.click(addButton)

    await waitFor(() => {
      expect(screen.getByText('New Station')).toBeInTheDocument()
      expect(screen.getByText('Configure a new base station')).toBeInTheDocument()
    })
  })

  it('opens edit station dialog when edit button is clicked', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getByText('BS-001')).toBeInTheDocument()
    })

    // Find and click the edit button for BS-001
    const editButtons = screen.getAllByLabelText('Edit')
    fireEvent.click(editButtons[0])

    await waitFor(() => {
      expect(screen.getByText('Edit Station')).toBeInTheDocument()
      expect(screen.getByText('Update station configuration')).toBeInTheDocument()
      expect(screen.getByDisplayValue('BS-001')).toBeInTheDocument()
    })
  })

  it('creates a new station successfully', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(stationApi.create).mockResolvedValue(mockAxiosResponse({}))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getByText('Base Stations')).toBeInTheDocument()
    })

    // Open create dialog
    const addButton = screen.getByRole('button', { name: /add station/i })
    fireEvent.click(addButton)

    await waitFor(() => {
      expect(screen.getByText('New Station')).toBeInTheDocument()
    })

    // Fill form
    const stationNameInput = screen.getByLabelText('Station Name')
    const locationInput = screen.getByLabelText('Location')
    const latitudeInput = screen.getByLabelText('Latitude')
    const longitudeInput = screen.getByLabelText('Longitude')
    const powerInput = screen.getByLabelText('Power Consumption')

    fireEvent.change(stationNameInput, { target: { value: 'BS-004' } })
    fireEvent.change(locationInput, { target: { value: 'Boston, MA' } })
    fireEvent.change(latitudeInput, { target: { value: '42.3601' } })
    fireEvent.change(longitudeInput, { target: { value: '-71.0589' } })
    fireEvent.change(powerInput, { target: { value: '1200' } })

    // Submit form
    const createButton = screen.getByRole('button', { name: /create station/i })
    fireEvent.click(createButton)

    await waitFor(() => {
      expect(stationApi.create).toHaveBeenCalledWith(
        {
          stationName: 'BS-004',
          location: 'Boston, MA',
          latitude: 42.3601,
          longitude: -71.0589,
          stationType: StationType.MACRO_CELL,
          status: StationStatus.ACTIVE,
          powerConsumption: 1200,
        },
        expect.any(Object) // QueryClient context
      )
    })
  })

  it('updates a station successfully', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(stationApi.update).mockResolvedValue(mockAxiosResponse({}))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getByText('BS-001')).toBeInTheDocument()
    })

    // Open edit dialog for BS-001
    const editButtons = screen.getAllByLabelText('Edit')
    fireEvent.click(editButtons[0])

    await waitFor(() => {
      expect(screen.getByDisplayValue('BS-001')).toBeInTheDocument()
    })

    // Update station name
    const stationNameInput = screen.getByDisplayValue('BS-001')
    fireEvent.change(stationNameInput, { target: { value: 'BS-001-Updated' } })

    // Submit form
    const updateButton = screen.getByRole('button', { name: /update station/i })
    fireEvent.click(updateButton)

    await waitFor(() => {
      expect(stationApi.update).toHaveBeenCalledWith(1, expect.any(Object))
      const updateCall = vi.mocked(stationApi.update).mock.calls[0]
      expect(updateCall[1]).toHaveProperty('stationName', 'BS-001-Updated')
    })
  })

  it('deletes a station after confirmation', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(stationApi.delete).mockResolvedValue(mockAxiosResponse({}))
    mockConfirm.mockReturnValue(true)

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getByText('BS-001')).toBeInTheDocument()
    })

    // Click delete button for BS-001
    const deleteButtons = screen.getAllByLabelText('Delete')
    fireEvent.click(deleteButtons[0])

    await waitFor(() => {
      expect(mockConfirm).toHaveBeenCalledWith('Are you sure you want to delete this station?')
      expect(stationApi.delete).toHaveBeenCalledWith(1, expect.any(Object))
    })
  })

  it('does not delete station when confirmation is cancelled', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    mockConfirm.mockReturnValue(false)

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getByText('BS-001')).toBeInTheDocument()
    })

    // Click delete button for BS-001
    const deleteButtons = screen.getAllByLabelText('Delete')
    fireEvent.click(deleteButtons[0])

    await waitFor(() => {
      expect(mockConfirm).toHaveBeenCalledWith('Are you sure you want to delete this station?')
      expect(stationApi.delete).not.toHaveBeenCalled()
    })
  })

  it('navigates to station detail when view button is clicked', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getByText('BS-001')).toBeInTheDocument()
    })

    // Click view button for BS-001
    const viewButtons = screen.getAllByLabelText('View Details')
    fireEvent.click(viewButtons[0])

    expect(mockNavigate).toHaveBeenCalledWith('/stations/1')
  })

  it('disables create/update button when required fields are empty', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getByText('Base Stations')).toBeInTheDocument()
    })

    // Open create dialog
    const addButton = screen.getByRole('button', { name: /add station/i })
    fireEvent.click(addButton)

    await waitFor(() => {
      expect(screen.getByText('New Station')).toBeInTheDocument()
    })

    // Check that create button is disabled initially
    const createButton = screen.getByRole('button', { name: /create station/i })
    expect(createButton).toBeDisabled()

    // Fill required fields
    const stationNameInput = screen.getByLabelText('Station Name')
    const locationInput = screen.getByLabelText('Location')

    fireEvent.change(stationNameInput, { target: { value: 'BS-004' } })
    fireEvent.change(locationInput, { target: { value: 'Boston, MA' } })

    // Button should now be enabled
    await waitFor(() => {
      expect(createButton).not.toBeDisabled()
    })
  })

  it('closes dialog when cancel button is clicked', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getByText('Base Stations')).toBeInTheDocument()
    })

    // Open create dialog
    const addButton = screen.getByRole('button', { name: /add station/i })
    fireEvent.click(addButton)

    await waitFor(() => {
      expect(screen.getByText('New Station')).toBeInTheDocument()
    })

    // Click cancel
    const cancelButton = screen.getByRole('button', { name: /cancel/i })
    fireEvent.click(cancelButton)

    await waitFor(() => {
      expect(screen.queryByText('New Station')).not.toBeInTheDocument()
    })
  })

  it('closes dialog when close icon is clicked', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getByText('Base Stations')).toBeInTheDocument()
    })

    // Open create dialog
    const addButton = screen.getByRole('button', { name: /add station/i })
    fireEvent.click(addButton)

    await waitFor(() => {
      expect(screen.getByText('New Station')).toBeInTheDocument()
    })

    // Find and click close button (IconButton with close icon)
    const dialog = screen.getByRole('dialog')
    const closeButtons = dialog.querySelectorAll('button')

    // Look for the close button (usually the first button in the dialog header)
    let closeButton = null
    for (const button of Array.from(closeButtons)) {
      const svg = button.querySelector('svg')
      if (svg && button.getAttribute('aria-label')?.includes('close')) {
        closeButton = button
        break
      }
    }

    // If not found by aria-label, try the first button in the dialog
    if (!closeButton && closeButtons.length > 0) {
      closeButton = closeButtons[0]
    }

    if (closeButton) {
      fireEvent.click(closeButton)

      await waitFor(() => {
        expect(screen.queryByText('New Station')).not.toBeInTheDocument()
      })
    }
  })

  it('shows loading spinner on create button during submission', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(stationApi.create).mockImplementation(
      () => new Promise(() => {}) // Never resolves
    )

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getByText('Base Stations')).toBeInTheDocument()
    })

    // Open create dialog
    const addButton = screen.getByRole('button', { name: /add station/i })
    fireEvent.click(addButton)

    await waitFor(() => {
      expect(screen.getByText('New Station')).toBeInTheDocument()
    })

    // Fill required fields
    const stationNameInput = screen.getByLabelText('Station Name')
    const locationInput = screen.getByLabelText('Location')

    fireEvent.change(stationNameInput, { target: { value: 'BS-004' } })
    fireEvent.change(locationInput, { target: { value: 'Boston, MA' } })

    // Submit form
    const createButton = screen.getByRole('button', { name: /create station/i })
    fireEvent.click(createButton)

    // Check for loading spinner
    await waitFor(() => {
      const spinner = createButton.querySelector('.MuiCircularProgress-root')
      expect(spinner).toBeInTheDocument()
    })
  })

  it('pre-fills form data when editing a station', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getByText('BS-001')).toBeInTheDocument()
    })

    // Open edit dialog for BS-001 (first station)
    const editButtons = screen.getAllByLabelText('Edit')
    fireEvent.click(editButtons[0])

    await waitFor(() => {
      expect(screen.getByDisplayValue('BS-001')).toBeInTheDocument()
      expect(screen.getByDisplayValue('New York, NY')).toBeInTheDocument()
      expect(screen.getByDisplayValue('40.7128')).toBeInTheDocument()
      expect(screen.getByDisplayValue('-74.006')).toBeInTheDocument()
      expect(screen.getByDisplayValue('1500')).toBeInTheDocument()
    })
  })
})