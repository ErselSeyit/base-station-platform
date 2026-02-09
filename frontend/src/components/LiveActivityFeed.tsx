import { Circle, CheckCircle as CheckCircleIcon, AutoFixHigh as AutoFixHighIcon } from '@mui/icons-material'
import { Box, Chip, Typography } from '@mui/material'
import { motion, AnimatePresence } from 'framer-motion'
import { formatDistanceToNow } from 'date-fns'
import { useQuery } from '@tanstack/react-query'
import { notificationsApi, type Notification } from '../services/api/notifications'
import PulsingStatus from './PulsingStatus'
import { getSeverityColorVar, POLLING_INTERVALS } from '../constants/designSystem'

export default function LiveActivityFeed() {
  // Use lightweight recent endpoint (10 items) instead of loading all notifications
  const { data: notifications, error } = useQuery({
    queryKey: ['recent-notifications'],
    queryFn: async () => {
      const response = await notificationsApi.getRecent()
      return response.data
    },
    refetchInterval: POLLING_INTERVALS.FAST,
  })

  const recentNotifications = (notifications ?? []).slice(0, 5)

  // Show error state if API fails
  if (error) {
    return (
      <Box>
        <Typography variant="body2" sx={{ textAlign: 'center', py: 4, color: 'var(--status-offline)' }}>
          Unable to load activity feed
        </Typography>
      </Box>
    )
  }

  return (
    <Box>
      <Typography
        variant="h6"
        sx={{
          fontWeight: 600,
          mb: 2,
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          color: 'var(--mono-950)',
        }}
      >
        <Circle sx={{ fontSize: 8, color: 'var(--status-active)', animation: 'pulse 2s infinite' }} />
        Live Activity Feed
      </Typography>

      <AnimatePresence mode="popLayout">
        {recentNotifications.length === 0 ? (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          >
            <Typography variant="body2" sx={{ textAlign: 'center', py: 4, color: 'var(--mono-500)' }}>
              No recent activity
            </Typography>
          </motion.div>
        ) : (
          recentNotifications.map((notification: Notification, index: number) => {
            const isResolved = notification.status === 'RESOLVED'
            const isUnread = notification.status === 'UNREAD'
            const borderColor = isResolved ? 'var(--status-active)' : getSeverityColorVar(notification.type)

            return (
              <motion.div
                key={notification.id}
                initial={{ opacity: 0, x: -20 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: 20 }}
                transition={{
                  delay: index * 0.05,
                  duration: 0.3,
                  ease: [0.16, 1, 0.3, 1]
                }}
                layout
              >
                <Box
                  sx={{
                    display: 'flex',
                    gap: 2,
                    p: 2,
                    mb: 1.5,
                    borderRadius: 2,
                    background: 'var(--surface-elevated)',
                    borderLeft: '3px solid',
                    borderColor: borderColor,
                    opacity: isResolved ? 0.7 : 1,
                    transition: 'all 0.2s ease',
                    '&:hover': {
                      background: 'var(--surface-hover)',
                      transform: 'translateX(4px)',
                    },
                  }}
                >
                  <Box sx={{ mt: 0.5 }}>
                    {isResolved ? (
                      <AutoFixHighIcon sx={{ fontSize: 14, color: 'var(--status-active)' }} />
                    ) : (
                      <PulsingStatus
                        color={getSeverityColorVar(notification.type)}
                        size={10}
                        animate={isUnread}
                      />
                    )}
                  </Box>
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    {/* Header with station name and resolved chip */}
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5, flexWrap: 'wrap' }}>
                      <Typography
                        variant="caption"
                        sx={{
                          fontWeight: 600,
                          color: isResolved ? 'var(--mono-500)' : 'var(--mono-700)',
                        }}
                      >
                        {notification.stationName || `Station ${notification.stationId}`}
                      </Typography>
                      {isResolved && (
                        <Chip
                          icon={<CheckCircleIcon sx={{ fontSize: '12px !important' }} />}
                          label="Resolved"
                          size="small"
                          sx={{
                            height: '18px',
                            fontSize: '0.65rem',
                            fontWeight: 600,
                            backgroundColor: 'var(--status-active)',
                            color: 'white',
                            '& .MuiChip-icon': { color: 'white' },
                            '& .MuiChip-label': { px: 0.5 },
                          }}
                        />
                      )}
                    </Box>
                    {/* Message */}
                    <Typography
                      variant="body2"
                      sx={{
                        fontWeight: isUnread ? 600 : 400,
                        mb: 0.5,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                        color: isResolved ? 'var(--mono-500)' : 'var(--mono-950)',
                        textDecoration: isResolved ? 'line-through' : 'none',
                      }}
                    >
                      {notification.message}
                    </Typography>
                    {/* Timestamp */}
                    <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                      <Typography variant="caption" sx={{ color: 'var(--mono-500)' }}>
                        {notification.createdAt
                          ? formatDistanceToNow(new Date(notification.createdAt), { addSuffix: true })
                          : 'Just now'}
                      </Typography>
                      {isResolved && notification.resolvedAt && (
                        <Typography variant="caption" sx={{ color: 'var(--status-active)' }}>
                          · Resolved {formatDistanceToNow(new Date(notification.resolvedAt), { addSuffix: true })}
                        </Typography>
                      )}
                    </Box>
                  </Box>
                </Box>
              </motion.div>
            )
          })
        )}
      </AnimatePresence>
    </Box>
  )
}
