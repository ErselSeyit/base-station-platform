import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Container,
  TextField,
  Button,
  Typography,
  Box,
  Alert,
  CircularProgress,
} from '@mui/material'
import { CellTower, LockOpen } from '@mui/icons-material'
import { motion } from 'framer-motion'
import confetti from 'canvas-confetti'
import GlassCard from '../components/GlassCard'
import { showToast } from '../components/ToastProvider'
import { authService } from '../services/authService'
import { useTheme } from '../contexts/ThemeContext'

export default function Login() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const { mode } = useTheme()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)

    try {
      await authService.login({ username, password })

      // Celebration confetti on successful login!
      confetti({
        particleCount: 100,
        spread: 70,
        origin: { y: 0.6 }
      })

      showToast.success('Welcome back! Logging you in...')

      // Small delay to show the success message
      setTimeout(() => {
        navigate('/')
      }, 800)
    } catch (err) {
      console.error('Login error:', err)
      setError('Invalid username or password')
      showToast.error('Login failed. Please check your credentials.')
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
        background: mode === 'dark'
          ? 'radial-gradient(ellipse at top, rgba(100, 181, 246, 0.15) 0%, transparent 50%), radial-gradient(ellipse at bottom, rgba(186, 104, 200, 0.15) 0%, transparent 50%), #0a0e27'
          : 'radial-gradient(ellipse at top, rgba(25, 118, 210, 0.1) 0%, transparent 50%), radial-gradient(ellipse at bottom, rgba(156, 39, 176, 0.1) 0%, transparent 50%), #f5f7fa',
        position: 'relative',
        overflow: 'hidden',
        '&::before': {
          content: '""',
          position: 'absolute',
          top: '-50%',
          right: '-50%',
          width: '100%',
          height: '100%',
          background: 'radial-gradient(circle, rgba(100, 181, 246, 0.1) 0%, transparent 70%)',
          animation: 'float 20s infinite ease-in-out',
        },
        '&::after': {
          content: '""',
          position: 'absolute',
          bottom: '-50%',
          left: '-50%',
          width: '100%',
          height: '100%',
          background: 'radial-gradient(circle, rgba(186, 104, 200, 0.1) 0%, transparent 70%)',
          animation: 'float 25s infinite ease-in-out reverse',
        },
      }}
    >
      <Container maxWidth="sm" sx={{ position: 'relative', zIndex: 1 }}>
        <Box
          component={motion.div}
          initial={{ opacity: 0, scale: 0.9, y: 20 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
        >
        <GlassCard
          gradient
          sx={{ p: 5 }}
        >
          {/* Logo and Title */}
          <Box sx={{ textAlign: 'center', mb: 4 }}>
            <Box
              component={motion.div}
              animate={{
                rotate: [0, 5, -5, 0],
                scale: [1, 1.05, 1],
              }}
              transition={{
                duration: 3,
                repeat: Infinity,
                ease: 'easeInOut',
              }}
              sx={{
                display: 'inline-flex',
                p: 2,
                borderRadius: 3,
                background: mode === 'dark'
                  ? 'linear-gradient(135deg, rgba(100, 181, 246, 0.2) 0%, rgba(156, 39, 176, 0.2) 100%)'
                  : 'linear-gradient(135deg, rgba(25, 118, 210, 0.15) 0%, rgba(156, 39, 176, 0.15) 100%)',
                mb: 2,
              }}
            >
              <CellTower
                sx={{
                  fontSize: 64,
                  background: 'linear-gradient(135deg, #64b5f6 0%, #ba68c8 100%)',
                  backgroundClip: 'text',
                  WebkitBackgroundClip: 'text',
                  WebkitTextFillColor: 'transparent',
                }}
              />
            </Box>
            <Typography
              variant="h3"
              sx={{
                fontWeight: 700,
                mb: 1,
                background: mode === 'dark'
                  ? 'linear-gradient(135deg, #64b5f6 0%, #90caf9 50%, #ba68c8 100%)'
                  : 'linear-gradient(135deg, #1976d2 0%, #42a5f5 50%, #9c27b0 100%)',
                backgroundClip: 'text',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
              }}
            >
              Base Station Platform
            </Typography>
            <Typography variant="body1" color="text.secondary">
              Operations & Maintenance Dashboard
            </Typography>
          </Box>

          {error && (
            <motion.div
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
            >
              <Alert severity="error" sx={{ mb: 3, borderRadius: 2 }}>
                {error}
              </Alert>
            </motion.div>
          )}

          <Box component="form" onSubmit={handleSubmit}>
            <TextField
              margin="normal"
              required
              fullWidth
              id="username"
              label="Username"
              name="username"
              autoComplete="username"
              autoFocus
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              sx={{
                '& .MuiOutlinedInput-root': {
                  borderRadius: 2,
                  transition: 'all 0.3s ease',
                  '&:hover': {
                    transform: 'translateY(-2px)',
                  },
                  '&.Mui-focused': {
                    boxShadow: mode === 'dark'
                      ? '0 0 0 3px rgba(100, 181, 246, 0.2)'
                      : '0 0 0 3px rgba(25, 118, 210, 0.1)',
                  },
                },
              }}
            />
            <TextField
              margin="normal"
              required
              fullWidth
              name="password"
              label="Password"
              type="password"
              id="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              sx={{
                '& .MuiOutlinedInput-root': {
                  borderRadius: 2,
                  transition: 'all 0.3s ease',
                  '&:hover': {
                    transform: 'translateY(-2px)',
                  },
                  '&.Mui-focused': {
                    boxShadow: mode === 'dark'
                      ? '0 0 0 3px rgba(100, 181, 246, 0.2)'
                      : '0 0 0 3px rgba(25, 118, 210, 0.1)',
                  },
                },
              }}
            />
            <Button
              component={motion.button}
              whileHover={{ scale: 1.02 }}
              whileTap={{ scale: 0.98 }}
              type="submit"
              fullWidth
              variant="contained"
              disabled={loading}
              startIcon={loading ? <CircularProgress size={20} /> : <LockOpen />}
              sx={{
                mt: 4,
                mb: 2,
                py: 1.5,
                borderRadius: 2,
                fontSize: '1rem',
                fontWeight: 600,
                background: 'linear-gradient(135deg, #1976d2 0%, #42a5f5 50%, #9c27b0 100%)',
                boxShadow: mode === 'dark'
                  ? '0 8px 24px rgba(100, 181, 246, 0.3)'
                  : '0 8px 24px rgba(25, 118, 210, 0.2)',
                '&:hover': {
                  background: 'linear-gradient(135deg, #1565c0 0%, #1976d2 50%, #7b1fa2 100%)',
                  boxShadow: mode === 'dark'
                    ? '0 12px 32px rgba(100, 181, 246, 0.4)'
                    : '0 12px 32px rgba(25, 118, 210, 0.3)',
                },
              }}
            >
              {loading ? 'Signing in...' : 'Sign In'}
            </Button>
          </Box>

          <Box
            sx={{
              mt: 4,
              p: 3,
              borderRadius: 2,
              background: mode === 'dark'
                ? 'rgba(100, 181, 246, 0.05)'
                : 'rgba(25, 118, 210, 0.03)',
              border: '1px solid',
              borderColor: mode === 'dark'
                ? 'rgba(100, 181, 246, 0.1)'
                : 'rgba(25, 118, 210, 0.1)',
            }}
          >
            <Typography variant="body2" fontWeight={600} color="text.secondary" gutterBottom>
              Demo Credentials
            </Typography>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5, mt: 1 }}>
              <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                Admin: <strong>admin</strong> / <strong>admin</strong>
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                User: <strong>user</strong> / <strong>user</strong>
              </Typography>
            </Box>
          </Box>
        </GlassCard>
        </Box>
      </Container>
    </Box>
  )
}
