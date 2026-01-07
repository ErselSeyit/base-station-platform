import {
  TrendingUp as TrendingUpIcon,
} from '@mui/icons-material'
import {
  Alert,
  Box,
  Grid,
  Typography,
} from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { motion, useMotionValue, useSpring, useTransform } from 'framer-motion'
import { useState } from 'react'
import AnimatedCounter from '../components/AnimatedCounter'
import LiveActivityFeed from '../components/LiveActivityFeed'
import LoadingSpinner from '../components/LoadingSpinner'
import MetricsChart from '../components/MetricsChart'
import { notificationsApi, stationApi } from '../services/api'
import { BaseStation, Notification, StationStatus } from '../types'

// Status colors now use CSS variables
const STATUS_COLORS = {
  active: 'var(--status-active)',
  maintenance: 'var(--status-maintenance)',
  offline: 'var(--status-offline)',
  neutral: 'var(--status-info)',
}

// Revolutionary micro-metric component with magnetic hover
interface MicroMetricProps {
  label: string
  value: number | string
  status?: 'active' | 'maintenance' | 'offline' | 'neutral'
  unit?: string
  trend?: number
  delay?: number
}

const MicroMetric = ({ label, value, status = 'neutral', unit = '', trend, delay = 0 }: MicroMetricProps) => {
  const [isHovered, setIsHovered] = useState(false)
  const mouseX = useMotionValue(0)
  const mouseY = useMotionValue(0)

  const springConfig = { damping: 25, stiffness: 400 }
  const rotateX = useSpring(useTransform(mouseY, [-0.5, 0.5], [5, -5]), springConfig)
  const rotateY = useSpring(useTransform(mouseX, [-0.5, 0.5], [-5, 5]), springConfig)

  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect()
    const centerX = rect.left + rect.width / 2
    const centerY = rect.top + rect.height / 2
    mouseX.set((e.clientX - centerX) / rect.width)
    mouseY.set((e.clientY - centerY) / rect.height)
  }

  return (
    <Box
      component={motion.div}
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
      onMouseMove={handleMouseMove}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => {
        setIsHovered(false)
        mouseX.set(0)
        mouseY.set(0)
      }}
      sx={{
        position: 'relative',
        padding: '20px',
        background: 'var(--surface-base)',
        border: '1px solid var(--surface-border)',
        borderRadius: '12px',
        cursor: 'pointer',
        transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
        '&:hover': {
          transform: 'translateY(-2px)',
          boxShadow: 'var(--shadow-lg)',
          borderColor: 'var(--mono-300)',
        },
        '&::after': {
          content: '""',
          position: 'absolute',
          bottom: 0,
          left: 0,
          right: 0,
          height: '2px',
          background: STATUS_COLORS[status],
          transform: isHovered ? 'scaleX(1)' : 'scaleX(0)',
          transformOrigin: 'left',
          transition: 'transform 0.35s cubic-bezier(0.16, 1, 0.3, 1)',
        },
      }}
      style={{
        rotateX,
        rotateY,
        transformStyle: 'preserve-3d',
      }}
    >
      {/* Label */}
      <Typography
        variant="caption"
        sx={{
          display: 'block',
          fontSize: '0.75rem',
          fontWeight: 500,
          color: 'var(--mono-500)',
          textTransform: 'uppercase',
          letterSpacing: '0.05em',
          marginBottom: '8px',
        }}
      >
        {label}
      </Typography>

      {/* Value with tabular nums */}
      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: '4px', marginBottom: '4px' }}>
        <Typography
          sx={{
            fontSize: '2rem',
            fontWeight: 600,
            lineHeight: 1,
            letterSpacing: '-0.02em',
            fontVariantNumeric: 'tabular-nums',
            color: 'var(--mono-950)',
          }}
        >
          {typeof value === 'number' ? (
            <AnimatedCounter value={value} decimals={unit === 'kW' ? 1 : 0} duration={1200} />
          ) : (
            value
          )}
        </Typography>
        {unit && (
          <Typography
            sx={{
              fontSize: '0.875rem',
              fontWeight: 500,
              color: 'var(--mono-500)',
            }}
          >
            {unit}
          </Typography>
        )}
      </Box>

      {/* Trend indicator */}
      {trend !== undefined && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: '4px', marginTop: '8px' }}>
          <TrendingUpIcon
            sx={{
              fontSize: '14px',
              color: trend > 0 ? STATUS_COLORS.active : trend < 0 ? STATUS_COLORS.offline : STATUS_COLORS.neutral,
              transform: trend < 0 ? 'rotate(180deg)' : 'none',
            }}
          />
          <Typography
            sx={{
              fontSize: '0.75rem',
              fontWeight: 500,
              color: trend > 0 ? STATUS_COLORS.active : trend < 0 ? STATUS_COLORS.offline : STATUS_COLORS.neutral,
            }}
          >
            {trend > 0 ? '+' : ''}{trend}%
          </Typography>
        </Box>
      )}

      {/* Status indicator */}
      <Box
        sx={{
          position: 'absolute',
          top: '16px',
          right: '16px',
          width: '6px',
          height: '6px',
          borderRadius: '50%',
          background: STATUS_COLORS[status],
          boxShadow: `0 0 0 2px ${STATUS_COLORS[status]}20`,
        }}
      >
        {status === 'active' && (
          <Box
            sx={{
              position: 'absolute',
              inset: '-2px',
              borderRadius: '50%',
              background: STATUS_COLORS[status],
              opacity: 0.2,
              animation: 'pulse 2s ease-in-out infinite',
            }}
          />
        )}
      </Box>
    </Box>
  )
}

