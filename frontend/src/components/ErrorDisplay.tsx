/**
 * ErrorDisplay - Reusable error UI component for API failures.
 *
 * Displays a centered error message with retry button.
 * Used across Dashboard, MapView, FiveGDashboard, PowerDashboard.
 */
import { ErrorOutline as ErrorIcon, Refresh as RefreshIcon } from '@mui/icons-material'
import { Box, Button, Typography } from '@mui/material'

interface ErrorDisplayProps {
  readonly title: string
  readonly message?: string
  readonly onRetry?: () => void
}

export default function ErrorDisplay({ title, message, onRetry }: ErrorDisplayProps) {
  const handleRetry = onRetry ?? (() => globalThis.location.reload())

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '60vh',
        padding: '32px',
        textAlign: 'center',
      }}
    >
      <ErrorIcon sx={{ fontSize: 56, color: 'var(--status-offline)', mb: 2 }} />
      <Typography
        variant="h5"
        sx={{ fontWeight: 600, color: 'var(--mono-950)', mb: 1 }}
      >
        {title}
      </Typography>
      <Typography sx={{ color: 'var(--mono-600)', mb: 3, maxWidth: 400 }}>
        {message || 'Unable to fetch data. Please check your connection and try again.'}
      </Typography>
      <Button
        variant="contained"
        startIcon={<RefreshIcon />}
        onClick={handleRetry}
        sx={{
          background: 'var(--mono-950)',
          color: 'var(--mono-50)',
          textTransform: 'none',
          fontWeight: 500,
          '&:hover': { background: 'var(--mono-900)' },
        }}
      >
        Retry
      </Button>
    </Box>
  )
}
