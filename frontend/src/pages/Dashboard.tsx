import {
  TrendingUp as TrendingUpIcon,
  CheckCircle as HealthyIcon,
  Error as CriticalIcon,
  Warning as WarningIcon,
  CellTower as StationIcon,
  PowerOff as OfflineIcon,
  Build as MaintenanceIcon,
  Notifications as NotificationsIcon,
  Close as CloseIcon,
  Speed as PerformanceIcon,
  Router as NetworkIcon,
  FiveG as FiveGIcon,
} from '@mui/icons-material'
import {
  Badge,
  Box,
  Drawer,
  Fab,
  Grid,
  IconButton,
  LinearProgress,
  Typography,
} from '@mui/material'
import { motion } from 'framer-motion'
import { useState } from 'react'
import LiveActivityFeed from '../components/LiveActivityFeed'
import LoadingSpinner from '../components/LoadingSpinner'
import MetricsChart from '../components/MetricsChart'
import {
  useDashboardData,
  getInfraStatus,
  type HealthStatus,
  type ProcessedMetric,
} from '../hooks/useDashboardData'
import { BaseStation } from '../types'

// ============================================================================
// Constants
// ============================================================================

// Helper to format metric values consistently
function formatMetricValue(value: number, unit: string): string {
  if (unit === '%') return value.toFixed(1)
  if (value >= 100) return value.toFixed(0)
  return value.toFixed(1)
}

// Status styling
const STATUS_STYLES = {
  healthy: {
    bg: 'linear-gradient(135deg, rgba(22, 163, 74, 0.08) 0%, rgba(22, 163, 74, 0.04) 100%)',
    border: 'rgba(22, 163, 74, 0.3)',
    color: '#16a34a',
    label: 'Healthy',
    icon: HealthyIcon,
  },
  warning: {
    bg: 'linear-gradient(135deg, rgba(234, 179, 8, 0.08) 0%, rgba(234, 179, 8, 0.04) 100%)',
    border: 'rgba(234, 179, 8, 0.3)',
    color: '#ca8a04',
    label: 'Attention',
    icon: WarningIcon,
  },
  critical: {
    bg: 'linear-gradient(135deg, rgba(220, 38, 38, 0.08) 0%, rgba(220, 38, 38, 0.04) 100%)',
    border: 'rgba(220, 38, 38, 0.3)',
    color: '#dc2626',
    label: 'Critical',
    icon: CriticalIcon,
  },
}

// Executive Status Banner
interface StatusBannerProps {
  status: HealthStatus
  issueCount: number
  totalMetrics: number
}

