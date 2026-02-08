import {
  CheckCircle as CheckCircleIcon,
  ClearAll as ClearAllIcon,
  Error as ErrorIcon,
  Info as InfoIcon,
  Warning as WarningIcon,
} from '@mui/icons-material'
import {
  Box,
  Button,
  CircularProgress,
  IconButton,
  Skeleton,
  Tooltip,
  Typography,
} from '@mui/material'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { memo, useCallback, useMemo, useRef, useEffect } from 'react'
import ErrorDisplay from '../components/ErrorDisplay'
import { notificationsApi, type Notification } from '../services/api/notifications'
import { NotificationType } from '../types'
import { formatTimestamp, getErrorMessage } from '../utils/statusHelpers'
import { showToast } from '../utils/toast'

const PAGE_SIZE = 20 // Smaller initial load for faster LCP

const SEVERITY_STYLES = {
  ALERT: { color: 'var(--status-offline)', shadow: 'var(--status-error-shadow)' },
  WARNING: { color: 'var(--status-maintenance)', shadow: 'var(--status-warning-shadow)' },
  INFO: { color: 'var(--status-info)', shadow: 'var(--status-info-shadow)' },
}

// Pre-rendered icons to avoid recreation on each render
const SEVERITY_ICONS = {
  [NotificationType.ALERT]: <ErrorIcon sx={{ fontSize: '16px' }} />,
  [NotificationType.WARNING]: <WarningIcon sx={{ fontSize: '16px' }} />,
  [NotificationType.INFO]: <InfoIcon sx={{ fontSize: '16px' }} />,
} as const

// Shared styles for stat boxes
const STAT_BOX_LABEL_SX = {
  fontSize: '0.75rem',
  fontWeight: 500,
  color: 'var(--mono-500)',
  textTransform: 'uppercase',
  letterSpacing: '0.05em',
  marginBottom: '6px',
} as const

const STAT_BOX_VALUE_SX = {
  fontSize: '1.5rem',
  fontWeight: 600,
  fontVariantNumeric: 'tabular-nums',
  fontFamily: "'JetBrains Mono', monospace",
  color: 'var(--mono-950)',
} as const

interface StatBoxProps {
  label: string
  value: number | string
  borderColor: string
}

function StatBox({ label, value, borderColor }: Readonly<StatBoxProps>) {
  return (
    <Box
      sx={{
        flex: 1,
        padding: '16px 20px',
        background: 'var(--surface-base)',
        border: '1px solid var(--surface-border)',
        borderRadius: '12px',
        borderLeft: `3px solid ${borderColor}`,
      }}
    >
      <Typography sx={STAT_BOX_LABEL_SX}>{label}</Typography>
      <Typography sx={STAT_BOX_VALUE_SX}>{value}</Typography>
    </Box>
  )
}

// Skeleton row for loading state - renders immediately for fast LCP
function AlertRowSkeleton() {
  return (
    <Box sx={{ padding: '20px 24px', borderBottom: '1px solid var(--surface-border)' }}>
      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: '16px' }}>
        <Skeleton variant="circular" width={16} height={16} sx={{ marginTop: '2px' }} />
        <Box sx={{ flex: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px' }}>
            <Skeleton variant="text" width={120} height={20} />
            <Skeleton variant="text" width={60} height={16} />
          </Box>
          <Skeleton variant="text" width="80%" height={20} sx={{ marginBottom: '8px' }} />
          <Skeleton variant="text" width={140} height={16} />
        </Box>
      </Box>
    </Box>
  )
}

interface AlertRowProps {
  notification: Notification
  onDelete: (id: number) => void
}

