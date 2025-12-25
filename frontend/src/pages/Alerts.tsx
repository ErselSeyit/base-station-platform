import {
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon,
  Info as InfoIcon,
  Warning as WarningIcon,
} from '@mui/icons-material'
import {
  Box,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  IconButton,
  Alert as MuiAlert,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notificationsApi } from '../services/api'
import { Notification, NotificationType } from '../types'

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
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    )
  }

  const notifications = Array.isArray(data) ? data : []
  const unreadCount = notifications.filter((n: Notification) => n.status === 'UNREAD').length

  const getIcon = (type: NotificationType) => {
    switch (type) {
      case NotificationType.ALERT:
        return <ErrorIcon color="error" />
      case NotificationType.WARNING:
        return <WarningIcon color="warning" />
      case NotificationType.INFO:
        return <InfoIcon color="info" />
      default:
        return <InfoIcon />
    }
  }

  const getSeverity = (type: NotificationType): 'error' | 'warning' | 'info' => {
    switch (type) {
      case NotificationType.ALERT:
        return 'error'
      case NotificationType.WARNING:
        return 'warning'
      default:
        return 'info'
    }
  }

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4" sx={{ fontWeight: 600 }}>
          Alerts & Notifications
        </Typography>
        {unreadCount > 0 && (
          <Chip
            label={`${unreadCount} Unread`}
            color="error"
            size="small"
          />
        )}
      </Box>

      {notifications.length === 0 ? (
        <Card>
          <CardContent>
            <Typography variant="body1" color="textSecondary" align="center">
              No alerts or notifications
            </Typography>
          </CardContent>
        </Card>
      ) : (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {notifications.map((notification: Notification) => (
            <MuiAlert
              key={notification.id}
              severity={getSeverity(notification.type)}
              icon={getIcon(notification.type)}
              action={
                notification.status === 'UNREAD' && notification.id !== undefined && (
                  <IconButton
                    size="small"
                    onClick={() => notification.id !== undefined && markAsReadMutation.mutate(notification.id)}
                  >
                    <CheckCircleIcon fontSize="small" />
                  </IconButton>
                )
              }
              sx={{
                '& .MuiAlert-message': {
                  width: '100%',
                },
              }}
            >
              <Box>
                <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                  <Typography variant="subtitle2" fontWeight={600}>
                    {notification.stationName || `Station ${notification.stationId}`}
                  </Typography>
                  {notification.status === 'UNREAD' && (
                    <Chip label="Unread" size="small" color="error" />
                  )}
                </Box>
                <Typography variant="body2">
                  {notification.message}
                </Typography>
                <Typography variant="caption" color="textSecondary" sx={{ mt: 1, display: 'block' }}>
                  {notification.createdAt ? new Date(notification.createdAt).toLocaleString() : 'N/A'}
                </Typography>
              </Box>
            </MuiAlert>
          ))}
        </Box>
      )}
    </Box>
  )
}

