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
import ErrorDisplay from '../components/ErrorDisplay'
import LoadingSpinner from '../components/LoadingSpinner'
import MetricsChart from '../components/MetricsChart'
import {
  STATION_STATUS_STYLES,
  ALERT_SEVERITY_STYLES,
  GRID_CONTENT_SIDEBAR_SX,
  GRID_1_2_COL_SX,
  type StationStatusType,
  type AlertSeverity,
} from '../constants/designSystem'
import { metricsApi, notificationsApi, stationApi } from '../services/api'
import { formatTimestamp } from '../utils/statusHelpers'
import { MetricData, Notification, NotificationType, StationStatus } from '../types'
import { ensureArray } from '../utils/arrayUtils'

interface MetricRowProps {
  metric: MetricData
  delay: number
}

const MetricRow = ({ metric, delay }: Readonly<MetricRowProps>) => {
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
          background: 'var(--surface-hover)',
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
            {formatTimestamp(metric.timestamp)}
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

const AlertRow = ({ alert, delay }: Readonly<AlertRowProps>) => {
  const severityStyle = ALERT_SEVERITY_STYLES[alert.type as AlertSeverity] || ALERT_SEVERITY_STYLES.INFO
  const severityColor = severityStyle.colorVar

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
            {formatTimestamp(alert.createdAt)}
          </Typography>
        </Box>
      </Box>
    </Box>
  )
}

export default function StationDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const { data: station, isLoading, error: stationError } = useQuery({
    queryKey: ['station', id],
    queryFn: async () => {
      const response = await stationApi.getById(Number(id))
      return response.data
    },
    enabled: !!id,
  })

  const { data: metrics, error: metricsError } = useQuery({
    queryKey: ['station-metrics', id],
    queryFn: async () => {
      const response = await metricsApi.getByStation(Number(id))
      return response.data
    },
    enabled: !!id,
  })

  const { data: notifications, error: notificationsError } = useQuery({
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

  const error = stationError || metricsError || notificationsError
  if (error) {
    return <ErrorDisplay title="Failed to load station data" message={error.message} />
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

  const stationMetrics = ensureArray(metrics as MetricData[])
  const stationNotifications = ensureArray(notifications as Notification[])

  const latestMetrics = stationMetrics.slice(-5).reverse()

  const statusStyle = STATION_STATUS_STYLES[station.status as StationStatusType] || STATION_STATUS_STYLES.OFFLINE
  const statusColor = statusStyle.colorVar

  return (
    <Box sx={{ maxWidth: '1400px', margin: '0 auto', padding: { xs: '16px 12px', sm: '24px 16px', md: '32px 24px' } }}>
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
                background: 'var(--surface-hover)',
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
              fontSize: { xs: '1.5rem', sm: '1.75rem', md: '2.25rem' },
              fontWeight: 700,
              letterSpacing: '-0.025em',
              color: 'var(--mono-950)',
            }}
          >
            {station.stationName}
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
              {station.status === StationStatus.ACTIVE && (
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
              {station.status}
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
          Station ID: #{station.id} · {station.stationType}
        </Typography>
      </Box>

      {/* Main Content Grid */}
      <Box sx={GRID_CONTENT_SIDEBAR_SX}>
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
                borderColor: 'var(--mono-400)',
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

            <Box sx={GRID_1_2_COL_SX}>
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
                  #{station.id}
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
                  {station.stationType}
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
                  {station.location}
                </Typography>
                <Typography
                  sx={{
                    fontSize: '0.8125rem',
                    fontFamily: "'JetBrains Mono', monospace",
                    color: 'var(--mono-600)',
                  }}
                >
                  {station.latitude}, {station.longitude}
                </Typography>
              </Box>

              {station.description && (
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
                    {station.description}
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
                borderColor: 'var(--mono-400)',
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
                borderColor: 'var(--mono-400)',
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
                borderColor: 'var(--mono-400)',
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
