import api from './api'

export interface LoginCredentials {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  username: string
  role: string
}

export const authService = {
  login: async (credentials: LoginCredentials): Promise<LoginResponse> => {
    const response = await api.post('/auth/login', credentials)
    const data = response.data as LoginResponse

    // Store token in localStorage
    if (data.token) {
      localStorage.setItem('token', data.token)
      localStorage.setItem('username', data.username)
      localStorage.setItem('role', data.role)
    }

    return data
  },

  logout: async () => {
    // Clear server-side HttpOnly cookie
    try {
      await api.post('/auth/logout')
    } catch {
      // Ignore errors - still clear local state
    }
    // Clear local state (kept for backward compatibility)
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('role')
  },

  isAuthenticated: (): boolean => {
    return !!localStorage.getItem('token')
  },

  getToken: (): string | null => {
    return localStorage.getItem('token')
  },

  getUsername: (): string | null => {
    return localStorage.getItem('username')
  },

  getRole: (): string | null => {
    return localStorage.getItem('role')
  }
}
