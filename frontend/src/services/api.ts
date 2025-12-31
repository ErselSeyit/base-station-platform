import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { BaseStation, MetricData } from '../types'
import { logger } from './logger'

/**
 * API Configuration
 * 
 * Base URL resolution order:
 * 1. VITE_API_URL environment variable (for production builds)
 * 2. 'api-gateway:8080' for Docker/K8s networking
 * 3. Falls back to relative '/api/v1' for same-origin deployments
 * 
 * In development, Vite proxy handles routing to api-gateway.
 */
const API_BASE_URL = import.meta.env.VITE_API_URL || '/api/v1'

/**
 * Normalized error structure for consistent error handling across the app.
 */
export interface ApiError {
  message: string
  status: number
  code?: string
  data?: unknown
  timestamp?: string
}

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 30000, // 30 second timeout
})

// ============================================
// REQUEST INTERCEPTOR
// ============================================
// Automatically injects Authorization header if token exists.
// Also adds correlation ID for distributed tracing.
// ============================================
api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Inject JWT token if available
    const token = globalThis.window === undefined ? null : localStorage.getItem('token')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
      logger.api.debug(`Attaching token to request: ${config.url}`)
    } else {
      logger.api.warn(`No token available for request: ${config.url}`)
    }

    // Add correlation ID for distributed tracing
    const correlationId = crypto.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).substring(2, 11)}`
    if (config.headers) {
      config.headers['X-Correlation-ID'] = correlationId
    }

    return config
  },
  (error: AxiosError<{ message?: string; error?: string }>) => {
    // Request setup error (rare)
    logger.api.error('Request setup error', { message: error.message })
    return Promise.reject(new Error(normalizeError(error).message))
  }
)

/**
 * Handles unauthorized errors by clearing token and dispatching auth event.
 */
function handleUnauthorizedError(): void {
  if (globalThis.window !== undefined) {
    localStorage.removeItem('token')
    globalThis.dispatchEvent(new CustomEvent('auth:unauthorized'))
  }
  logger.api.warn('Unauthorized - token cleared')
}

/**
 * Logs specific error scenarios based on status code.
 */
function logErrorByStatus(normalized: ApiError, error: AxiosError): void {
  if (normalized.status === 401) {
    // Only clear token for authenticated endpoints, not login/register failures
    const url = error.config?.url || ''
    const isAuthEndpoint = url.includes('/auth/login') || url.includes('/auth/register')
    if (isAuthEndpoint) {
      logger.api.warn('Login/Register failed - invalid credentials')
    } else {
      handleUnauthorizedError()
    }
  } else if (normalized.status === 403) {
    logger.api.warn('Forbidden - insufficient permissions')
  } else if (normalized.status === 429) {
    logger.api.warn('Rate limited - too many requests')
  } else if (normalized.status >= 500) {
    logger.api.error('Server error', { message: normalized.message })
  } else if (normalized.status === 0) {
    logger.api.error('Network error - check connectivity')
  }

  // Log all errors for debugging (consider sending to monitoring service)
  logger.api.error('Request failed', {
    url: error.config?.url,
    method: error.config?.method,
    status: normalized.status,
    message: normalized.message,
  })
}

// ============================================
// RESPONSE INTERCEPTOR
// ============================================
// Normalizes all error responses into a consistent ApiError structure.
// Handles auth errors, network issues, and server errors uniformly.
// ============================================
api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<{ message?: string; error?: string; status?: number }>) => {
    const normalized = normalizeError(error)
    logErrorByStatus(normalized, error)

    // Reject with Error instance for proper error handling
    const apiError = new Error(normalized.message)
    Object.assign(apiError, normalized)
    return Promise.reject(apiError)
  }
)

/**
 * Normalizes various error types into a consistent ApiError structure.
 */
function normalizeError(error: AxiosError<{ message?: string; error?: string }>): ApiError {
  if (error.response) {
    // Server responded with error status
    return {
      message: error.response.data?.message || error.response.data?.error || error.message || 'Request failed',
      status: error.response.status,
      code: error.code,
      data: error.response.data,
      timestamp: new Date().toISOString(),
    }
  } else if (error.request) {
    // Request made but no response (network error)
    return {
      message: 'Network error - unable to reach server',
      status: 0,
      code: 'NETWORK_ERROR',
      timestamp: new Date().toISOString(),
    }
  } else {
    // Request setup error
    return {
      message: error.message || 'Unknown error',
      status: 0,
      code: 'REQUEST_SETUP_ERROR',
      timestamp: new Date().toISOString(),
    }
  }
}

// Base Station API
export const stationApi = {
  getAll: () => api.get('/stations'),
  getById: (id: number) => api.get(`/stations/${id}`),
  create: (data: BaseStation) => api.post('/stations', data),
  update: (id: number, data: Partial<BaseStation>) => api.put(`/stations/${id}`, data),
  delete: (id: number) => api.delete(`/stations/${id}`),
  searchByLocation: (latitude: number, longitude: number, radius: number = 10) =>
    api.get(`/stations/search?latitude=${latitude}&longitude=${longitude}&radius=${radius}`),
  getByStatus: (status: string) => api.get(`/stations?status=${status}`),
}

// Metrics API
export const metricsApi = {
  getAll: (params?: { startTime?: string; endTime?: string }) =>
    api.get('/metrics', { params }),
  getByStation: (stationId: number) => api.get(`/metrics/station/${stationId}`),
  create: (data: MetricData) => api.post('/metrics', data),
}

// Notifications API
export const notificationsApi = {
  getAll: () => api.get('/notifications'),
  getByStation: (stationId: number) => api.get(`/notifications/station/${stationId}`),
  create: (params: { stationId: number; message: string; type: string }) =>
    api.post('/notifications', null, { params }),
  markAsRead: (id: number) => api.put(`/notifications/${id}/read`),
}

export default api

