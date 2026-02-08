import { describe, it, expect, vi, beforeEach } from 'vitest'
import { BaseStation, ManagementProtocol, MetricData, MetricType, StationType, StationStatus } from '../../types'

// Create persistent mock functions at module level
const mockGet = vi.fn()
const mockPost = vi.fn()
const mockPut = vi.fn()
const mockDelete = vi.fn()

// Mock axios module before importing api
vi.mock('axios', () => {
  return {
    default: {
      create: vi.fn(() => ({
        get: mockGet,
        post: mockPost,
        put: mockPut,
        delete: mockDelete,
        interceptors: {
          request: { use: vi.fn((fn) => fn), eject: vi.fn(), clear: vi.fn() },
          response: { use: vi.fn((fn) => fn), eject: vi.fn(), clear: vi.fn() },
        },
      })),
    },
  }
})

// Import after mock
const { stationApi, metricsApi, notificationsApi } = await import('../api')

describe('API Service', () => {
  beforeEach(() => {
    // Clear all mocks before each test
    mockGet.mockClear()
    mockPost.mockClear()
    mockPut.mockClear()
    mockDelete.mockClear()
  })

  describe('stationApi', () => {
    it('getAll should call GET /stations', async () => {
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
        },
      ]
      mockGet.mockResolvedValue({ data: mockStations })

      const result = await stationApi.getAll()

      expect(mockGet).toHaveBeenCalledWith('/stations')
      expect(result.data).toEqual(mockStations)
    })

    it('getById should call GET /stations/:id', async () => {
      const mockStation: BaseStation = {
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
      }
      mockGet.mockResolvedValue({ data: mockStation })

      const result = await stationApi.getById(1)

      expect(mockGet).toHaveBeenCalledWith('/stations/1')
      expect(result.data).toEqual(mockStation)
    })

    it('create should call POST /stations with data', async () => {
      const newStation: BaseStation = {
        stationName: 'BS-002',
        location: 'Location 2',
        latitude: 40.758,
        longitude: -73.9855,
        stationType: StationType.SMALL_CELL,
        ipAddress: '10.100.1.102',
        managementProtocol: ManagementProtocol.DIRECT,
        status: StationStatus.ACTIVE,
        powerConsumption: 800,
      }
      mockPost.mockResolvedValue({ data: { ...newStation, id: 2 } })

      const result = await stationApi.create(newStation)

      expect(mockPost).toHaveBeenCalledWith('/stations', newStation)
      expect(result.data.id).toBe(2)
    })

    it('update should call PUT /stations/:id with data', async () => {
      const updateData = { status: StationStatus.MAINTENANCE }
      mockPut.mockResolvedValue({ data: { id: 1, ...updateData } })

      const result = await stationApi.update(1, updateData)

      expect(mockPut).toHaveBeenCalledWith('/stations/1', updateData)
      expect(result.data.id).toBe(1)
    })

    it('delete should call DELETE /stations/:id', async () => {
      mockDelete.mockResolvedValue({ data: {} })

      await stationApi.delete(1)

      expect(mockDelete).toHaveBeenCalledWith('/stations/1')
    })

    it('searchByLocation should call GET with query parameters', async () => {
      mockGet.mockResolvedValue({ data: [] })

      await stationApi.searchByLocation(40.7128, -74.006, 10)

      expect(mockGet).toHaveBeenCalled()
      const callArgs = mockGet.mock.calls[0][0] as string
      expect(callArgs).toContain('/stations/search')
      expect(callArgs).toContain('latitude=40.7128')
      expect(callArgs).toContain('radius=10')
    })
  })

  describe('metricsApi', () => {
    it('getAll should call GET /metrics with optional params', async () => {
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
      ]
      mockGet.mockResolvedValue({ data: mockMetrics })

      const params = { startTime: '2024-01-01T00:00:00Z' }
      const result = await metricsApi.getAll(params)

      expect(mockGet).toHaveBeenCalledWith('/metrics', { params })
      expect(result.data).toEqual(mockMetrics)
    })

    it('getByStation should call GET /metrics/station/:id', async () => {
      mockGet.mockResolvedValue({ data: [] })

      await metricsApi.getByStation(1)

      expect(mockGet).toHaveBeenCalledWith('/metrics/station/1')
    })

    it('create should call POST /metrics with data', async () => {
      const newMetric: MetricData = {
        stationId: 1,
        stationName: 'BS-001',
        metricType: MetricType.CPU_USAGE,
        value: 75.5,
        unit: '%',
      }
      mockPost.mockResolvedValue({ data: { ...newMetric, id: '1' } })

      const result = await metricsApi.create(newMetric)

      expect(mockPost).toHaveBeenCalledWith('/metrics', newMetric)
      expect(result.data.id).toBe('1')
    })
  })

  describe('notificationsApi', () => {
    it('getAll should call GET /notifications', async () => {
      mockGet.mockResolvedValue({ data: [] })

      await notificationsApi.getAll()

      expect(mockGet).toHaveBeenCalledWith('/notifications')
    })

    it('getByStation should call GET /notifications/station/:id', async () => {
      mockGet.mockResolvedValue({ data: [] })

      await notificationsApi.getByStation(1)

      expect(mockGet).toHaveBeenCalledWith('/notifications/station/1')
    })

    it('create should call POST /notifications with params', async () => {
      const params = { stationId: 1, message: 'Test alert', type: 'ALERT' }
      mockPost.mockResolvedValue({ data: { id: 1, ...params } })

      const result = await notificationsApi.create(params)

      expect(mockPost).toHaveBeenCalledWith('/notifications', null, { params })
      expect(result.data.id).toBe(1)
    })

    it('deleteNotification should call DELETE /notifications/:id', async () => {
      mockDelete.mockResolvedValue({ data: {} })

      await notificationsApi.deleteNotification(1)

      expect(mockDelete).toHaveBeenCalledWith('/notifications/1')
    })
  })
})
