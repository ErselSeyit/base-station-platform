/**
 * Token Manager - Centralized token refresh state management.
 *
 * Handles:
 * - Refresh-in-progress state to prevent concurrent refreshes
 * - Subscriber pattern for queuing requests during refresh
 * - User info storage (for UI display)
 *
 * Note: Refresh tokens are now stored in HttpOnly cookies by the backend.
 * This module no longer handles refresh token storage directly.
 *
 * Used by: api/client.ts (interceptor), authService.ts
 */

// Refresh state
let isRefreshing = false
let refreshSubscribers: Array<(success: boolean) => void> = []

/**
 * Subscribe to token refresh completion.
 * Used to queue requests while refresh is in progress.
 */
export function subscribeToRefresh(callback: (success: boolean) => void): void {
  refreshSubscribers.push(callback)
}

/**
 * Notify all subscribers of refresh result.
 */
export function notifyRefreshSubscribers(success: boolean): void {
  refreshSubscribers.forEach((callback) => callback(success))
  refreshSubscribers = []
}

/**
 * Check if a token refresh is currently in progress.
 */
export function isRefreshInProgress(): boolean {
  return isRefreshing
}

/**
 * Set the refresh-in-progress state.
 */
export function setRefreshInProgress(value: boolean): void {
  isRefreshing = value
}

/**
 * Get the stored auth user from sessionStorage.
 */
export function getStoredAuthUser(): { username: string; role: string } | null {
  if (typeof globalThis.window === 'undefined') return null
  const stored = sessionStorage.getItem('authUser')
  if (!stored) return null
  try {
    return JSON.parse(stored)
  } catch {
    return null
  }
}

/**
 * Store auth user info in sessionStorage.
 */
export function setStoredAuthUser(user: { username: string; role: string }): void {
  if (typeof globalThis.window === 'undefined') return
  sessionStorage.setItem('authUser', JSON.stringify(user))
}

/**
 * Clear stored user info.
 * Note: HttpOnly cookies are cleared by the backend on logout.
 */
export function clearStoredTokens(): void {
  if (typeof globalThis.window === 'undefined') return
  sessionStorage.removeItem('authUser')
}

/**
 * Check if we believe a refresh token exists.
 * Since refresh tokens are now in HttpOnly cookies (not accessible to JS),
 * we infer this from whether we have stored user info.
 */
export function hasRefreshToken(): boolean {
  return getStoredAuthUser() !== null
}
