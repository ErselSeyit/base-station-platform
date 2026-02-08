import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

// Create persistent mock functions at module level
const mockGet = vi.fn()
const mockPost = vi.fn()

// Mock axios module before importing authService
vi.mock('axios', () => {
  return {
    default: {
      create: vi.fn(() => ({
        get: mockGet,
        post: mockPost,
        put: vi.fn(),
        delete: vi.fn(),
        interceptors: {
          request: { use: vi.fn((fn) => fn), eject: vi.fn(), clear: vi.fn() },
          response: { use: vi.fn((fn) => fn), eject: vi.fn(), clear: vi.fn() },
        },
      })),
    },
  }
})

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

// Import after mocks are set up
const { authService } = await import('../authService')

describe('AuthService', () => {
  beforeEach(() => {
    mockGet.mockClear()
    mockPost.mockClear()
    sessionStorageMock.clear()
    authService.clearLocalState()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  describe('login', () => {
    it('should authenticate user and store user info', async () => {
      const loginResponse = {
        token: 'test-token',
        expiresIn: 3600,
        username: 'testuser',
        role: 'OPERATOR',
      }
      mockPost.mockResolvedValue({ data: loginResponse })

      const result = await authService.login({ username: 'testuser', password: 'password123' })

      expect(mockPost).toHaveBeenCalledWith('/auth/login', { username: 'testuser', password: 'password123' })
      expect(result.username).toBe('testuser')
      expect(result.role).toBe('OPERATOR')
      expect(authService.isAuthenticated()).toBe(true)
      expect(authService.getUsername()).toBe('testuser')
      expect(authService.getRole()).toBe('OPERATOR')
    })

    it('should store user info in sessionStorage', async () => {
      const loginResponse = {
        token: 'test-token',
        username: 'admin',
        role: 'ADMIN',
      }
      mockPost.mockResolvedValue({ data: loginResponse })

      await authService.login({ username: 'admin', password: 'admin123' })

      expect(sessionStorageMock.setItem).toHaveBeenCalledWith(
        'authUser',
        JSON.stringify({ username: 'admin', role: 'ADMIN' })
      )
    })

    it('should throw error on invalid credentials', async () => {
      mockPost.mockRejectedValue(new Error('Invalid credentials'))

      await expect(
        authService.login({ username: 'wrong', password: 'wrong' })
      ).rejects.toThrow('Invalid credentials')

      expect(authService.isAuthenticated()).toBe(false)
    })
  })

  describe('logout', () => {
    it('should clear user state and call logout endpoint', async () => {
      // First login
      mockPost.mockResolvedValueOnce({
        data: { token: 'test', username: 'user', role: 'OPERATOR' },
      })
      await authService.login({ username: 'user', password: 'pass' })
      expect(authService.isAuthenticated()).toBe(true)

      // Then logout
      mockPost.mockResolvedValueOnce({})
      await authService.logout()

      expect(mockPost).toHaveBeenLastCalledWith('/auth/logout')
      expect(authService.isAuthenticated()).toBe(false)
      expect(authService.getUsername()).toBeNull()
      expect(authService.getRole()).toBeNull()
    })

    it('should clear local state even if server call fails', async () => {
      // First login
      mockPost.mockResolvedValueOnce({
        data: { token: 'test', username: 'user', role: 'OPERATOR' },
      })
      await authService.login({ username: 'user', password: 'pass' })

      // Logout fails on server
      mockPost.mockRejectedValueOnce(new Error('Network error'))
      await authService.logout()

      // Local state should still be cleared
      expect(authService.isAuthenticated()).toBe(false)
      expect(sessionStorageMock.removeItem).toHaveBeenCalledWith('authUser')
    })
  })

  describe('isAuthenticated', () => {
    it('should return false when not logged in', () => {
      expect(authService.isAuthenticated()).toBe(false)
    })

    it('should return true after login', async () => {
      mockPost.mockResolvedValue({
        data: { token: 'test', username: 'user', role: 'ADMIN' },
      })
      await authService.login({ username: 'user', password: 'pass' })

      expect(authService.isAuthenticated()).toBe(true)
    })
  })

  describe('validateSession', () => {
    it('should return true if session is valid', async () => {
      // Setup: user logged in
      mockPost.mockResolvedValueOnce({
        data: { token: 'test', username: 'user', role: 'OPERATOR' },
      })
      await authService.login({ username: 'user', password: 'pass' })

      // Validate returns success
      mockGet.mockResolvedValue({})

      const result = await authService.validateSession()

      expect(mockGet).toHaveBeenCalledWith('/auth/validate')
      expect(result).toBe(true)
    })

    it('should clear state if session is invalid and refresh fails', async () => {
      // Setup: user appears logged in (has stored user)
      sessionStorageMock.setItem('authUser', JSON.stringify({ username: 'user', role: 'ADMIN' }))

      // Validate fails
      mockGet.mockRejectedValue(new Error('Unauthorized'))
      // Refresh also fails
      mockPost.mockRejectedValue(new Error('Refresh token expired'))

      const result = await authService.validateSession()

      expect(result).toBe(false)
    })
  })

  describe('refreshAccessToken', () => {
    it('should return false if no refresh token exists', async () => {
      const result = await authService.refreshAccessToken()

      expect(result).toBe(false)
      expect(mockPost).not.toHaveBeenCalled()
    })

    it('should refresh token and update user info', async () => {
      // Setup: user appears logged in
      sessionStorageMock.setItem('authUser', JSON.stringify({ username: 'user', role: 'OPERATOR' }))

      const refreshResponse = {
        accessToken: 'new-token',
        expiresIn: 3600,
        username: 'user',
        role: 'ADMIN', // Role could change on refresh
      }
      mockPost.mockResolvedValue({ data: refreshResponse })

      const result = await authService.refreshAccessToken()

      expect(mockPost).toHaveBeenCalledWith('/auth/refresh')
      expect(result).toBe(true)
      expect(authService.getRole()).toBe('ADMIN')
    })

    it('should clear state on refresh failure', async () => {
      // Setup: user appears logged in
      sessionStorageMock.setItem('authUser', JSON.stringify({ username: 'user', role: 'OPERATOR' }))

      mockPost.mockRejectedValue(new Error('Refresh token expired'))

      const result = await authService.refreshAccessToken()

      expect(result).toBe(false)
      expect(authService.isAuthenticated()).toBe(false)
    })
  })

  describe('getCurrentUser', () => {
    it('should return null when not authenticated', () => {
      expect(authService.getCurrentUser()).toBeNull()
    })

    it('should return user info when authenticated', async () => {
      mockPost.mockResolvedValue({
        data: { token: 'test', username: 'admin', role: 'ADMIN' },
      })
      await authService.login({ username: 'admin', password: 'pass' })

      const user = authService.getCurrentUser()

      expect(user).toEqual({ username: 'admin', role: 'ADMIN' })
    })

    it('should return a copy to prevent mutation', async () => {
      mockPost.mockResolvedValue({
        data: { token: 'test', username: 'user', role: 'OPERATOR' },
      })
      await authService.login({ username: 'user', password: 'pass' })

      const user1 = authService.getCurrentUser()
      const user2 = authService.getCurrentUser()

      expect(user1).not.toBe(user2) // Different object references
      expect(user1).toEqual(user2) // But same values
    })
  })

  describe('clearLocalState', () => {
    it('should clear in-memory and storage state', async () => {
      // Setup: login first
      mockPost.mockResolvedValue({
        data: { token: 'test', username: 'user', role: 'ADMIN' },
      })
      await authService.login({ username: 'user', password: 'pass' })
      expect(authService.isAuthenticated()).toBe(true)

      // Clear state
      authService.clearLocalState()

      expect(authService.isAuthenticated()).toBe(false)
      expect(authService.getCurrentUser()).toBeNull()
      expect(sessionStorageMock.removeItem).toHaveBeenCalledWith('authUser')
    })
  })
})
