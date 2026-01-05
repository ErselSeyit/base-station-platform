import { Circle } from '@mui/icons-material'
import { Box, Typography } from '@mui/material'
import { motion, AnimatePresence } from 'framer-motion'
import { formatDistanceToNow } from 'date-fns'
import { useQuery } from '@tanstack/react-query'
import { notificationsApi } from '../services/api'
import { Notification } from '../types'
import { useTheme } from '../contexts/ThemeContext'
import PulsingStatus from './PulsingStatus'

function getSeverityColor(severity?: string): string {
  if (!severity) return '#64748b'
  switch (severity.toUpperCase()) {
    case 'CRITICAL':
      return '#ef4444'
    case 'WARNING':
      return '#f59e0b'
    case 'INFO':
      return '#3b82f6'
    default:
      return '#64748b'
  }
}

export default function LiveActivityFeed() {
  const { mode } = useTheme()
  const { data: notifications } = useQuery({
    queryKey: ['recent-notifications'],
    queryFn: async () => {
      const response = await notificationsApi.getAll()
      return response.data
    },
    refetchInterval: 10000, // Refresh every 10 seconds
  })

  const recentNotifications = Array.isArray(notifications)
    ? notifications.slice(0, 5)
    : []

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
        }}
      >
        <Circle sx={{ fontSize: 8, color: '#10b981', animation: 'pulse 2s infinite' }} />
        Live Activity Feed
      </Typography>

      <AnimatePresence mode="popLayout">
        {recentNotifications.length === 0 ? (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          >
            <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 4 }}>
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
                  background: mode === 'dark'
                    ? 'rgba(255, 255, 255, 0.03)'
                    : 'rgba(0, 0, 0, 0.02)',
                  borderLeft: '3px solid',
                  borderColor: getSeverityColor(notification.severity),
                  cursor: 'pointer',
                  transition: 'all 0.2s ease',
                  '&:hover': {
                    background: mode === 'dark'
                      ? 'rgba(255, 255, 255, 0.06)'
                      : 'rgba(0, 0, 0, 0.04)',
                    transform: 'translateX(4px)',
                  },
                }}
              >
                <Box sx={{ mt: 0.5 }}>
                  <PulsingStatus
                    color={getSeverityColor(notification.severity)}
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
                    }}
                  >
                    {notification.message}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
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
