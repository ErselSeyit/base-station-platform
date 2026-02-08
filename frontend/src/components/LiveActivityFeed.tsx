import { Circle } from '@mui/icons-material'
import { Box, Typography } from '@mui/material'
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
          recentNotifications.map((notification: Notification, index: number) => (
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
                  borderColor: getSeverityColorVar(notification.type),
                  transition: 'all 0.2s ease',
                  '&:hover': {
                    background: 'var(--surface-hover)',
                    transform: 'translateX(4px)',
                  },
                }}
              >
                <Box sx={{ mt: 0.5 }}>
                  <PulsingStatus
                    color={getSeverityColorVar(notification.type)}
                    size={10}
                    animate={notification.status === 'UNREAD'}
                  />
                </Box>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography
                    variant="body2"
                    sx={{
                      fontWeight: notification.status === 'UNREAD' ? 600 : 400,
                      mb: 0.5,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                      color: 'var(--mono-950)',
                    }}
                  >
                    {notification.message}
                  </Typography>
                  <Typography variant="caption" sx={{ color: 'var(--mono-500)' }}>
                    {notification.createdAt
                      ? formatDistanceToNow(new Date(notification.createdAt), { addSuffix: true })
                      : 'Just now'}
                  </Typography>
                </Box>
              </Box>
            </motion.div>
          ))
        )}
      </AnimatePresence>
    </Box>
  )
}
