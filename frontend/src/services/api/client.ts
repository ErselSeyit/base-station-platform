/**
 * API Client - Base axios instance with interceptors and error handling.
 *
 * This module provides the core HTTP client configuration used by all API modules.
 */
import axios, { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios'
import { logger } from '../logger'

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

/**
 * Creates and configures the base axios instance.
 */
function createApiClient(): AxiosInstance {
  const client = axios.create({
    baseURL: API_BASE_URL,
    headers: {
      'Content-Type': 'application/json',
    },
    timeout: 30000, // 30 second timeout
    withCredentials: true, // Send cookies with requests (HttpOnly auth_token)
  })

  // Add request interceptor
  client.interceptors.request.use(
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

  // Add response interceptor
  client.interceptors.response.use(
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

  return client
}

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

  // Log all errors for debugging
  logger.api.error('Request failed', {
    url: error.config?.url,
    method: error.config?.method,
    status: normalized.status,
    message: normalized.message,
  })
}

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

// Create and export the singleton API client
export const api = createApiClient()
export default api
