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
import LoadingSpinner from '../components/LoadingSpinner'
import { notificationsApi } from '../services/api'
import { Notification, NotificationType } from '../types'
import { formatTimestamp } from '../utils/statusHelpers'

const SEVERITY_COLORS = {
  ALERT: 'var(--status-offline)',
  WARNING: 'var(--status-maintenance)',
  INFO: 'var(--status-info)',
}

interface AlertRowProps {
  notification: Notification
  delay: number
  onMarkAsRead: (id: number) => void
}

const AlertRow = ({ notification, delay, onMarkAsRead }: AlertRowProps) => {
  const severityColor = SEVERITY_COLORS[notification.type as keyof typeof SEVERITY_COLORS] || SEVERITY_COLORS.INFO
  const isUnread = notification.status === 'UNREAD'

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
        background: isUnread ? 'var(--mono-50)' : 'transparent',
        '&:hover': {
          background: 'var(--mono-100)',
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
                  boxShadow: `0 0 0 2px ${severityColor}20`,
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
        {isUnread && notification.id !== undefined && (
          <Tooltip title="Mark as read">
            <IconButton
              size="small"
              onClick={() => onMarkAsRead(notification.id!)}
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
  const { data, isLoading } = useQuery({
    queryKey: ['notifications'],
    queryFn: async () => {
      const response = await notificationsApi.getAll()
      return response.data
    },
    refetchInterval: 30000,
  })

  const markAsReadMutation = useMutation({
    mutationFn: notificationsApi.markAsRead,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })

  if (isLoading) {
    return <LoadingSpinner />
  }

  const notifications = Array.isArray(data) ? data : []
  const unreadCount = notifications.filter((n: Notification) => n.status === 'UNREAD').length
  const alertCount = notifications.filter((n: Notification) => n.type === NotificationType.ALERT).length
  const warningCount = notifications.filter((n: Notification) => n.type === NotificationType.WARNING).length

  const handleMarkAsRead = (id: number) => {
    markAsReadMutation.mutate(id)
  }

  return (
    <Box sx={{ maxWidth: '1400px', margin: '0 auto', padding: '32px 24px' }}>
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
            fontSize: '2.25rem',
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
          gap: '16px',
          marginBottom: '24px',
        }}
      >
        <Box
          sx={{
            flex: 1,
            padding: '16px 20px',
            background: 'var(--surface-base)',
            border: '1px solid var(--surface-border)',
            borderRadius: '12px',
            borderLeft: `3px solid ${SEVERITY_COLORS.ALERT}`,
          }}
        >
          <Typography
            sx={{
              fontSize: '0.75rem',
              fontWeight: 500,
              color: 'var(--mono-500)',
              textTransform: 'uppercase',
              letterSpacing: '0.05em',
              marginBottom: '6px',
            }}
          >
            Critical
          </Typography>
          <Typography
            sx={{
              fontSize: '1.5rem',
              fontWeight: 600,
              fontVariantNumeric: 'tabular-nums',
              fontFamily: "'JetBrains Mono', monospace",
              color: 'var(--mono-950)',
            }}
          >
            {alertCount}
          </Typography>
        </Box>

        <Box
          sx={{
            flex: 1,
            padding: '16px 20px',
            background: 'var(--surface-base)',
            border: '1px solid var(--surface-border)',
            borderRadius: '12px',
            borderLeft: `3px solid ${SEVERITY_COLORS.WARNING}`,
          }}
        >
          <Typography
            sx={{
              fontSize: '0.75rem',
              fontWeight: 500,
              color: 'var(--mono-500)',
              textTransform: 'uppercase',
              letterSpacing: '0.05em',
              marginBottom: '6px',
            }}
          >
            Warnings
          </Typography>
          <Typography
            sx={{
              fontSize: '1.5rem',
              fontWeight: 600,
              fontVariantNumeric: 'tabular-nums',
              fontFamily: "'JetBrains Mono', monospace",
              color: 'var(--mono-950)',
            }}
          >
            {warningCount}
          </Typography>
        </Box>

        <Box
          sx={{
            flex: 1,
            padding: '16px 20px',
            background: 'var(--surface-base)',
            border: '1px solid var(--surface-border)',
            borderRadius: '12px',
            borderLeft: `3px solid ${SEVERITY_COLORS.INFO}`,
          }}
        >
          <Typography
            sx={{
              fontSize: '0.75rem',
              fontWeight: 500,
              color: 'var(--mono-500)',
              textTransform: 'uppercase',
              letterSpacing: '0.05em',
              marginBottom: '6px',
            }}
          >
            Unread
          </Typography>
          <Typography
            sx={{
              fontSize: '1.5rem',
              fontWeight: 600,
              fontVariantNumeric: 'tabular-nums',
              fontFamily: "'JetBrains Mono', monospace",
              color: 'var(--mono-950)',
            }}
          >
            {unreadCount}
          </Typography>
        </Box>
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
