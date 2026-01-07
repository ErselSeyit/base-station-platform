import {
  ArrowBack as ArrowBackIcon,
  Error as ErrorIcon,
  Info as InfoIcon,
  LocationOn as LocationIcon,
  Memory as MemoryIcon,
  Power as PowerIcon,
  Speed as SpeedIcon,
  Warning as WarningIcon,
} from '@mui/icons-material'
import {
  Box,
  Button,
  Typography,
} from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { motion } from 'framer-motion'
import { useNavigate, useParams } from 'react-router-dom'
import LoadingSpinner from '../components/LoadingSpinner'
import MetricsChart from '../components/MetricsChart'
import { metricsApi, notificationsApi, stationApi } from '../services/api'
import { MetricData, Notification, NotificationType, StationStatus } from '../types'

const STATUS_COLORS = {
  ACTIVE: 'var(--status-active)',
  MAINTENANCE: 'var(--status-maintenance)',
  OFFLINE: 'var(--status-offline)',
}

const SEVERITY_COLORS = {
  ALERT: 'var(--status-offline)',
  WARNING: 'var(--status-maintenance)',
  INFO: 'var(--status-info)',
}

interface MetricRowProps {
  metric: MetricData
  delay: number
}

const MetricRow = ({ metric, delay }: MetricRowProps) => {
  const getIcon = (type: string) => {
    switch (type) {
      case 'POWER_CONSUMPTION':
        return <PowerIcon sx={{ fontSize: '16px' }} />
      case 'MEMORY_USAGE':
        return <MemoryIcon sx={{ fontSize: '16px' }} />
      case 'CPU_USAGE':
        return <SpeedIcon sx={{ fontSize: '16px' }} />
      default:
        return <SpeedIcon sx={{ fontSize: '16px' }} />
    }
  }

  const getMetricColor = (type: string) => {
    switch (type) {
      case 'POWER_CONSUMPTION':
        return 'var(--status-info)'
      case 'MEMORY_USAGE':
        return 'var(--status-maintenance)'
      case 'CPU_USAGE':
        return 'var(--status-active)'
      case 'TEMPERATURE':
        return 'var(--status-offline)'
      default:
        return 'var(--status-info)'
    }
  }

  return (
    <Box
      component={motion.div}
      initial={{ opacity: 0, x: -16 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ delay, duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '16px 20px',
        borderBottom: '1px solid var(--surface-border)',
        transition: 'background 0.15s cubic-bezier(0.16, 1, 0.3, 1)',
        '&:hover': {
          background: 'var(--mono-50)',
        },
        '&:last-child': {
          borderBottom: 'none',
        },
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px', flex: 1 }}>
        <Box sx={{ color: getMetricColor(metric.metricType) }}>
          {getIcon(metric.metricType)}
        </Box>
        <Box>
          <Typography
            sx={{
              fontSize: '0.875rem',
              fontWeight: 600,
              color: 'var(--mono-950)',
            }}
          >
            {metric.metricType.replace('_', ' ')}
          </Typography>
          <Typography
            sx={{
              fontSize: '0.75rem',
              fontFamily: "'JetBrains Mono', monospace",
              color: 'var(--mono-500)',
            }}
          >
            {metric.timestamp ? new Date(metric.timestamp).toLocaleString() : 'No date'}
          </Typography>
        </Box>
      </Box>
      <Typography
        sx={{
          fontSize: '1.25rem',
          fontWeight: 600,
          fontVariantNumeric: 'tabular-nums',
          fontFamily: "'JetBrains Mono', monospace",
          color: 'var(--mono-950)',
        }}
      >
        {metric.value} {metric.unit}
      </Typography>
    </Box>
  )
}

interface AlertRowProps {
  alert: Notification
  delay: number
}

const AlertRow = ({ alert, delay }: AlertRowProps) => {
  const severityColor = SEVERITY_COLORS[alert.type as keyof typeof SEVERITY_COLORS] || SEVERITY_COLORS.INFO

  const getIcon = (type: NotificationType) => {
    switch (type) {
      case NotificationType.ALERT:
        return <ErrorIcon sx={{ fontSize: '14px' }} />
      case NotificationType.WARNING:
        return <WarningIcon sx={{ fontSize: '14px' }} />
      case NotificationType.INFO:
        return <InfoIcon sx={{ fontSize: '14px' }} />
      default:
        return <InfoIcon sx={{ fontSize: '14px' }} />
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
        padding: '16px 20px',
        borderBottom: '1px solid var(--surface-border)',
        transition: 'background 0.15s cubic-bezier(0.16, 1, 0.3, 1)',
        '&:hover': {
          background: 'var(--mono-50)',
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
          width: '2px',
          background: severityColor,
        },
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: '12px' }}>
        <Box sx={{ color: severityColor, marginTop: '2px' }}>
          {getIcon(alert.type)}
        </Box>
        <Box sx={{ flex: 1 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
            <Typography
              sx={{
                fontSize: '0.75rem',
                fontWeight: 500,
                color: 'var(--mono-500)',
                textTransform: 'uppercase',
                letterSpacing: '0.05em',
              }}
            >
              {alert.type}
            </Typography>
          </Box>
          <Typography
            sx={{
              fontSize: '0.8125rem',
              color: 'var(--mono-700)',
              lineHeight: 1.5,
              marginBottom: '4px',
            }}
          >
            {alert.message}
          </Typography>
          <Typography
            sx={{
              fontSize: '0.7rem',
              fontFamily: "'JetBrains Mono', monospace",
              color: 'var(--mono-500)',
            }}
          >
            {alert.createdAt ? new Date(alert.createdAt).toLocaleString() : 'No date'}
          </Typography>
        </Box>
      </Box>
    </Box>
  )
}

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
      <Box sx={{ maxWidth: '1400px', margin: '0 auto', padding: '32px 24px' }}>
        <Typography variant="h6" sx={{ color: 'var(--accent-error)' }}>
          Station not found
        </Typography>
      </Box>
    )
  }

  const stationData = station
  const stationMetrics = Array.isArray(metrics) ? metrics : []
  const stationNotifications = Array.isArray(notifications) ? notifications : []

  const latestMetrics = stationMetrics.slice(-5).reverse()

  const statusColor = STATUS_COLORS[stationData.status as keyof typeof STATUS_COLORS] || STATUS_COLORS.OFFLINE

  return (
    <Box sx={{ maxWidth: '1400px', margin: '0 auto', padding: '32px 24px' }}>
      {/* Header with Back Button */}
      <Box
        component={motion.div}
        initial={{ opacity: 0, y: -16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
        sx={{ marginBottom: '32px' }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '16px' }}>
          <Button
            startIcon={<ArrowBackIcon />}
            onClick={() => navigate('/stations')}
            sx={{
              color: 'var(--mono-600)',
              fontSize: '0.875rem',
              fontWeight: 500,
              textTransform: 'none',
              padding: '6px 12px',
              borderRadius: '8px',
              transition: 'all 0.15s cubic-bezier(0.16, 1, 0.3, 1)',
              '&:hover': {
                background: 'var(--mono-100)',
                color: 'var(--mono-950)',
              },
            }}
          >
            Back to Stations
          </Button>
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <Typography
            variant="h1"
            sx={{
              fontSize: '2.25rem',
              fontWeight: 700,
              letterSpacing: '-0.025em',
              color: 'var(--mono-950)',
            }}
          >
            {stationData.stationName}
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <Box
              sx={{
                width: '8px',
                height: '8px',
                borderRadius: '50%',
                background: statusColor,
                position: 'relative',
              }}
            >
              {stationData.status === StationStatus.ACTIVE && (
                <Box
                  sx={{
                    position: 'absolute',
                    inset: '-2px',
                    borderRadius: '50%',
                    background: statusColor,
                    opacity: 0.2,
                    animation: 'pulse 2s ease-in-out infinite',
                  }}
                />
              )}
            </Box>
            <Typography
              sx={{
                fontSize: '0.875rem',
                fontWeight: 500,
                color: 'var(--mono-700)',
              }}
            >
              {stationData.status}
            </Typography>
          </Box>
        </Box>

        <Typography
          sx={{
            fontSize: '0.875rem',
            color: 'var(--mono-500)',
            letterSpacing: '0.01em',
            marginTop: '8px',
          }}
        >
          Station ID: #{stationData.id} · {stationData.stationType}
        </Typography>
      </Box>

      {/* Main Content Grid */}
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', lg: '2fr 1fr' }, gap: '16px' }}>
        {/* Left Column */}
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {/* Station Information */}
          <Box
            component={motion.div}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
            sx={{
              background: 'var(--surface-base)',
              border: '1px solid var(--surface-border)',
              borderRadius: '12px',
              padding: '24px',
              transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
              '&:hover': {
                boxShadow: 'var(--shadow-md)',
                borderColor: 'var(--mono-300)',
              },
            }}
          >
            <Typography
              sx={{
                fontSize: '0.875rem',
                fontWeight: 600,
                color: 'var(--mono-950)',
                marginBottom: '20px',
                letterSpacing: '-0.01em',
              }}
            >
              Station Information
            </Typography>

            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: '20px' }}>
              <Box>
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
                  Station ID
                </Typography>
                <Typography
                  sx={{
                    fontSize: '1rem',
                    fontWeight: 600,
                    fontFamily: "'JetBrains Mono', monospace",
                    color: 'var(--mono-950)',
                  }}
                >
                  #{stationData.id}
                </Typography>
              </Box>

              <Box>
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
                  Station Type
                </Typography>
                <Typography
                  sx={{
                    fontSize: '1rem',
                    fontWeight: 600,
                    color: 'var(--mono-950)',
                  }}
                >
                  {stationData.stationType}
                </Typography>
              </Box>

              <Box sx={{ gridColumn: { xs: '1', sm: '1 / -1' } }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
                  <LocationIcon sx={{ fontSize: '14px', color: 'var(--mono-500)' }} />
                  <Typography
                    sx={{
                      fontSize: '0.75rem',
                      fontWeight: 500,
                      color: 'var(--mono-500)',
                      textTransform: 'uppercase',
                      letterSpacing: '0.05em',
                    }}
                  >
                    Location
                  </Typography>
                </Box>
                <Typography
                  sx={{
                    fontSize: '1rem',
                    fontWeight: 600,
                    color: 'var(--mono-950)',
                    marginBottom: '4px',
                  }}
                >
                  {stationData.location}
                </Typography>
                <Typography
                  sx={{
                    fontSize: '0.8125rem',
                    fontFamily: "'JetBrains Mono', monospace",
                    color: 'var(--mono-600)',
                  }}
                >
                  {stationData.latitude}, {stationData.longitude}
                </Typography>
              </Box>

              {stationData.description && (
                <Box sx={{ gridColumn: { xs: '1', sm: '1 / -1' } }}>
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
                    Description
                  </Typography>
                  <Typography
                    sx={{
                      fontSize: '0.875rem',
                      color: 'var(--mono-700)',
                      lineHeight: 1.6,
                    }}
                  >
                    {stationData.description}
                  </Typography>
                </Box>
              )}
            </Box>
          </Box>

          {/* Metrics Chart */}
          <Box
            component={motion.div}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.15, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
            sx={{
              background: 'var(--surface-base)',
              border: '1px solid var(--surface-border)',
              borderRadius: '12px',
              padding: '24px',
              transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
              '&:hover': {
                boxShadow: 'var(--shadow-md)',
                borderColor: 'var(--mono-300)',
              },
            }}
          >
            <Typography
              sx={{
                fontSize: '0.875rem',
                fontWeight: 600,
                color: 'var(--mono-950)',
                marginBottom: '20px',
                letterSpacing: '-0.01em',
              }}
            >
              Performance Metrics
            </Typography>
            <MetricsChart />
          </Box>
        </Box>

        {/* Right Column */}
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {/* Current Metrics */}
          <Box
            component={motion.div}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
            sx={{
              background: 'var(--surface-base)',
              border: '1px solid var(--surface-border)',
              borderRadius: '12px',
              overflow: 'hidden',
              transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
              '&:hover': {
                boxShadow: 'var(--shadow-md)',
                borderColor: 'var(--mono-300)',
              },
            }}
          >
            <Box sx={{ padding: '24px 24px 16px' }}>
              <Typography
                sx={{
                  fontSize: '0.875rem',
                  fontWeight: 600,
                  color: 'var(--mono-950)',
                  letterSpacing: '-0.01em',
                }}
              >
                Current Metrics
              </Typography>
              <Typography
                sx={{
                  fontSize: '0.75rem',
                  color: 'var(--mono-500)',
                  marginTop: '4px',
                }}
              >
                Latest readings
              </Typography>
            </Box>
            <Box>
              {latestMetrics.length > 0 ? (
                latestMetrics.map((metric: MetricData, idx: number) => (
                  <MetricRow
                    key={metric.id || `${metric.metricType}-${metric.timestamp}`}
                    metric={metric}
                    delay={0.25 + idx * 0.03}
                  />
                ))
              ) : (
                <Box sx={{ padding: '24px', textAlign: 'center' }}>
                  <Typography
                    sx={{
                      fontSize: '0.875rem',
                      color: 'var(--mono-500)',
                    }}
                  >
                    No metrics available
                  </Typography>
                </Box>
              )}
            </Box>
          </Box>

          {/* Recent Alerts */}
          <Box
            component={motion.div}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.25, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
            sx={{
              background: 'var(--surface-base)',
              border: '1px solid var(--surface-border)',
              borderRadius: '12px',
              overflow: 'hidden',
              transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
              '&:hover': {
                boxShadow: 'var(--shadow-md)',
                borderColor: 'var(--mono-300)',
              },
            }}
          >
            <Box sx={{ padding: '24px 24px 16px' }}>
              <Typography
                sx={{
                  fontSize: '0.875rem',
                  fontWeight: 600,
                  color: 'var(--mono-950)',
                  letterSpacing: '-0.01em',
                }}
              >
                Recent Alerts
              </Typography>
              <Typography
                sx={{
                  fontSize: '0.75rem',
                  color: 'var(--mono-500)',
                  marginTop: '4px',
                }}
              >
                Last 5 notifications
              </Typography>
            </Box>
            <Box>
              {stationNotifications.length > 0 ? (
                stationNotifications.slice(0, 5).map((alert: Notification, idx: number) => (
                  <AlertRow
                    key={alert.id}
                    alert={alert}
                    delay={0.3 + idx * 0.03}
                  />
                ))
              ) : (
                <Box sx={{ padding: '24px', textAlign: 'center' }}>
                  <Typography
                    sx={{
                      fontSize: '0.875rem',
                      color: 'var(--mono-500)',
                    }}
                  >
                    No alerts
                  </Typography>
                </Box>
              )}
            </Box>
          </Box>
        </Box>
      </Box>
    </Box>
  )
}
