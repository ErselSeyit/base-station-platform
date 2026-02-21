/**
 * Threshold Refresh Button
 *
 * A small button component that allows manual refresh of threshold configurations.
 * Only re-renders when threshold state changes - doesn't cause page reload.
 *
 * Usage:
 *   <ThresholdRefreshButton />
 *   <ThresholdRefreshButton showLastUpdated />
 *   <ThresholdRefreshButton size="small" variant="text" />
 */
import { IconButton, Tooltip, CircularProgress, Box, Typography } from '@mui/material'
import RefreshIcon from '@mui/icons-material/Refresh'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline'
import { useThresholds } from '../contexts/ThresholdContext'

interface ThresholdRefreshButtonProps {
  /** Show last updated timestamp */
  readonly showLastUpdated?: boolean
  /** Button size */
  readonly size?: 'small' | 'medium' | 'large'
  /** Button variant */
  readonly variant?: 'icon' | 'text'
}

export function ThresholdRefreshButton({
  showLastUpdated = false,
  size = 'small',
  variant = 'icon',
}: ThresholdRefreshButtonProps) {
  const { refresh, isRefreshing, isFromApi, lastUpdated, error } = useThresholds()

  const handleRefresh = () => {
    refresh()
  }

  const formatTime = (date: Date | null) => {
    if (!date) return 'Never'
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }

  const getTooltipText = () => {
    if (isRefreshing) return 'Refreshing thresholds...'
    if (error) return `Error: ${error}. Click to retry.`
    if (!isFromApi) return 'Using default thresholds. Click to fetch from server.'
    return `Thresholds from server. Last updated: ${formatTime(lastUpdated)}`
  }

  const getStatusIcon = () => {
    if (error) return <ErrorOutlineIcon sx={{ fontSize: 12, color: 'var(--status-offline)' }} />
    if (isFromApi) return <CheckCircleIcon sx={{ fontSize: 12, color: 'var(--status-active)' }} />
    return null
  }

  if (variant === 'text') {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <Tooltip title={getTooltipText()}>
          <IconButton
            onClick={handleRefresh}
            disabled={isRefreshing}
            size={size}
            sx={{
              color: error ? 'var(--status-offline)' : 'var(--mono-500)',
              '&:hover': { color: 'var(--mono-700)' },
            }}
          >
            {isRefreshing ? (
              <CircularProgress size={size === 'small' ? 16 : 20} color="inherit" />
            ) : (
              <RefreshIcon fontSize={size} />
            )}
          </IconButton>
        </Tooltip>
        {showLastUpdated && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            {getStatusIcon()}
            <Typography variant="caption" sx={{ color: 'var(--mono-500)' }}>
              {isFromApi ? formatTime(lastUpdated) : 'Defaults'}
            </Typography>
          </Box>
        )}
      </Box>
    )
  }

  return (
    <Tooltip title={getTooltipText()}>
      <span>
        <IconButton
          onClick={handleRefresh}
          disabled={isRefreshing}
          size={size}
          sx={{
            color: error ? 'var(--status-offline)' : 'var(--mono-500)',
            '&:hover': { color: 'var(--mono-700)' },
          }}
        >
          {isRefreshing ? (
            <CircularProgress size={size === 'small' ? 16 : 20} color="inherit" />
          ) : (
            <RefreshIcon fontSize={size} />
          )}
        </IconButton>
      </span>
    </Tooltip>
  )
}

export default ThresholdRefreshButton