// Memoized row component - only re-renders when notification or callback changes
const AlertRow = memo(function AlertRow({ notification, onDelete }: Readonly<AlertRowProps>) {
  const severityStyle = SEVERITY_STYLES[notification.type as keyof typeof SEVERITY_STYLES] || SEVERITY_STYLES.INFO
  const severityColor = severityStyle.color
  const isUnread = notification.status === 'UNREAD'
  const notificationId = notification.id
  const icon = SEVERITY_ICONS[notification.type as keyof typeof SEVERITY_ICONS] || SEVERITY_ICONS[NotificationType.INFO]

  return (
    <Box
      sx={{
        position: 'relative',
        padding: '20px 24px',
        borderBottom: '1px solid var(--surface-border)',
        background: isUnread ? 'var(--surface-elevated)' : 'transparent',
        '&:hover': {
          background: 'var(--surface-hover)',
        },
        '&:last-child': {
          borderBottom: 'none',
        },
        '&::before': {
          content: '""',
          position: 'absolute',
          left: 0,
          top: 0,
          bottom: 0,
          width: '3px',
          background: severityColor,
        },
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: '16px' }}>
        {/* Icon */}
        <Box
          sx={{
            marginTop: '2px',
            color: severityColor,
            display: 'flex',
            alignItems: 'center',
          }}
        >
          {icon}
        </Box>

        {/* Content */}
        <Box sx={{ flex: 1, minWidth: 0 }}>
          {/* Header */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px' }}>
            <Typography
              sx={{
                fontSize: '0.875rem',
                fontWeight: 600,
                color: 'var(--mono-950)',
              }}
            >
              {notification.stationName || `Station ${notification.stationId}`}
            </Typography>
            {isUnread && (
              <Box
                sx={{
                  width: '6px',
                  height: '6px',
                  borderRadius: '50%',
                  background: severityColor,
                  boxShadow: `0 0 0 2px ${severityStyle.shadow}`,
                }}
              />
            )}
            <Typography
              sx={{
                fontSize: '0.75rem',
                fontWeight: 500,
                color: 'var(--mono-500)',
                textTransform: 'uppercase',
                letterSpacing: '0.05em',
              }}
            >
              {notification.type}
            </Typography>
          </Box>

          {/* Message */}
          <Typography
            sx={{
              fontSize: '0.875rem',
              color: 'var(--mono-700)',
              lineHeight: 1.6,
              marginBottom: '8px',
            }}
          >
            {notification.message}
          </Typography>

          {/* Timestamp */}
          <Typography
            sx={{
              fontSize: '0.75rem',
              fontFamily: "'JetBrains Mono', monospace",
              color: 'var(--mono-500)',
            }}
          >
            {formatTimestamp(notification.createdAt)}
          </Typography>
        </Box>

        {/* Action */}
        {isUnread && notificationId !== undefined && (
          <Tooltip title="Mark as read">
            <IconButton
              size="small"
              onClick={() => onDelete(notificationId)}
              sx={{
                width: '32px',
                height: '32px',
                color: 'var(--mono-600)',
                '&:hover': {
                  background: 'var(--mono-200)',
                  color: 'var(--mono-950)',
                },
              }}
            >
              <CheckCircleIcon sx={{ fontSize: '18px' }} />
            </IconButton>
          </Tooltip>
        )}
      </Box>
    </Box>
  )
})

export default function Alerts() {
  const queryClient = useQueryClient()
  const loadMoreRef = useRef<HTMLDivElement>(null)

  // Infinite query for paginated data
  const {
    data,
    isLoading,
    error,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery({
    queryKey: ['notifications-paged', 'UNREAD'],
    queryFn: async ({ pageParam = 0 }) => {
      const response = await notificationsApi.getPaged(pageParam, PAGE_SIZE, 'UNREAD')
      return response.data
    },
    getNextPageParam: (lastPage) => {
      if (lastPage.last) return undefined
      return lastPage.number + 1
    },
    initialPageParam: 0,
  })

  // Intersection observer for infinite scroll
  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && hasNextPage && !isFetchingNextPage) {
          fetchNextPage()
        }
      },
      { threshold: 0.1 }
    )

    const currentRef = loadMoreRef.current
    if (currentRef) {
      observer.observe(currentRef)
    }

    return () => {
      if (currentRef) {
        observer.unobserve(currentRef)
      }
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage])

  const deleteMutation = useMutation({
    mutationFn: notificationsApi.deleteNotification,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications-paged'] })
      queryClient.invalidateQueries({ queryKey: ['notification-counts'] })
      queryClient.invalidateQueries({ queryKey: ['recent-notifications'] })
    },
    onError: (err: Error) => {
      queryClient.invalidateQueries({ queryKey: ['notifications-paged'] })
      showToast.error(`Failed to delete notification: ${err.message}`)
    },
  })

  const clearAllMutation = useMutation({
    mutationFn: notificationsApi.clearAllUnread,
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['notifications-paged'] })
      queryClient.invalidateQueries({ queryKey: ['notification-counts'] })
      queryClient.invalidateQueries({ queryKey: ['recent-notifications'] })
      showToast.success(`Cleared ${response.data.deleted} alerts`)
    },
    onError: (err: Error) => {
      showToast.error(`Failed to clear alerts: ${err.message}`)
    },
  })

  // Flatten all pages into a single array
  const notifications = useMemo(() => {
    if (!data?.pages) return []
    return data.pages.flatMap((page) => page.content)
  }, [data])

  // Get total count from first page
  const totalCount = data?.pages[0]?.totalElements ?? 0

  // Use lightweight counts endpoint for accurate stats (doesn't load all data)
  const { data: counts } = useQuery({
    queryKey: ['notification-counts'],
    queryFn: async () => {
      const response = await notificationsApi.getCounts()
      return response.data
    },
    staleTime: 30000, // Cache for 30s to reduce API calls
  })

  const unreadCount = counts?.unread ?? 0
  const alertCount = counts?.alerts ?? 0
  const warningCount = counts?.warnings ?? 0

  const handleDelete = useCallback((id: number) => {
    deleteMutation.mutate(id)
  }, [deleteMutation])

  if (error) {
    return <ErrorDisplay title="Failed to load alerts" message={getErrorMessage(error)} />
  }

  return (
    <Box sx={{ maxWidth: '1400px', margin: '0 auto', padding: { xs: '16px 12px', sm: '24px 16px', md: '32px 24px' } }}>
      {/* Header - renders immediately for fast LCP */}
      <Box sx={{ marginBottom: '32px', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography
            variant="h1"
            sx={{
              fontSize: { xs: '1.5rem', sm: '1.75rem', md: '2.25rem' },
              fontWeight: 700,
              letterSpacing: '-0.025em',
              color: 'var(--mono-950)',
              marginBottom: '8px',
            }}
          >
            Alerts
          </Typography>
          <Typography
            sx={{
              fontSize: '0.875rem',
              color: 'var(--mono-500)',
              letterSpacing: '0.01em',
            }}
          >
            {isLoading
              ? 'Loading notifications...'
              : `System notifications and alerts · ${totalCount.toLocaleString()} total · ${notifications.length.toLocaleString()} loaded`}
          </Typography>
        </Box>
        <Tooltip title={unreadCount === 0 ? 'No unread alerts' : 'Mark all alerts as read'}>
          <span>
            <Button
              variant="outlined"
              size="small"
              startIcon={clearAllMutation.isPending ? <CircularProgress size={16} /> : <ClearAllIcon />}
              onClick={() => clearAllMutation.mutate()}
              disabled={clearAllMutation.isPending || unreadCount === 0}
              sx={{
                borderColor: 'var(--mono-300)',
                color: 'var(--mono-700)',
                '&:hover': { borderColor: 'var(--mono-400)', background: 'var(--surface-hover)' },
                '&.Mui-disabled': { borderColor: 'var(--mono-200)', color: 'var(--mono-400)' },
              }}
            >
              Clear All ({unreadCount})
            </Button>
          </span>
        </Tooltip>
      </Box>

      {/* Stats Bar */}
      <Box
        sx={{
          display: 'flex',
          flexDirection: { xs: 'column', sm: 'row' },
          gap: { xs: '12px', sm: '16px' },
          marginBottom: '24px',
        }}
      >
        <StatBox label="Critical" value={isLoading ? '—' : alertCount} borderColor={SEVERITY_STYLES.ALERT.color} />
        <StatBox label="Warnings" value={isLoading ? '—' : warningCount} borderColor={SEVERITY_STYLES.WARNING.color} />
        <StatBox label="Unread" value={isLoading ? '—' : unreadCount} borderColor={SEVERITY_STYLES.INFO.color} />
      </Box>

      {/* Alerts List */}
      <Box
        sx={{
          background: 'var(--surface-base)',
          border: '1px solid var(--surface-border)',
          borderRadius: '12px',
          overflow: 'hidden',
        }}
      >
        {isLoading ? (
          // Skeleton loading - shows page structure immediately for fast LCP
          <Box>
            {Array.from({ length: 5 }).map((_, i) => (
              <AlertRowSkeleton key={i} />
            ))}
          </Box>
        ) : notifications.length === 0 ? (
          <Box sx={{ padding: '48px 24px', textAlign: 'center' }}>
            <Typography
              sx={{
                fontSize: '0.875rem',
                color: 'var(--mono-500)',
              }}
            >
              No alerts or notifications
            </Typography>
          </Box>
        ) : (
          <Box>
            {notifications.map((notification) => (
              <AlertRow
                key={notification.id}
                notification={notification}
                onDelete={handleDelete}
              />
            ))}

            {/* Load more trigger */}
            <Box
              ref={loadMoreRef}
              sx={{
                padding: '16px',
                display: 'flex',
                justifyContent: 'center',
                alignItems: 'center',
                minHeight: '60px',
              }}
            >
              {isFetchingNextPage ? (
                <CircularProgress size={24} />
              ) : hasNextPage ? (
                <Button
                  variant="text"
                  onClick={() => fetchNextPage()}
                  sx={{ color: 'var(--mono-600)' }}
                >
                  Load more
                </Button>
              ) : notifications.length > 0 ? (
                <Typography sx={{ fontSize: '0.875rem', color: 'var(--mono-500)' }}>
                  All {totalCount.toLocaleString()} notifications loaded
                </Typography>
              ) : null}
            </Box>
          </Box>
        )}
      </Box>
    </Box>
  )
}
