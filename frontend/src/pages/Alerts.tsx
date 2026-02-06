import {
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon,
  Info as InfoIcon,
  Warning as WarningIcon,
} from '@mui/icons-material'
import {
  Box,
  IconButton,
  Tooltip,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { useMemo } from 'react'
import ErrorDisplay from '../components/ErrorDisplay'
import LoadingSpinner from '../components/LoadingSpinner'
import { notificationsApi } from '../services/api'
import { Notification, NotificationType } from '../types'
import { ensureArray } from '../utils/arrayUtils'
import { formatTimestamp, getErrorMessage } from '../utils/statusHelpers'
import { showToast } from '../utils/toast'
import { POLLING_INTERVALS } from '../constants/designSystem'

const SEVERITY_STYLES = {
  ALERT: { color: 'var(--status-offline)', shadow: 'var(--status-error-shadow)' },
  WARNING: { color: 'var(--status-maintenance)', shadow: 'var(--status-warning-shadow)' },
  INFO: { color: 'var(--status-info)', shadow: 'var(--status-info-shadow)' },
}

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
  value: number
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

interface AlertRowProps {
  notification: Notification
  delay: number
  onMarkAsRead: (id: number) => void
}

const AlertRow = ({ notification, delay, onMarkAsRead }: Readonly<AlertRowProps>) => {
  const severityStyle = SEVERITY_STYLES[notification.type as keyof typeof SEVERITY_STYLES] || SEVERITY_STYLES.INFO
  const severityColor = severityStyle.color
  const isUnread = notification.status === 'UNREAD'
  const notificationId = notification.id

  const getIcon = (type: NotificationType) => {
    switch (type) {
      case NotificationType.ALERT:
        return <ErrorIcon sx={{ fontSize: '16px' }} />
      case NotificationType.WARNING:
        return <WarningIcon sx={{ fontSize: '16px' }} />
      case NotificationType.INFO:
        return <InfoIcon sx={{ fontSize: '16px' }} />
      default:
        return <InfoIcon sx={{ fontSize: '16px' }} />
    }
  }

  return (
    <Box
      component={motion.div}
      initial={{ opacity: 0, x: -16 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ delay, duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
      sx={{
        position: 'relative',
        padding: '20px 24px',
        borderBottom: '1px solid var(--surface-border)',
        transition: 'background 0.15s cubic-bezier(0.16, 1, 0.3, 1)',
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
          {getIcon(notification.type)}
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
              onClick={() => onMarkAsRead(notificationId)}
              sx={{
                width: '32px',
                height: '32px',
                color: 'var(--mono-600)',
                transition: 'all 0.15s cubic-bezier(0.16, 1, 0.3, 1)',
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
}

export default function Alerts() {
  const queryClient = useQueryClient()
  const { data, isLoading, error } = useQuery({
    queryKey: ['notifications'],
    queryFn: async () => {
      const response = await notificationsApi.getAll()
      return response.data
    },
    refetchInterval: POLLING_INTERVALS.NORMAL,
  })

  const markAsReadMutation = useMutation({
    mutationFn: notificationsApi.markAsRead,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
    onError: (err: Error) => {
      // Refresh to show current state even on error
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
      showToast.error(`Failed to mark notification as read: ${err.message}`)
    },
  })

  const notifications = ensureArray(data as Notification[])

  // Calculate all counts in a single iteration (useMemo must be before early returns)
  const { unreadCount, alertCount, warningCount } = useMemo(() => {
    let unread = 0
    let alerts = 0
    let warnings = 0
    for (const n of notifications) {
      if (n.status === 'UNREAD') unread++
      if (n.type === NotificationType.ALERT) alerts++
      if (n.type === NotificationType.WARNING) warnings++
    }
    return { unreadCount: unread, alertCount: alerts, warningCount: warnings }
  }, [notifications])

  const handleMarkAsRead = (id: number) => {
    markAsReadMutation.mutate(id)
  }

  if (isLoading) {
    return <LoadingSpinner />
  }

  if (error) {
    return <ErrorDisplay title="Failed to load alerts" message={getErrorMessage(error)} />
  }

  return (
    <Box sx={{ maxWidth: '1400px', margin: '0 auto', padding: { xs: '16px 12px', sm: '24px 16px', md: '32px 24px' } }}>
      {/* Header - Brutally minimal */}
      <Box
        component={motion.div}
        initial={{ opacity: 0, y: -16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
        sx={{ marginBottom: '32px' }}
      >
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
          System notifications and alerts · {notifications.length} total · {unreadCount} unread
        </Typography>
      </Box>

      {/* Stats Bar */}
      <Box
        component={motion.div}
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.05, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
        sx={{
          display: 'flex',
          flexDirection: { xs: 'column', sm: 'row' },
          gap: { xs: '12px', sm: '16px' },
          marginBottom: '24px',
        }}
      >
        <StatBox label="Critical" value={alertCount} borderColor={SEVERITY_STYLES.ALERT.color} />
        <StatBox label="Warnings" value={warningCount} borderColor={SEVERITY_STYLES.WARNING.color} />
        <StatBox label="Unread" value={unreadCount} borderColor={SEVERITY_STYLES.INFO.color} />
      </Box>

      {/* Alerts List */}
      <Box
        component={motion.div}
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
        sx={{
          background: 'var(--surface-base)',
          border: '1px solid var(--surface-border)',
          borderRadius: '12px',
          overflow: 'hidden',
        }}
      >
        {notifications.length === 0 ? (
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
            {notifications.map((notification: Notification, idx: number) => (
              <AlertRow
                key={notification.id}
                notification={notification}
                delay={0.15 + idx * 0.03}
                onMarkAsRead={handleMarkAsRead}
              />
            ))}
          </Box>
        )}
      </Box>
    </Box>
  )
}