// Inline status row component
interface StatusRowProps {
  station: BaseStation
  delay: number
}

const StatusRow = ({ station, delay }: StatusRowProps) => {
  const statusColor =
    station.status === StationStatus.ACTIVE
      ? STATUS_COLORS.active
      : station.status === StationStatus.MAINTENANCE
      ? STATUS_COLORS.maintenance
      : STATUS_COLORS.offline

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
        padding: '12px 16px',
        borderBottom: '1px solid var(--surface-border)',
        transition: 'background 0.15s cubic-bezier(0.16, 1, 0.3, 1)',
        cursor: 'pointer',
        '&:hover': {
          background: 'var(--mono-50)',
        },
        '&:last-child': {
          borderBottom: 'none',
        },
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px', flex: 1 }}>
        <Box
          sx={{
            width: '6px',
            height: '6px',
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
            color: 'var(--mono-950)',
          }}
        >
          {station.stationName}
        </Typography>
      </Box>
      <Typography
        sx={{
          fontSize: '0.875rem',
          fontWeight: 600,
          fontVariantNumeric: 'tabular-nums',
          fontFamily: "'JetBrains Mono', monospace",
          color: 'var(--mono-600)',
        }}
      >
        {(station.powerConsumption || 0).toFixed(1)} kW
      </Typography>
    </Box>
  )
}

