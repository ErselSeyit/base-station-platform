/**
 * API Client - Base axios instance with interceptors and error handling.
 *
 * This module provides the core HTTP client configuration used by all API modules.
 * Includes automatic token refresh on 401 errors using HttpOnly refresh cookies.
 */
import axios, { AxiosError, AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { logger } from '../logger'
import {
  setStoredAuthUser,
  clearStoredTokens,
  hasRefreshToken,
  isRefreshInProgress,
  setRefreshInProgress,
  subscribeToRefresh,
  notifyRefreshSubscribers,
} from '../tokenManager'

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
 * Endpoints that should not trigger token refresh on 401.
 */
const AUTH_ENDPOINTS = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout']

/**
 * Check if URL is an auth endpoint.
 */
function isAuthEndpoint(url: string | undefined): boolean {
  if (!url) return false
  return AUTH_ENDPOINTS.some((endpoint) => url.includes(endpoint))
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
    withCredentials: true, // Send cookies with requests (HttpOnly tokens)
  })

  // Add request interceptor
  client.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
      // Authentication is handled via HttpOnly cookies
      // No need to inject Authorization header - cookies are sent automatically
      // with withCredentials: true

      // Add correlation ID for distributed tracing
      const correlationId = crypto.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).substring(2, 11)}`
      if (config.headers) {
        config.headers['X-Correlation-ID'] = correlationId
      }

      logger.api.debug(`Request: ${config.method?.toUpperCase()} ${config.url}`)
      return config
    },
    (error: AxiosError<{ message?: string; error?: string }>) => {
      // Request setup error (rare)
      logger.api.error('Request setup error', { message: error.message })
      throw new Error(normalizeError(error).message)
    }
  )

  // Add response interceptor with token refresh
  client.interceptors.response.use(
    (response: AxiosResponse) => response,
    async (error: AxiosError<{ message?: string; error?: string; status?: number }>) => {
      const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }
      const normalized = normalizeError(error)

      // Handle 401 with automatic token refresh
      if (normalized.status === 401 && !isAuthEndpoint(originalRequest?.url) && !originalRequest?._retry) {
        // Try to refresh the token
        const refreshed = await attemptTokenRefresh(client)

        if (refreshed && originalRequest) {
          // Mark request as retried to prevent infinite loops
          originalRequest._retry = true
          logger.api.info('Retrying request after token refresh')
          return client(originalRequest)
        }
      }

      logErrorByStatus(normalized, error)

      // Throw Error instance for proper error handling
      const apiError = new Error(normalized.message)
      Object.assign(apiError, normalized)
      throw apiError
    }
  )

  return client
}

/**
 * Attempt to refresh the access token using HttpOnly refresh cookie.
 * Returns true if refresh was successful, false otherwise.
 */
async function attemptTokenRefresh(client: AxiosInstance): Promise<boolean> {
  // Check if we believe we have a session
  if (!hasRefreshToken()) {
    logger.api.debug('No session available for refresh')
    handleUnauthorizedError()
    return false
  }

  // If already refreshing, wait for that to complete
  if (isRefreshInProgress()) {
    return new Promise((resolve) => {
      subscribeToRefresh(resolve)
    })
  }

  setRefreshInProgress(true)
  logger.api.info('Attempting token refresh')

  try {
    // Backend reads refresh token from HttpOnly cookie
    const response = await client.post('/auth/refresh')
    const data = response.data

    // Update stored user info (tokens are in HttpOnly cookies)
    if (data.username && data.role) {
      setStoredAuthUser({ username: data.username, role: data.role })
    }

    logger.api.info('Token refresh successful')
    notifyRefreshSubscribers(true)
    return true
  } catch (refreshError) {
    logger.api.warn('Token refresh failed', { error: refreshError instanceof Error ? refreshError.message : String(refreshError) })
    clearStoredTokens()
    handleUnauthorizedError()
    notifyRefreshSubscribers(false)
    return false
  } finally {
    setRefreshInProgress(false)
  }
}

/**
 * Handles unauthorized errors by dispatching auth event.
 * The HttpOnly cookies will be cleared by the server on logout.
 */
function handleUnauthorizedError(): void {
  if (globalThis.window) {
    globalThis.dispatchEvent(new CustomEvent('auth:unauthorized'))
  }
  logger.api.warn('Unauthorized - session expired')
}

/**
 * Logs specific error scenarios based on status code.
 */
function logErrorByStatus(normalized: ApiError, error: AxiosError): void {
  if (normalized.status === 401) {
    // Only warn for authenticated endpoints, not login/register failures
    const url = error.config?.url || ''
    if (isAuthEndpoint(url)) {
      logger.api.warn('Auth endpoint failed - invalid credentials or token')
    }
    // handleUnauthorizedError is called in the interceptor after refresh attempt
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