const StatusBanner = ({ status, issueCount, totalMetrics }: StatusBannerProps) => {
  const styles = STATUS_STYLES[status]
  const Icon = styles.icon

  const getMessage = () => {
    if (status === 'healthy') return 'All Systems Operational'
    if (status === 'warning') return `${issueCount} Item${issueCount > 1 ? 's' : ''} Need${issueCount === 1 ? 's' : ''} Attention`
    return `${issueCount} Critical Issue${issueCount > 1 ? 's' : ''} Detected`
  }

  return (
    <Box
      component={motion.div}
      initial={{ opacity: 0, y: -20 }}
      animate={{ opacity: 1, y: 0 }}
      sx={{
        background: styles.bg,
        border: `2px solid ${styles.border}`,
        borderRadius: '16px',
        padding: { xs: '20px', md: '24px 32px' },
        marginBottom: '24px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '16px',
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
        <Box
          sx={{
            width: { xs: 48, md: 56 },
            height: { xs: 48, md: 56 },
            borderRadius: '14px',
            background: `${styles.color}15`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Icon sx={{ fontSize: { xs: 28, md: 32 }, color: styles.color }} />
        </Box>
        <Box>
          <Typography
            sx={{
              fontSize: { xs: '1.25rem', md: '1.5rem' },
              fontWeight: 700,
              color: styles.color,
              letterSpacing: '-0.02em',
              lineHeight: 1.2,
            }}
          >
            {getMessage()}
          </Typography>
          <Typography sx={{ fontSize: '0.875rem', color: 'var(--mono-500)', mt: '4px' }}>
            Monitoring {totalMetrics.toLocaleString()} data points in real-time
          </Typography>
        </Box>
      </Box>
      <Box
        sx={{
          padding: '8px 20px',
          borderRadius: '100px',
          background: `${styles.color}15`,
          border: `1px solid ${styles.border}`,
        }}
      >
        <Typography sx={{ fontSize: '0.875rem', fontWeight: 700, color: styles.color, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
          {styles.label}
        </Typography>
      </Box>
    </Box>
  )
}

// Section Header with Status Badge
interface SectionHeaderProps {
  title: string
  subtitle?: string
  icon: React.ElementType
  status: HealthStatus
  compact?: boolean
}

const SectionHeader = ({ title, subtitle, icon: Icon, status, compact }: SectionHeaderProps) => {
  const styles = STATUS_STYLES[status]

  if (compact) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '16px' }}>
        <Box
          sx={{
            width: 36,
            height: 36,
            borderRadius: '8px',
            background: 'var(--mono-100)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Icon sx={{ fontSize: 20, color: 'var(--mono-600)' }} />
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <Typography sx={{ fontSize: '0.9375rem', fontWeight: 700, color: 'var(--mono-950)', letterSpacing: '-0.01em' }}>
            {title}
          </Typography>
          <Box
            sx={{
              padding: '3px 10px',
              borderRadius: '100px',
              background: `${styles.color}12`,
              display: 'flex',
              alignItems: 'center',
              gap: '5px',
            }}
          >
            <Box sx={{ width: 6, height: 6, borderRadius: '50%', background: styles.color }} />
            <Typography sx={{ fontSize: '0.6875rem', fontWeight: 600, color: styles.color, textTransform: 'uppercase' }}>
              {styles.label}
            </Typography>
          </Box>
        </Box>
      </Box>
    )
  }

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <Box
          sx={{
            width: 40,
            height: 40,
            borderRadius: '10px',
            background: 'var(--mono-100)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Icon sx={{ fontSize: 22, color: 'var(--mono-600)' }} />
        </Box>
        <Box>
          <Typography sx={{ fontSize: '1rem', fontWeight: 700, color: 'var(--mono-950)', letterSpacing: '-0.01em' }}>
            {title}
          </Typography>
          {subtitle && (
            <Typography sx={{ fontSize: '0.8125rem', color: 'var(--mono-500)' }}>
              {subtitle}
            </Typography>
          )}
        </Box>
      </Box>
      <Box
        sx={{
          padding: '6px 14px',
          borderRadius: '100px',
          background: styles.bg,
          border: `1px solid ${styles.border}`,
          display: 'flex',
          alignItems: 'center',
          gap: '6px',
        }}
      >
        <Box sx={{ width: 8, height: 8, borderRadius: '50%', background: styles.color }} />
        <Typography sx={{ fontSize: '0.8125rem', fontWeight: 600, color: styles.color }}>
          {styles.label}
        </Typography>
      </Box>
    </Box>
  )
}

// Large Metric Card
interface MetricCardProps {
  label: string
  fullLabel: string
  value: number
  unit: string
  status: HealthStatus
  showProgress?: boolean
  progressMax?: number
  delay?: number
}

const MetricCard = ({ label, fullLabel, value, unit, status, showProgress, progressMax = 100, delay = 0 }: MetricCardProps) => {
  const styles = STATUS_STYLES[status]

  return (
    <Box
      component={motion.div}
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.25 }}
      sx={{
        background: 'var(--surface-base)',
        border: '1px solid var(--surface-border)',
        borderRadius: '14px',
        padding: '20px',
        position: 'relative',
        overflow: 'hidden',
        transition: 'all 0.2s ease',
        '&:hover': {
          borderColor: styles.border,
          boxShadow: `0 8px 24px ${styles.color}10`,
          transform: 'translateY(-2px)',
        },
      }}
    >
      {/* Status indicator bar */}
      <Box
        sx={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          height: '4px',
          background: styles.color,
          opacity: 0.8,
        }}
      />

      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: '12px' }}>
        <Box>
          <Typography sx={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--mono-600)', mb: '2px' }}>
            {label}
          </Typography>
          <Typography sx={{ fontSize: '0.75rem', color: 'var(--mono-400)' }}>
            {fullLabel}
          </Typography>
        </Box>
        <Box
          sx={{
            padding: '4px 10px',
            borderRadius: '6px',
            background: `${styles.color}12`,
          }}
        >
          <Typography sx={{ fontSize: '0.6875rem', fontWeight: 700, color: styles.color, textTransform: 'uppercase' }}>
            {styles.label}
          </Typography>
        </Box>
      </Box>

      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: '6px', mb: showProgress ? '12px' : 0 }}>
        <Typography
          sx={{
            fontSize: '2rem',
            fontWeight: 800,
            fontFamily: "'JetBrains Mono', monospace",
            color: 'var(--mono-950)',
            lineHeight: 1,
            letterSpacing: '-0.02em',
          }}
        >
          {typeof value === 'number' ? formatMetricValue(value, unit) : value}
        </Typography>
        <Typography sx={{ fontSize: '1rem', fontWeight: 600, color: 'var(--mono-400)' }}>
          {unit}
        </Typography>
      </Box>

      {showProgress && (
        <Box sx={{ mt: '4px' }}>
          <LinearProgress
            variant="determinate"
            value={Math.min(100, (value / progressMax) * 100)}
            sx={{
              height: 8,
              borderRadius: 4,
              backgroundColor: 'var(--mono-100)',
              '& .MuiLinearProgress-bar': {
                borderRadius: 4,
                backgroundColor: styles.color,
              },
            }}
          />
        </Box>
      )}
    </Box>
  )
}

