import { Box, CircularProgress, Typography } from '@mui/material'

interface LoadingSpinnerProps {
  /** Minimum height of the container. Default: '400px' */
  readonly minHeight?: string | number
  /** Optional message to show below the spinner */
  readonly message?: string
  /** Size of the spinner. Default: 40 */
  readonly size?: number
}

/**
 * Centered loading spinner component.
 * Use this for consistent loading states across all pages.
 */
export default function LoadingSpinner({
  minHeight = '400px',
  message,
  size = 40,
}: LoadingSpinnerProps) {
  return (
    <Box
      display="flex"
      flexDirection="column"
      justifyContent="center"
      alignItems="center"
      minHeight={minHeight}
      gap={2}
    >
      <CircularProgress size={size} />
      {message && (
        <Typography variant="body2" color="text.secondary">
          {message}
        </Typography>
      )}
    </Box>
  )
}
