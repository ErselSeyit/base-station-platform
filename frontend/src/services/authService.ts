import api from './api'
import {
  getStoredAuthUser,
  setStoredAuthUser,
  clearStoredTokens,
  hasRefreshToken,
  isRefreshInProgress,
  setRefreshInProgress,
  subscribeToRefresh,
  notifyRefreshSubscribers,
} from './tokenManager'

/**
 * Authentication Service
 *
 * Uses HttpOnly cookies for both access and refresh token storage (XSS-safe).
 * User info is kept in memory and sessionStorage for UI display.
 * Cookies are sent automatically with withCredentials: true.
 */

export interface LoginCredentials {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  expiresIn?: number
  username: string
  role: string
}

export interface TokenResponse {
  accessToken: string
  expiresIn: number
  username: string
  role: string
}

// In-memory auth state for current user (restored from sessionStorage on load)
let currentUser: { username: string; role: string } | null = null

// Restore user info from sessionStorage on module load (survives page refresh)
if (globalThis.window !== undefined) {
  currentUser = getStoredAuthUser()
}

export const authService = {
  login: async (credentials: LoginCredentials): Promise<LoginResponse> => {
    const response = await api.post('/auth/login', credentials)
    const data = response.data as LoginResponse

    // Store user info in memory and sessionStorage
    // Tokens are stored in HttpOnly cookies by the backend
    currentUser = { username: data.username, role: data.role }
    setStoredAuthUser(currentUser)

    return data
  },

  logout: async () => {
    // Clear server-side HttpOnly cookies (both access and refresh tokens)
    try {
      await api.post('/auth/logout')
    } catch {
      // Ignore errors - still clear local state
    }

    // Clear in-memory and session state
    currentUser = null
    clearStoredTokens()
  },

  /**
   * Check if user is authenticated.
   * For quick UI checks, uses in-memory state.
   * For definitive checks (e.g., on app load), use validateSession().
   */
  isAuthenticated: (): boolean => {
    return currentUser !== null
  },

  /**
   * Validate session with server (checks if HttpOnly cookie is still valid).
   * Use this on app initialization to restore auth state.
   */
  validateSession: async (): Promise<boolean> => {
    try {
      await api.get('/auth/validate')
      return true
    } catch {
      // Try refresh if validation fails and we believe we have a session
      if (hasRefreshToken()) {
        const refreshed = await authService.refreshAccessToken()
        if (refreshed) {
          return true
        }
      }

      // Session invalid - clear local state
      authService.clearLocalState()
      return false
    }
  },

  /**
   * Attempt to refresh the access token using the HttpOnly refresh cookie.
   * Returns true if refresh was successful, false otherwise.
   */
  refreshAccessToken: async (): Promise<boolean> => {
    // Check if we believe we have a session (user info exists)
    if (!hasRefreshToken()) {
      return false
    }

    // If already refreshing, wait for that to complete
    if (isRefreshInProgress()) {
      return new Promise((resolve) => {
        subscribeToRefresh(resolve)
      })
    }

    setRefreshInProgress(true)

    try {
      // Backend reads refresh token from HttpOnly cookie
      const response = await api.post('/auth/refresh')
      const data = response.data as TokenResponse

      // Update user info (tokens are in HttpOnly cookies)
      currentUser = { username: data.username, role: data.role }
      setStoredAuthUser(currentUser)

      notifyRefreshSubscribers(true)
      return true
    } catch {
      // Refresh failed - clear auth state
      authService.clearLocalState()
      notifyRefreshSubscribers(false)
      return false
    } finally {
      setRefreshInProgress(false)
    }
  },

  /**
   * Clear local authentication state without server calls.
   */
  clearLocalState: () => {
    currentUser = null
    clearStoredTokens()
  },

  /**
   * Check if a token refresh is currently in progress.
   */
  isRefreshInProgress: (): boolean => {
    return isRefreshInProgress()
  },

  /**
   * Check if we believe we have a refresh token (based on stored user info).
   */
  hasRefreshToken: (): boolean => {
    return hasRefreshToken()
  },

  getUsername: (): string | null => {
    return currentUser?.username ?? null
  },

  getRole: (): string | null => {
    return currentUser?.role ?? null
  },

  /**
   * Get current user info (username and role).
   * Returns null if not authenticated.
   * Returns a shallow copy to prevent external mutation.
   */
  getCurrentUser: (): { username: string; role: string } | null => {
    return currentUser ? { ...currentUser } : null
  },
}