// Metric Section Component - reduces conditional complexity
interface MetricSectionProps {
  title: string
  subtitle?: string
  icon: React.ElementType
  status: HealthStatus
  metrics: ProcessedMetric[]
  baseDelay: number
  compact?: boolean
}

const MetricSection = ({ title, subtitle, icon, status, metrics, baseDelay, compact }: MetricSectionProps) => {
  if (metrics.length === 0) return null

  return (
    <Box sx={{ marginBottom: compact ? 0 : '32px' }}>
      <SectionHeader title={title} subtitle={subtitle} icon={icon} status={status} compact={compact} />
      <Grid container spacing={compact ? 1.5 : 2}>
        {metrics.map((m, idx) => (
          <Grid item xs={6} sm={compact ? 6 : 4} md={compact ? 6 : 3} key={m.type}>
            <MetricCard
              label={m.config.label}
              fullLabel={m.config.fullLabel}
              value={m.value}
              unit={m.config.unit}
              status={m.config.getStatus(m.value)}
              showProgress={m.config.showProgress}
              progressMax={m.config.progressMax}
              delay={baseDelay + idx * 0.05}
            />
          </Grid>
        ))}
      </Grid>
    </Box>
  )
}

// Station Item for attention list
interface StationItemProps {
  station: BaseStation
  variant: 'offline' | 'maintenance'
  delay: number
}

const STATION_ITEM_STYLES = {
  offline: {
    bg: 'rgba(220, 38, 38, 0.03)',
    hoverBg: 'rgba(220, 38, 38, 0.06)',
    iconBg: 'rgba(220, 38, 38, 0.1)',
    color: '#dc2626',
    subColor: '#b91c1c',
    label: 'OFFLINE',
    Icon: OfflineIcon,
  },
  maintenance: {
    bg: 'rgba(234, 179, 8, 0.03)',
    hoverBg: 'rgba(234, 179, 8, 0.06)',
    iconBg: 'rgba(234, 179, 8, 0.1)',
    color: '#ca8a04',
    subColor: '#a16207',
    label: 'MAINTENANCE',
    Icon: MaintenanceIcon,
  },
} as const

const StationItem = ({ station, variant, delay }: StationItemProps) => {
  const style = STATION_ITEM_STYLES[variant]
  const Icon = style.Icon

  return (
    <Box
      component={motion.div}
      initial={{ opacity: 0, x: -10 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ delay }}
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: '14px',
        padding: '16px 24px',
        borderBottom: '1px solid var(--surface-border)',
        background: style.bg,
        '&:hover': { background: style.hoverBg },
        '&:last-child': { borderBottom: 'none' },
      }}
    >
      <Box
        sx={{
          width: 40,
          height: 40,
          borderRadius: '10px',
          background: style.iconBg,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Icon sx={{ fontSize: 20, color: style.color }} />
      </Box>
      <Box sx={{ flex: 1 }}>
        <Typography sx={{ fontSize: '0.9375rem', fontWeight: 600, color: style.color }}>
          {station.stationName}
        </Typography>
        <Typography sx={{ fontSize: '0.8125rem', color: style.subColor }}>
          {station.location || 'Location unknown'}
        </Typography>
      </Box>
      <Box sx={{ padding: '4px 12px', borderRadius: '6px', background: style.iconBg }}>
        <Typography sx={{ fontSize: '0.75rem', fontWeight: 700, color: style.color }}>
          {style.label}
        </Typography>
      </Box>
    </Box>
  )
}

// Stations Attention List
interface StationsAttentionListProps {
  offlineStations: BaseStation[]
  maintenanceStations: BaseStation[]
}

