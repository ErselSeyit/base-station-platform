import {
  CellTower as CellTowerIcon,
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon,
  TrendingUp as TrendingUpIcon,
  Warning as WarningIcon,
} from '@mui/icons-material'
import {
  Alert,
  Box,
  Card,
  CardContent,
  CircularProgress,
  Grid,
  Typography,
} from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import MetricsChart from '../components/MetricsChart'
import { useTheme } from '../contexts/ThemeContext'
import { metricsApi, notificationsApi, stationApi } from '../services/api'
import { BaseStation, Notification, StationStatus } from '../types'

export default function Dashboard() {
  const { mode } = useTheme()
  const { data: stations, isLoading: stationsLoading } = useQuery({
    queryKey: ['stations'],
    queryFn: async () => {
      const response = await stationApi.getAll()
      return response.data
    },
  })
  // Metrics are used in MetricsChart component
  useQuery({
    queryKey: ['recent-metrics'],
    queryFn: () => metricsApi.getAll({ startTime: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString() }),
  })
  const { data: notifications } = useQuery({
    queryKey: ['recent-notifications'],
    queryFn: async () => {
      const response = await notificationsApi.getAll()
      return response.data
    },
  })

  if (stationsLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    )
  }

  const stationsData = Array.isArray(stations) ? stations : []
  const activeCount = stationsData.filter((s: BaseStation) => s.status === StationStatus.ACTIVE).length
  const maintenanceCount = stationsData.filter((s: BaseStation) => s.status === StationStatus.MAINTENANCE).length
  const offlineCount = stationsData.filter((s: BaseStation) => s.status === StationStatus.OFFLINE).length
  const totalPower = stationsData.reduce((sum: number, s: BaseStation) => sum + (s.powerConsumption || 0), 0)
  const notificationsList = Array.isArray(notifications) ? notifications : []
  const unreadAlerts = notificationsList.filter((n: Notification) => n.status === 'UNREAD').length || 0

  const statCards = [
    {
      title: 'Total Stations',
      value: stationsData.length,
      icon: <CellTowerIcon sx={{ fontSize: 40 }} />,
      color: '#1976d2',
    },
    {
      title: 'Active Stations',
      value: activeCount,
      icon: <CheckCircleIcon sx={{ fontSize: 40 }} />,
      color: '#2e7d32',
    },
    {
      title: 'Maintenance',
      value: maintenanceCount,
      icon: <WarningIcon sx={{ fontSize: 40 }} />,
      color: '#ed6c02',
    },
    {
      title: 'Offline',
      value: offlineCount,
      icon: <ErrorIcon sx={{ fontSize: 40 }} />,
      color: '#d32f2f',
    },
    {
      title: 'Total Power (kW)',
      value: totalPower.toFixed(1),
      icon: <TrendingUpIcon sx={{ fontSize: 40 }} />,
      color: '#9c27b0',
    },
    {
      title: 'Unread Alerts',
      value: unreadAlerts,
      icon: <WarningIcon sx={{ fontSize: 40 }} />,
      color: '#d32f2f',
    },
  ]

  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography
          variant="h3"
          gutterBottom
          sx={{
            fontWeight: 700,
            mb: 1,
            background: mode === 'dark'
              ? 'linear-gradient(135deg, #64b5f6 0%, #90caf9 50%, #ba68c8 100%)'
              : 'linear-gradient(135deg, #1976d2 0%, #42a5f5 50%, #9c27b0 100%)',
            backgroundClip: 'text',
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent',
          }}
        >
          Dashboard
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Overview of your base station operations and performance metrics
        </Typography>
      </Box>

      {unreadAlerts > 0 && (
        <Alert
          severity="warning"
          sx={{
            mb: 3,
            borderRadius: 2,
            boxShadow: mode === 'dark'
              ? '0 4px 12px rgba(255, 167, 38, 0.2)'
              : '0 4px 12px rgba(237, 108, 2, 0.15)',
          }}
        >
          You have {unreadAlerts} unread alert{unreadAlerts > 1 ? 's' : ''}
        </Alert>
      )}

      <Grid container spacing={3}>
        {statCards.map((card, index) => (
          <Grid item xs={12} sm={6} md={4} key={index}>
            <Card
              sx={{
                height: '100%',
                position: 'relative',
                overflow: 'hidden',
                background: mode === 'dark'
                  ? `linear-gradient(135deg, ${card.color}20 0%, ${card.color}10 100%)`
                  : `linear-gradient(135deg, ${card.color}15 0%, ${card.color}05 100%)`,
                borderLeft: `4px solid ${card.color}`,
                '&::before': {
                  content: '""',
                  position: 'absolute',
                  top: 0,
                  right: 0,
                  width: '100px',
                  height: '100px',
                  background: `radial-gradient(circle, ${card.color}20 0%, transparent 70%)`,
                  opacity: 0.5,
                },
              }}
            >
              <CardContent sx={{ position: 'relative', zIndex: 1 }}>
                <Box display="flex" justifyContent="space-between" alignItems="center">
                  <Box>
                    <Typography
                      color="text.secondary"
                      gutterBottom
                      variant="body2"
                      sx={{ fontWeight: 500, textTransform: 'uppercase', letterSpacing: '0.05em', fontSize: '0.75rem' }}
                    >
                      {card.title}
                    </Typography>
                    <Typography
                      variant="h3"
                      sx={{
                        fontWeight: 700,
                        background: `linear-gradient(135deg, ${card.color} 0%, ${card.color}dd 100%)`,
                        backgroundClip: 'text',
                        WebkitBackgroundClip: 'text',
                        WebkitTextFillColor: 'transparent',
                      }}
                    >
                      {card.value}
                    </Typography>
                  </Box>
                  <Box
                    sx={{
                      color: card.color,
                      opacity: 0.9,
                      p: 1.5,
                      borderRadius: 2,
                      background: mode === 'dark'
                        ? `rgba(${card.color === '#1976d2' ? '100, 181, 246' : card.color === '#2e7d32' ? '102, 187, 106' : card.color === '#ed6c02' ? '255, 167, 38' : card.color === '#d32f2f' ? '239, 83, 80' : '186, 104, 200'}, 0.1)`
                        : `${card.color}15`,
                    }}
                  >
                    {card.icon}
                  </Box>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}

        <Grid item xs={12} md={8}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom sx={{ fontWeight: 600, mb: 3 }}>
                Recent Metrics
              </Typography>
              <MetricsChart />
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom sx={{ fontWeight: 600, mb: 3 }}>
                Recent Alerts
              </Typography>
              {notificationsList.slice(0, 5).map((alert: Notification) => (
                <Box
                  key={alert.id}
                  sx={{
                    p: 2,
                    mb: 2,
                    borderRadius: 2,
                    background: mode === 'dark'
                      ? alert.status === 'UNREAD'
                        ? 'linear-gradient(135deg, rgba(255, 167, 38, 0.15) 0%, rgba(255, 167, 38, 0.05) 100%)'
                        : 'rgba(255, 255, 255, 0.03)'
                      : alert.status === 'UNREAD'
                        ? 'linear-gradient(135deg, rgba(255, 152, 0, 0.1) 0%, rgba(255, 152, 0, 0.05) 100%)'
                        : 'rgba(0, 0, 0, 0.02)',
                    borderLeft: `3px solid ${alert.type === 'ALERT' ? 'error.main' : 'warning.main'
                      }`,
                    transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                    '&:hover': {
                      transform: 'translateX(4px)',
                      boxShadow: mode === 'dark'
                        ? '0 4px 12px rgba(0, 0, 0, 0.3)'
                        : '0 4px 12px rgba(0, 0, 0, 0.1)',
                    },
                  }}
                >
                  <Typography variant="body2" fontWeight={600} sx={{ mb: 0.5 }}>
                    {alert.stationName || `Station ${alert.stationId}`}
                  </Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                    {alert.message}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {alert.createdAt ? new Date(alert.createdAt).toLocaleString() : 'N/A'}
                  </Typography>
                </Box>
              ))}
              {notificationsList.length === 0 && (
                <Typography variant="body2" color="text.secondary" align="center" sx={{ py: 4 }}>
                  No recent alerts
                </Typography>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  )
}

