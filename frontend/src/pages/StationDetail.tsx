import {
  ArrowBack as ArrowBackIcon,
  LocationOn as LocationIcon,
  Memory as MemoryIcon,
  Power as PowerIcon,
  Speed as SpeedIcon,
} from '@mui/icons-material'
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Grid,
  Paper,
  Typography,
} from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import LoadingSpinner from '../components/LoadingSpinner'
import MetricsChart from '../components/MetricsChart'
import { metricsApi, notificationsApi, stationApi } from '../services/api'
import { MetricData, Notification } from '../types'
import { getStatusColor } from '../utils/statusHelpers'

export default function StationDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const { data: station, isLoading } = useQuery({
    queryKey: ['station', id],
    queryFn: async () => {
      const response = await stationApi.getById(Number(id))
      return response.data
    },
    enabled: !!id,
  })

  const { data: metrics } = useQuery({
    queryKey: ['station-metrics', id],
    queryFn: async () => {
      const response = await metricsApi.getByStation(Number(id))
      return response.data
    },
    enabled: !!id,
  })

  const { data: notifications } = useQuery({
    queryKey: ['station-notifications', id],
    queryFn: async () => {
      const response = await notificationsApi.getByStation(Number(id))
      return response.data
    },
    enabled: !!id,
  })

  if (isLoading) {
    return <LoadingSpinner />
  }

  if (!station) {
    return (
      <Box>
        <Typography variant="h6" color="error">
          Station not found
        </Typography>
      </Box>
    )
  }

  const stationData = station
  const stationMetrics = Array.isArray(metrics) ? metrics : []
  const stationNotifications = Array.isArray(notifications) ? notifications : []

  const latestMetrics = stationMetrics.slice(-5).reverse()

  return (
    <Box>
      <Box display="flex" alignItems="center" gap={2} mb={3}>
        <Button
          startIcon={<ArrowBackIcon />}
          onClick={() => navigate('/stations')}
        >
          Back
        </Button>
        <Typography variant="h4" sx={{ fontWeight: 600 }}>
          {stationData.stationName}
        </Typography>
        <Chip
          label={stationData.status}
          color={getStatusColor(stationData.status)}
        />
      </Box>

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Station Information
              </Typography>
              <Divider sx={{ my: 2 }} />
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Typography variant="body2" color="textSecondary">
                    Station ID
                  </Typography>
                  <Typography variant="body1" fontWeight={600}>
                    {stationData.id}
                  </Typography>
                </Grid>
                <Grid item xs={6}>
                  <Typography variant="body2" color="textSecondary">
                    Station Type
                  </Typography>
                  <Typography variant="body1" fontWeight={600}>
                    {stationData.stationType}
                  </Typography>
                </Grid>
                <Grid item xs={12}>
                  <Box display="flex" alignItems="center" gap={1} mb={1}>
                    <LocationIcon color="action" />
                    <Typography variant="body2" color="textSecondary">
                      Location
                    </Typography>
                  </Box>
                  <Typography variant="body1" fontWeight={600}>
                    {stationData.location}
                  </Typography>
                  <Typography variant="body2" color="textSecondary">
                    {stationData.latitude}, {stationData.longitude}
                  </Typography>
                </Grid>
                {stationData.description && (
                  <Grid item xs={12}>
                    <Typography variant="body2" color="textSecondary">
                      Description
                    </Typography>
                    <Typography variant="body1">
                      {stationData.description}
                    </Typography>
                  </Grid>
                )}
              </Grid>
            </CardContent>
          </Card>

          <Card sx={{ mt: 3 }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Metrics History
              </Typography>
              <MetricsChart />
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Current Metrics
              </Typography>
              <Divider sx={{ my: 2 }} />
              {latestMetrics.length > 0 ? (
                latestMetrics.map((metric: MetricData) => (
                  <Paper
                    key={metric.id || `${metric.metricType}-${metric.timestamp}`}
                    sx={{
                      p: 2,
                      mb: 2,
                      backgroundColor: (theme) => 
                        theme.palette.mode === 'dark' 
                          ? 'rgba(255, 255, 255, 0.05)' 
                          : 'grey.50',
                    }}
                  >
                    <Box display="flex" justifyContent="space-between" alignItems="center">
                      <Box>
                        <Typography variant="body2" color="textSecondary">
                          {metric.metricType}
                        </Typography>
                        <Typography variant="h6">
                          {metric.value} {metric.unit}
                        </Typography>
                      </Box>
                      {metric.metricType === 'POWER_CONSUMPTION' && (
                        <PowerIcon color="primary" />
                      )}
                      {metric.metricType === 'MEMORY_USAGE' && (
                        <MemoryIcon color="primary" />
                      )}
                      {metric.metricType === 'CPU_USAGE' && (
                        <SpeedIcon color="primary" />
                      )}
                    </Box>
                    <Typography variant="caption" color="textSecondary">
                      {metric.timestamp ? new Date(metric.timestamp).toLocaleString() : 'No date available'}
                    </Typography>
                  </Paper>
                ))
              ) : (
                <Typography variant="body2" color="textSecondary">
                  No metrics available
                </Typography>
              )}
            </CardContent>
          </Card>

          <Card sx={{ mt: 3 }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Recent Alerts
              </Typography>
              <Divider sx={{ my: 2 }} />
              {stationNotifications.length > 0 ? (
                stationNotifications.slice(0, 5).map((alert: Notification) => (
                  <Box
                    key={alert.id}
                    sx={{
                      p: 2,
                      mb: 1,
                      borderRadius: 1,
                      backgroundColor: alert.status === 'UNREAD' ? 'warning.light' : 'grey.50',
                      borderLeft: `3px solid ${alert.type === 'ALERT' ? 'error.main' : 'warning.main'
                        }`,
                    }}
                  >
                    <Typography variant="body2" fontWeight={600}>
                      {alert.type}
                    </Typography>
                    <Typography variant="body2" color="textSecondary">
                      {alert.message}
                    </Typography>
                    <Typography variant="caption" color="textSecondary">
                      {alert.createdAt ? new Date(alert.createdAt).toLocaleString() : 'No date available'}
                    </Typography>
                  </Box>
                ))
              ) : (
                <Typography variant="body2" color="textSecondary">
                  No alerts
                </Typography>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  )
}