const StationsAttentionList = ({ offlineStations, maintenanceStations }: StationsAttentionListProps) => {
  const hasIssues = offlineStations.length > 0 || maintenanceStations.length > 0

  if (!hasIssues) {
    return (
      <Box sx={{ padding: '40px 24px', textAlign: 'center' }}>
        <HealthyIcon sx={{ fontSize: 48, color: '#16a34a', opacity: 0.6, mb: '12px' }} />
        <Typography sx={{ fontSize: '0.9375rem', fontWeight: 600, color: 'var(--mono-600)' }}>
          All Stations Healthy
        </Typography>
        <Typography sx={{ fontSize: '0.8125rem', color: 'var(--mono-400)', mt: '4px' }}>
          No immediate action required
        </Typography>
      </Box>
    )
  }

  return (
    <>
      {offlineStations.map((station, idx) => (
        <StationItem key={station.id} station={station} variant="offline" delay={0.6 + idx * 0.05} />
      ))}
      {maintenanceStations.map((station, idx) => (
        <StationItem key={station.id} station={station} variant="maintenance" delay={0.65 + idx * 0.05} />
      ))}
    </>
  )
}

// Fab styles for alert states
const FAB_STYLES = {
  alert: {
    background: '#dc2626',
    hoverBg: '#b91c1c',
    boxShadow: '0 6px 24px rgba(220, 38, 38, 0.4)',
    animation: 'pulse-glow 2s infinite',
    badgeBg: '#fef2f2',
    badgeColor: '#dc2626',
  },
  normal: {
    background: '#1f2937',
    hoverBg: '#374151',
    boxShadow: '0 6px 24px rgba(0, 0, 0, 0.25)',
    animation: 'none',
    badgeBg: 'var(--mono-200)',
    badgeColor: 'var(--mono-600)',
  },
} as const

// Infrastructure Status Card
interface InfraCardProps {
  icon: React.ElementType
  label: string
  value: number
  status: HealthStatus
  description: string
  delay?: number
}

const InfraCard = ({ icon: Icon, label, value, status, description, delay = 0 }: InfraCardProps) => {
  const styles = STATUS_STYLES[status]

  return (
    <Box
      component={motion.div}
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ delay, duration: 0.25 }}
      sx={{
        background: styles.bg,
        border: `1px solid ${styles.border}`,
        borderRadius: '14px',
        padding: '20px',
        display: 'flex',
        alignItems: 'center',
        gap: '16px',
        transition: 'all 0.2s ease',
        '&:hover': {
          transform: 'translateY(-2px)',
          boxShadow: `0 8px 24px ${styles.color}15`,
        },
      }}
    >
      <Box
        sx={{
          width: 52,
          height: 52,
          borderRadius: '12px',
          background: `${styles.color}15`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        }}
      >
        <Icon sx={{ fontSize: 26, color: styles.color }} />
      </Box>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography sx={{ fontSize: '0.8125rem', fontWeight: 600, color: 'var(--mono-500)', mb: '2px' }}>
          {label}
        </Typography>
        <Typography
          sx={{
            fontSize: '1.75rem',
            fontWeight: 800,
            fontFamily: "'JetBrains Mono', monospace",
            color: styles.color,
            lineHeight: 1,
          }}
        >
          {value}
        </Typography>
        <Typography sx={{ fontSize: '0.75rem', color: 'var(--mono-400)', mt: '4px' }}>
          {description}
        </Typography>
      </Box>
    </Box>
  )
}

