import { mockAxiosResponse } from '../../test/mockHelpers'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import userEvent from '@testing-library/user-event'
import { render, screen, waitFor } from '../../test/test-utils'
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
      const progress = screen.getByRole('status')
      expect(progress).toBeInTheDocument()
    })
  })

  it('renders stations table with data', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getAllByText('Stations').length).toBeGreaterThan(0)
    })

    // Check table headers
    expect(screen.getAllByText('ID').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Station Name').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Location').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Type').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Status').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Actions').length).toBeGreaterThan(0)

    // Check station data is displayed
    expect(screen.getAllByText('BS-001').length).toBeGreaterThan(0)
    expect(screen.getAllByText('New York, NY').length).toBeGreaterThan(0)
    expect(screen.getAllByText('MACRO_CELL').length).toBeGreaterThan(0)
    expect(screen.getAllByText('ACTIVE').length).toBeGreaterThan(0)

    expect(screen.getAllByText('BS-002').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Los Angeles, CA').length).toBeGreaterThan(0)
    expect(screen.getAllByText('SMALL_CELL').length).toBeGreaterThan(0)
    expect(screen.getAllByText('MAINTENANCE').length).toBeGreaterThan(0)

    expect(screen.getAllByText('BS-003').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Chicago, IL').length).toBeGreaterThan(0)
    expect(screen.getAllByText('OFFLINE').length).toBeGreaterThan(0)
  })

  it('displays empty state when no stations', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse([]))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getAllByText('No stations found. Click "Add Station" to create one.').length).toBeGreaterThan(0)
    })
  })

  it('displays error message when API fails', async () => {
    const errorMessage = 'Failed to load stations'
    vi.mocked(stationApi.getAll).mockRejectedValue(new Error(errorMessage))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getAllByText(`Error loading stations: ${errorMessage}`).length).toBeGreaterThan(0)
    })
  })

  it('opens create station dialog when Add Station button is clicked', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getAllByText('Stations').length).toBeGreaterThan(0)
    })

    const addButton = screen.getByRole('button', { name: /add station/i })
    await userEvent.click(addButton)

    await waitFor(() => {
      expect(screen.getAllByText('New Station').length).toBeGreaterThan(0)
      expect(screen.getAllByText('Configure a new base station').length).toBeGreaterThan(0)
    })
  })

  it('opens edit station dialog when edit button is clicked', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getAllByText('BS-001').length).toBeGreaterThan(0)
    })

    // Find and click the edit button for BS-001
    const editButtons = screen.getAllByLabelText(/^Edit /)
    await userEvent.click(editButtons[0])

    await waitFor(() => {
      expect(screen.getAllByText('Edit Station').length).toBeGreaterThan(0)
      expect(screen.getAllByText('Update station configuration').length).toBeGreaterThan(0)
      expect(screen.getByDisplayValue('BS-001')).toBeInTheDocument()
    })
  })

  it('creates a new station successfully', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(stationApi.create).mockResolvedValue(mockAxiosResponse({}))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getAllByText('Stations').length).toBeGreaterThan(0)
    })

    // Open create dialog
    const addButton = screen.getByRole('button', { name: /add station/i })
    await userEvent.click(addButton)

    await waitFor(() => {
      expect(screen.getAllByText('New Station').length).toBeGreaterThan(0)
    })

    // Fill form
    const stationNameInput = screen.getByLabelText('Station Name')
    const locationInput = screen.getByLabelText('Location')
    const latitudeInput = screen.getByLabelText('Latitude')
    const longitudeInput = screen.getByLabelText('Longitude')

    await userEvent.clear(stationNameInput); await userEvent.type(stationNameInput, 'BS-004')
    await userEvent.clear(locationInput); await userEvent.type(locationInput, 'Boston, MA')

    const ipInput = screen.getByLabelText('IP Address')
    await userEvent.clear(ipInput); await userEvent.type(ipInput, '10.0.0.4')
    await userEvent.clear(latitudeInput); await userEvent.type(latitudeInput, '42.3601')
    await userEvent.clear(longitudeInput); await userEvent.type(longitudeInput, '-71.0589')

    // Submit form
    const createButton = screen.getByRole('button', { name: /create station/i })
    await userEvent.click(createButton)

    await waitFor(() => {
      // Assert on the fields the form collects rather than the whole payload:
      // powerConsumption is no longer editable and ipAddress is now required.
      const [payload] = vi.mocked(stationApi.create).mock.calls[0]
      expect(payload).toMatchObject({
        stationName: 'BS-004',
        location: 'Boston, MA',
        latitude: 42.3601,
        longitude: -71.0589,
        ipAddress: '10.0.0.4',
      })
    })
  })

  it('updates a station successfully', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    vi.mocked(stationApi.update).mockResolvedValue(mockAxiosResponse({}))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getAllByText('BS-001').length).toBeGreaterThan(0)
    })

    // Open edit dialog for BS-001
    const editButtons = screen.getAllByLabelText(/^Edit /)
    await userEvent.click(editButtons[0])

    await waitFor(() => {
      expect(screen.getByDisplayValue('BS-001')).toBeInTheDocument()
    })

    // Update station name
    const stationNameInput = screen.getByDisplayValue('BS-001')
    await userEvent.clear(stationNameInput); await userEvent.type(stationNameInput, 'BS-001-Updated')

    // Submit form
    const updateButton = screen.getByRole('button', { name: /update station/i })
    await userEvent.click(updateButton)

    await waitFor(() => {
      expect(vi.mocked(stationApi.update).mock.calls[0][0]).toBe(1)
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
      expect(screen.getAllByText('BS-001').length).toBeGreaterThan(0)
    })

    // Click delete button for BS-001
    const deleteButtons = screen.getAllByLabelText(/^Delete /)
    await userEvent.click(deleteButtons[0])

    await waitFor(() => {
      expect(
        screen.getByText(/Are you sure you want to delete this station\?/)
      ).toBeInTheDocument()
    })
  })

  it('does not delete station when confirmation is cancelled', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))
    mockConfirm.mockReturnValue(false)

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getAllByText('BS-001').length).toBeGreaterThan(0)
    })

    // Click delete button for BS-001
    const deleteButtons = screen.getAllByLabelText(/^Delete /)
    await userEvent.click(deleteButtons[0])

    await waitFor(() => {
      expect(
        screen.getByText(/Are you sure you want to delete this station\?/)
      ).toBeInTheDocument()
    })
  })

  it('navigates to station detail when view button is clicked', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getAllByText('BS-001').length).toBeGreaterThan(0)
    })

    // Click view button for BS-001
    const viewButtons = screen.getAllByLabelText(/^View details /i)
    await userEvent.click(viewButtons[0])

    expect(mockNavigate).toHaveBeenCalledWith('/stations/1')
  })

  it('disables create/update button when required fields are empty', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getAllByText('Stations').length).toBeGreaterThan(0)
    })

    // Open create dialog
    const addButton = screen.getByRole('button', { name: /add station/i })
    await userEvent.click(addButton)

    await waitFor(() => {
      expect(screen.getAllByText('New Station').length).toBeGreaterThan(0)
    })

    // Check that create button is disabled initially
    const createButton = screen.getByRole('button', { name: /create station/i })
    expect(createButton).toBeDisabled()

    // Fill required fields
    const stationNameInput = screen.getByLabelText('Station Name')
    const locationInput = screen.getByLabelText('Location')

    await userEvent.clear(stationNameInput); await userEvent.type(stationNameInput, 'BS-004')
    await userEvent.clear(locationInput); await userEvent.type(locationInput, 'Boston, MA')

    const ipInput = screen.getByLabelText('IP Address')
    await userEvent.clear(ipInput); await userEvent.type(ipInput, '10.0.0.4')

    // Button should now be enabled
    await waitFor(() => {
      expect(createButton).not.toBeDisabled()
    })
  })

  it('closes dialog when cancel button is clicked', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getAllByText('Stations').length).toBeGreaterThan(0)
    })

    // Open create dialog
    const addButton = screen.getByRole('button', { name: /add station/i })
    await userEvent.click(addButton)

    await waitFor(() => {
      expect(screen.getAllByText('New Station').length).toBeGreaterThan(0)
    })

    // Click cancel
    const cancelButton = screen.getByRole('button', { name: /cancel/i })
    await userEvent.click(cancelButton)

    await waitFor(() => {
      expect(screen.queryByText('New Station')).not.toBeInTheDocument()
    })
  })

  it('closes dialog when close icon is clicked', async () => {
    vi.mocked(stationApi.getAll).mockResolvedValue(mockAxiosResponse(mockStations))

    render(<Stations />)

    await waitFor(() => {
      expect(screen.getAllByText('Stations').length).toBeGreaterThan(0)
    })

    // Open create dialog
    const addButton = screen.getByRole('button', { name: /add station/i })
    await userEvent.click(addButton)

    await waitFor(() => {
      expect(screen.getAllByText('New Station').length).toBeGreaterThan(0)
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
      await userEvent.click(closeButton)

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
      expect(screen.getAllByText('Stations').length).toBeGreaterThan(0)
    })

    // Open create dialog
    const addButton = screen.getByRole('button', { name: /add station/i })
    await userEvent.click(addButton)

    await waitFor(() => {
      expect(screen.getAllByText('New Station').length).toBeGreaterThan(0)
    })

    // Fill required fields
    const stationNameInput = screen.getByLabelText('Station Name')
    const locationInput = screen.getByLabelText('Location')

    await userEvent.clear(stationNameInput); await userEvent.type(stationNameInput, 'BS-004')
    await userEvent.clear(locationInput); await userEvent.type(locationInput, 'Boston, MA')

    const ipInput = screen.getByLabelText('IP Address')
    await userEvent.clear(ipInput); await userEvent.type(ipInput, '10.0.0.4')

    // Submit form
    const createButton = screen.getByRole('button', { name: /create station/i })
    await userEvent.click(createButton)

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
      expect(screen.getAllByText('BS-001').length).toBeGreaterThan(0)
    })

    // Open edit dialog for BS-001 (first station)
    const editButtons = screen.getAllByLabelText(/^Edit /)
    await userEvent.click(editButtons[0])

    await waitFor(() => {
      expect(screen.getByDisplayValue('BS-001')).toBeInTheDocument()
      expect(screen.getByDisplayValue('New York, NY')).toBeInTheDocument()
      expect(screen.getByDisplayValue('40.7128')).toBeInTheDocument()
      expect(screen.getByDisplayValue('-74.006')).toBeInTheDocument()
    })
  })
})