export default function Dashboard() {
  const { data: stations, isLoading: stationsLoading } = useQuery({
    queryKey: ['stations'],
    queryFn: async () => {
      const response = await stationApi.getAll()
      return response.data
    },
  })
  const { data: notifications } = useQuery({
    queryKey: ['recent-notifications'],
    queryFn: async () => {
      const response = await notificationsApi.getAll()
      return response.data
    },
  })

  if (stationsLoading) {
    return <LoadingSpinner />
  }

  const stationsData = Array.isArray(stations) ? stations : []
  const activeCount = stationsData.filter((s: BaseStation) => s.status === StationStatus.ACTIVE).length
  const maintenanceCount = stationsData.filter((s: BaseStation) => s.status === StationStatus.MAINTENANCE).length
  const offlineCount = stationsData.filter((s: BaseStation) => s.status === StationStatus.OFFLINE).length
  const totalPower = stationsData.reduce((sum: number, s: BaseStation) => sum + (s.powerConsumption || 0), 0)
  const notificationsList = Array.isArray(notifications) ? notifications : []
  const unreadAlerts = notificationsList.filter((n: Notification) => n.status === 'UNREAD').length || 0

  const uptime = stationsData.length > 0 ? ((activeCount / stationsData.length) * 100).toFixed(1) : '0'

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
          Operations
        </Typography>
        <Typography
          sx={{
            fontSize: '0.875rem',
            color: 'var(--mono-500)',
            letterSpacing: '0.01em',
          }}
        >
          Real-time infrastructure monitoring · {stationsData.length} stations · {uptime}% uptime
        </Typography>
      </Box>

      {/* Alert banner - minimal */}
      {unreadAlerts > 0 && (
        <Box
          component={motion.div}
          initial={{ opacity: 0, y: -8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3 }}
          sx={{ marginBottom: '24px' }}
        >
          <Alert
            severity="warning"
            sx={{
              background: 'var(--mono-50)',
              border: '1px solid var(--surface-border)',
              borderLeft: `3px solid ${STATUS_COLORS.maintenance}`,
              borderRadius: '8px',
              padding: '12px 16px',
              fontSize: '0.875rem',
              '& .MuiAlert-icon': {
                fontSize: '18px',
                color: STATUS_COLORS.maintenance,
              },
            }}
          >
            {unreadAlerts} unread alert{unreadAlerts > 1 ? 's' : ''} require attention
          </Alert>
        </Box>
      )}

      {/* Metrics grid - hyper-dense */}
      <Grid container spacing={2} sx={{ marginBottom: '24px' }}>
        <Grid item xs={12} sm={6} md={3}>
          <MicroMetric
            label="Total Stations"
            value={stationsData.length}
            status="neutral"
            delay={0}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MicroMetric
            label="Active"
            value={activeCount}
            status="active"
            trend={5}
            delay={0.05}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MicroMetric
            label="Maintenance"
            value={maintenanceCount}
            status="maintenance"
            delay={0.1}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MicroMetric
            label="Offline"
            value={offlineCount}
            status="offline"
            trend={offlineCount > 0 ? -2 : 0}
            delay={0.15}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4}>
          <MicroMetric
            label="Power Consumption"
            value={totalPower.toFixed(1)}
            unit="kW"
            status="neutral"
            delay={0.2}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4}>
          <MicroMetric
            label="System Uptime"
            value={uptime}
            unit="%"
            status="active"
            trend={1}
            delay={0.25}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={4}>
          <MicroMetric
            label="Alerts"
            value={unreadAlerts}
            status={unreadAlerts > 0 ? 'maintenance' : 'neutral'}
            delay={0.3}
          />
        </Grid>
      </Grid>

      {/* Main content grid */}
      <Grid container spacing={2}>
        {/* Metrics chart */}
        <Grid item xs={12} lg={8}>
          <Box
            component={motion.div}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.35, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
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
        </Grid>

        {/* Station health list */}
        <Grid item xs={12} lg={4}>
          <Box
            component={motion.div}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.4, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
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
                Power Consumption
              </Typography>
              <Typography
                sx={{
                  fontSize: '0.75rem',
                  color: 'var(--mono-500)',
                  marginTop: '4px',
                }}
              >
                Top consumers by usage
              </Typography>
            </Box>
            <Box>
              {[...stationsData]
                .sort((a: BaseStation, b: BaseStation) => (b.powerConsumption || 0) - (a.powerConsumption || 0))
                .slice(0, 6)
                .map((station: BaseStation, idx: number) => (
                  <StatusRow key={station.id} station={station} delay={0.45 + idx * 0.05} />
                ))}
            </Box>
          </Box>
        </Grid>

        {/* Activity feed */}
        <Grid item xs={12}>
          <Box
            component={motion.div}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.5, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
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
            <LiveActivityFeed />
          </Box>
        </Grid>
      </Grid>
    </Box>
  )
}