export default function Dashboard() {
  const [activityDrawerOpen, setActivityDrawerOpen] = useState(false)

  // Use custom hook for all data fetching and processing
  const {
    isLoading,
    stations,
    activeCount,
    maintenanceStations,
    offlineStations,
    unreadAlerts,
    metrics,
    systemMetrics,
    nr78Metrics,
    nr28Metrics,
    qualityMetrics,
    systemStatus,
    nr78Status,
    nr28Status,
    qualityStatus,
    infraStatus,
    overallStatus,
    issueCount,
  } = useDashboardData()

  if (isLoading) {
    return <LoadingSpinner />
  }

  // Fab button styles
  const fabStyles = unreadAlerts > 0 ? FAB_STYLES.alert : FAB_STYLES.normal

  return (
    <Box sx={{ maxWidth: '1600px', margin: '0 auto', padding: { xs: '16px', sm: '24px', md: '32px' } }}>
      {/* Header */}
      <Box component={motion.div} initial={{ opacity: 0, y: -12 }} animate={{ opacity: 1, y: 0 }} sx={{ marginBottom: '8px' }}>
        <Typography
          variant="h1"
          sx={{
            fontSize: { xs: '1.5rem', sm: '1.75rem', md: '2rem' },
            fontWeight: 800,
            letterSpacing: '-0.03em',
            color: 'var(--mono-950)',
          }}
        >
          Operations Dashboard
        </Typography>
      </Box>

      {/* Executive Status Banner */}
      <StatusBanner status={overallStatus} issueCount={issueCount} totalMetrics={metrics.length} />

      {/* Infrastructure Overview */}
      <Box sx={{ marginBottom: '32px' }}>
        <SectionHeader
          title="Infrastructure"
          subtitle="Base station fleet status"
          icon={StationIcon}
          status={infraStatus}
        />
        <Grid container spacing={2}>
          <Grid item xs={6} md={3}>
            <InfraCard
              icon={StationIcon}
              label="Total Stations"
              value={stations.length}
              status="healthy"
              description="Deployed units"
              delay={0.1}
            />
          </Grid>
          <Grid item xs={6} md={3}>
            <InfraCard
              icon={HealthyIcon}
              label="Active"
              value={activeCount}
              status={getInfraStatus(activeCount, stations.length, 'active')}
              description="Operating normally"
              delay={0.15}
            />
          </Grid>
          <Grid item xs={6} md={3}>
            <InfraCard
              icon={MaintenanceIcon}
              label="Maintenance"
              value={maintenanceStations.length}
              status={getInfraStatus(0, 0, 'maintenance', maintenanceStations.length)}
              description="Scheduled work"
              delay={0.2}
            />
          </Grid>
          <Grid item xs={6} md={3}>
            <InfraCard
              icon={OfflineIcon}
              label="Offline"
              value={offlineStations.length}
              status={getInfraStatus(0, 0, 'offline', offlineStations.length)}
              description="Need attention"
              delay={0.25}
            />
          </Grid>
        </Grid>
      </Box>

      {/* System Performance */}
      <MetricSection
        title="System Performance"
        subtitle="Hardware utilization metrics"
        icon={PerformanceIcon}
        status={systemStatus}
        metrics={systemMetrics}
        baseDelay={0.3}
      />

      {/* 5G NR n78 (3.5 GHz) */}
      <MetricSection
        title="5G NR n78"
        subtitle="3.5 GHz high-speed band"
        icon={FiveGIcon}
        status={nr78Status}
        metrics={nr78Metrics}
        baseDelay={0.35}
      />

      {/* 5G NR n28 + Network Quality + Stations Requiring Attention */}
      <Grid container spacing={3} sx={{ marginBottom: '32px' }}>
        {/* 5G NR n28 (700 MHz) - compact */}
        <Grid item xs={12} md={4}>
          <Box
            component={motion.div}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.4 }}
            sx={{
              background: 'var(--surface-base)',
              border: '1px solid var(--surface-border)',
              borderRadius: '16px',
              padding: '20px',
              height: '100%',
            }}
          >
            <MetricSection
              title="5G NR n28"
              icon={NetworkIcon}
              status={nr28Status}
              metrics={nr28Metrics}
              baseDelay={0.4}
              compact
            />
          </Box>
        </Grid>

        {/* Network Quality - compact */}
        <Grid item xs={12} md={4}>
          <Box
            component={motion.div}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.45 }}
            sx={{
              background: 'var(--surface-base)',
              border: '1px solid var(--surface-border)',
              borderRadius: '16px',
              padding: '20px',
              height: '100%',
            }}
          >
            <MetricSection
              title="Network Quality"
              icon={TrendingUpIcon}
              status={qualityStatus}
              metrics={qualityMetrics}
              baseDelay={0.45}
              compact
            />
          </Box>
        </Grid>

        {/* Stations requiring attention */}
        <Grid item xs={12} md={4}>
          <Box
            component={motion.div}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.5 }}
            sx={{
              background: 'var(--surface-base)',
              border: '1px solid var(--surface-border)',
              borderRadius: '16px',
              overflow: 'hidden',
              height: '100%',
            }}
          >
            <Box sx={{ padding: '16px 20px', borderBottom: '1px solid var(--surface-border)' }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Typography sx={{ fontSize: '0.9375rem', fontWeight: 700, color: 'var(--mono-950)' }}>
                  Attention Required
                </Typography>
                {(maintenanceStations.length + offlineStations.length) > 0 && (
                  <Box sx={{ padding: '3px 10px', borderRadius: '100px', background: 'rgba(220, 38, 38, 0.1)' }}>
                    <Typography sx={{ fontSize: '0.6875rem', fontWeight: 700, color: '#dc2626' }}>
                      {maintenanceStations.length + offlineStations.length}
                    </Typography>
                  </Box>
                )}
              </Box>
            </Box>
            <Box sx={{ maxHeight: 280, overflow: 'auto' }}>
              <StationsAttentionList offlineStations={offlineStations} maintenanceStations={maintenanceStations} />
            </Box>
          </Box>
        </Grid>
      </Grid>

      {/* Trends Chart - full width */}
      <Box
        component={motion.div}
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.55 }}
        sx={{
          background: 'var(--surface-base)',
          border: '1px solid var(--surface-border)',
          borderRadius: '16px',
          padding: '24px',
          marginBottom: '32px',
        }}
      >
        <Typography sx={{ fontSize: '1rem', fontWeight: 700, color: 'var(--mono-950)', marginBottom: '16px' }}>
          Performance Trends
        </Typography>
        <MetricsChart />
      </Box>

      {/* Floating Activity Feed Button */}
      <Fab
        onClick={() => setActivityDrawerOpen(true)}
        sx={{
          position: 'fixed',
          bottom: 28,
          right: 28,
          width: 60,
          height: 60,
          background: fabStyles.background,
          boxShadow: fabStyles.boxShadow,
          animation: fabStyles.animation,
          '&:hover': { background: fabStyles.hoverBg, transform: 'scale(1.05)' },
          '@keyframes pulse-glow': {
            '0%, 100%': { boxShadow: '0 6px 24px rgba(220, 38, 38, 0.4)' },
            '50%': { boxShadow: '0 6px 36px rgba(220, 38, 38, 0.6)' },
          },
        }}
      >
        <Badge
          badgeContent={unreadAlerts}
          max={99}
          sx={{
            '& .MuiBadge-badge': {
              background: fabStyles.badgeBg,
              color: fabStyles.badgeColor,
              fontWeight: 700,
              fontSize: '0.75rem',
              minWidth: '22px',
              height: '22px',
              top: -4,
              right: -4,
            },
          }}
        >
          <NotificationsIcon sx={{ fontSize: 28, color: '#fff' }} />
        </Badge>
      </Fab>

      {/* Activity Feed Drawer */}
      <Drawer
        anchor="right"
        open={activityDrawerOpen}
        onClose={() => setActivityDrawerOpen(false)}
        PaperProps={{
          sx: {
            width: { xs: '100%', sm: 420 },
            background: 'var(--surface-base)',
            borderLeft: '1px solid var(--surface-border)',
          },
        }}
      >
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '20px 24px',
              borderBottom: '1px solid var(--surface-border)',
              background: unreadAlerts > 0 ? 'rgba(220, 38, 38, 0.03)' : 'transparent',
            }}
          >
            <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <Box
                sx={{
                  width: 10,
                  height: 10,
                  borderRadius: '50%',
                  background: unreadAlerts > 0 ? '#dc2626' : '#16a34a',
                  animation: unreadAlerts > 0 ? 'pulse 1.5s infinite' : 'none',
                  '@keyframes pulse': {
                    '0%, 100%': { opacity: 1 },
                    '50%': { opacity: 0.5 },
                  },
                }}
              />
              <Typography sx={{ fontSize: '1.125rem', fontWeight: 700, color: 'var(--mono-950)' }}>
                Activity Feed
              </Typography>
              {unreadAlerts > 0 && (
                <Box
                  sx={{
                    padding: '4px 10px',
                    borderRadius: '100px',
                    background: 'rgba(220, 38, 38, 0.1)',
                    border: '1px solid rgba(220, 38, 38, 0.2)',
                  }}
                >
                  <Typography sx={{ fontSize: '0.75rem', fontWeight: 700, color: '#dc2626' }}>
                    {unreadAlerts} unread
                  </Typography>
                </Box>
              )}
            </Box>
            <IconButton
              onClick={() => setActivityDrawerOpen(false)}
              sx={{ color: 'var(--mono-500)', '&:hover': { background: 'var(--mono-100)' } }}
            >
              <CloseIcon />
            </IconButton>
          </Box>
          <Box sx={{ flex: 1, overflow: 'auto', padding: '20px' }}>
            <LiveActivityFeed />
          </Box>
        </Box>
      </Drawer>
    </Box>
  )
}
