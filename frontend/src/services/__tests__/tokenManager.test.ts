import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  subscribeToRefresh,
  notifyRefreshSubscribers,
  isRefreshInProgress,
  setRefreshInProgress,
  getStoredAuthUser,
  setStoredAuthUser,
  clearStoredTokens,
  hasRefreshToken,
} from '../tokenManager'

// Mock sessionStorage
const sessionStorageMock = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, value: string) => { store[key] = value }),
    removeItem: vi.fn((key: string) => { delete store[key] }),
    clear: vi.fn(() => { store = {} }),
  }
})()

Object.defineProperty(globalThis, 'sessionStorage', { value: sessionStorageMock })

describe('TokenManager', () => {
  beforeEach(() => {
    // Reset state
    sessionStorageMock.clear()
    setRefreshInProgress(false)
    // Clear any pending subscribers by notifying
    notifyRefreshSubscribers(false)
  })

  describe('refresh state management', () => {
    it('should track refresh in progress state', () => {
      expect(isRefreshInProgress()).toBe(false)

      setRefreshInProgress(true)
      expect(isRefreshInProgress()).toBe(true)

      setRefreshInProgress(false)
      expect(isRefreshInProgress()).toBe(false)
    })

    it('should notify all subscribers on refresh completion', () => {
      const callback1 = vi.fn()
      const callback2 = vi.fn()
      const callback3 = vi.fn()

      subscribeToRefresh(callback1)
      subscribeToRefresh(callback2)
      subscribeToRefresh(callback3)

      notifyRefreshSubscribers(true)

      expect(callback1).toHaveBeenCalledWith(true)
      expect(callback2).toHaveBeenCalledWith(true)
      expect(callback3).toHaveBeenCalledWith(true)
    })

    it('should notify subscribers with failure status', () => {
      const callback = vi.fn()
      subscribeToRefresh(callback)

      notifyRefreshSubscribers(false)

      expect(callback).toHaveBeenCalledWith(false)
    })

    it('should clear subscribers after notification', () => {
      const callback = vi.fn()
      subscribeToRefresh(callback)

      notifyRefreshSubscribers(true)
      notifyRefreshSubscribers(true) // Second notification

      // Callback should only be called once (cleared after first notification)
      expect(callback).toHaveBeenCalledTimes(1)
    })
  })

  describe('auth user storage', () => {
    it('should store and retrieve auth user', () => {
      const user = { username: 'testuser', role: 'ADMIN' }

      setStoredAuthUser(user)

      expect(sessionStorageMock.setItem).toHaveBeenCalledWith(
        'authUser',
        JSON.stringify(user)
      )

      const retrieved = getStoredAuthUser()
      expect(retrieved).toEqual(user)
    })

    it('should return null when no user is stored', () => {
      const result = getStoredAuthUser()
      expect(result).toBeNull()
    })

    it('should return null for invalid JSON in storage', () => {
      sessionStorageMock.setItem('authUser', 'invalid-json')

      const result = getStoredAuthUser()
      expect(result).toBeNull()
    })

    it('should clear stored tokens', () => {
      setStoredAuthUser({ username: 'user', role: 'OPERATOR' })

      clearStoredTokens()

      expect(sessionStorageMock.removeItem).toHaveBeenCalledWith('authUser')
      expect(getStoredAuthUser()).toBeNull()
    })
  })

  describe('hasRefreshToken', () => {
    it('should return false when no user is stored', () => {
      expect(hasRefreshToken()).toBe(false)
    })

    it('should return true when user is stored', () => {
      setStoredAuthUser({ username: 'user', role: 'ADMIN' })

      expect(hasRefreshToken()).toBe(true)
    })

    it('should return false after clearing tokens', () => {
      setStoredAuthUser({ username: 'user', role: 'ADMIN' })
      expect(hasRefreshToken()).toBe(true)

      clearStoredTokens()
      expect(hasRefreshToken()).toBe(false)
    })
  })
})
