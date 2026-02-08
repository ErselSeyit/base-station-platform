import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box,
  TextField,
  Button,
  Typography,
  Alert,
  CircularProgress,
  InputAdornment,
  IconButton,
} from '@mui/material'
import { Visibility, VisibilityOff } from '@mui/icons-material'
import { motion } from 'framer-motion'
import { showToast } from '../utils/toast'
import { authService } from '../services/authService'
import { logger } from '../services/logger'

// Shared TextField styling
const TEXT_FIELD_SX = {
  '& .MuiOutlinedInput-root': {
    background: 'var(--surface-base)',
    borderRadius: '8px',
    fontSize: '0.875rem',
    transition: 'all 0.15s cubic-bezier(0.16, 1, 0.3, 1)',
    '& fieldset': {
      borderColor: 'var(--surface-border)',
    },
    '&:hover fieldset': {
      borderColor: 'var(--mono-400)',
    },
    '&.Mui-focused fieldset': {
      borderColor: 'var(--mono-950)',
      borderWidth: '1px',
    },
  },
  '& .MuiOutlinedInput-input': {
    padding: '12px 14px',
    color: 'var(--mono-950)',
  },
} as const

// Shared label styling
const LABEL_SX = {
  display: 'block',
  fontSize: '0.75rem',
  fontWeight: 500,
  color: 'var(--mono-700)',
  textTransform: 'uppercase',
  letterSpacing: '0.05em',
  marginBottom: '8px',
} as const

// Navigation delay to allow toast to display
const NAVIGATION_DELAY_MS = 400

export default function Login() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)

    try {
      await authService.login({ username, password })
      showToast.success('Welcome back')
      setTimeout(() => {
        navigate('/')
      }, NAVIGATION_DELAY_MS)
    } catch (err) {
      logger.error('Login error', { error: err })
      setError('Invalid username or password')
      showToast.error('Login failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--surface-base)',
        padding: '24px',
      }}
    >
      <Box
        component={motion.div}
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
        sx={{
          width: '100%',
          maxWidth: '400px',
        }}
      >
        {/* Logo and Title - Minimal */}
        <Box sx={{ marginBottom: '48px', textAlign: 'center' }}>
          <Typography
            variant="h1"
            sx={{
              fontSize: '2rem',
              fontWeight: 700,
              letterSpacing: '-0.025em',
              color: 'var(--mono-950)',
              marginBottom: '8px',
            }}
          >
            Base Station Platform
          </Typography>
          <Typography
            sx={{
              fontSize: '0.875rem',
              color: 'var(--mono-500)',
              letterSpacing: '0.01em',
            }}
          >
            Operations & Maintenance Dashboard
          </Typography>
        </Box>

        {/* Form Container - Ultra minimal card */}
        <Box
          sx={{
            background: 'var(--surface-base)',
            border: '1px solid var(--surface-border)',
            borderRadius: '12px',
            padding: '32px',
            transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
            '&:hover': {
              boxShadow: 'var(--shadow-lg)',
              borderColor: 'var(--mono-400)',
            },
          }}
        >
          {error && (
            <Box
              component={motion.div}
              initial={{ opacity: 0, x: -8 }}
              animate={{ opacity: 1, x: 0 }}
              sx={{ marginBottom: '24px' }}
            >
              <Alert
                severity="error"
                sx={{
                  background: 'var(--surface-elevated)',
                  border: '1px solid var(--surface-border)',
                  borderLeft: '3px solid var(--status-offline)',
                  borderRadius: '8px',
                  padding: '12px 16px',
                  fontSize: '0.875rem',
                  '& .MuiAlert-icon': {
                    fontSize: '18px',
                    color: 'var(--status-offline)',
                  },
                }}
              >
                {error}
              </Alert>
            </Box>
          )}

          <Box component="form" onSubmit={handleSubmit}>
            <Box sx={{ marginBottom: '20px' }}>
              <Typography
                component="label"
                htmlFor="username"
                sx={LABEL_SX}
              >
                Username
              </Typography>
              <TextField
                required
                fullWidth
                id="username"
                name="username"
                autoComplete="username"
                autoFocus
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                sx={TEXT_FIELD_SX}
              />
            </Box>

            <Box sx={{ marginBottom: '28px' }}>
              <Typography
                component="label"
                htmlFor="password"
                sx={LABEL_SX}
              >
                Password
              </Typography>
              <TextField
                required
                fullWidth
                name="password"
                type={showPassword ? 'text' : 'password'}
                id="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                InputProps={{
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton
                        aria-label={showPassword ? 'Hide password' : 'Show password'}
                        onClick={() => setShowPassword(!showPassword)}
                        edge="end"
                        size="small"
                        sx={{ color: 'var(--mono-500)' }}
                      >
                        {showPassword ? <VisibilityOff /> : <Visibility />}
                      </IconButton>
                    </InputAdornment>
                  ),
                }}
                sx={TEXT_FIELD_SX}
              />
            </Box>

            <Button
              component={motion.button}
              whileHover={{ scale: 1.01 }}
              whileTap={{ scale: 0.99 }}
              type="submit"
              fullWidth
              variant="contained"
              disabled={loading}
              sx={{
                background: 'var(--mono-950)',
                color: 'var(--mono-50)',
                borderRadius: '8px',
                padding: '12px 20px',
                fontSize: '0.875rem',
                fontWeight: 600,
                textTransform: 'none',
                boxShadow: 'var(--shadow-sm)',
                transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
                '&:hover': {
                  background: 'var(--mono-800)',
                  color: 'var(--mono-50)',
                  boxShadow: 'var(--shadow-md)',
                },
                '&:disabled': {
                  background: 'var(--mono-200)',
                  color: 'var(--mono-600)',
                },
              }}
            >
              {loading ? (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--mono-600)' }}>
                  <CircularProgress size={16} sx={{ color: 'var(--mono-600)' }} />
                  <span>Signing in...</span>
                </Box>
              ) : (
                'Sign In'
              )}
            </Button>
          </Box>
        </Box>

        {/* Demo credentials hint - only shown in development mode */}
        {import.meta.env.DEV && (
          <Box
            sx={{
              marginTop: '24px',
              padding: '16px',
              borderRadius: '8px',
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
            }}
          >
            <Typography
              sx={{
                fontSize: '0.75rem',
                fontWeight: 600,
                color: 'var(--mono-700)',
                textTransform: 'uppercase',
                letterSpacing: '0.05em',
              }}
            >
              Demo Credentials (Dev Only)
            </Typography>
            <Typography
              component="div"
              sx={{
                fontSize: '0.8125rem',
                color: 'var(--mono-600)',
                marginTop: '4px',
                lineHeight: 1.6,
              }}
            >
              See .env.example or QUICK_START.md for test credentials
            </Typography>
          </Box>
        )}
      </Box>
    </Box>
  )
}
