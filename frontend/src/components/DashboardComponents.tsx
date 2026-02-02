/**
 * Dashboard Components
 *
 * Reusable components for the Dashboard page.
 * Extracted to reduce file size and improve maintainability.
 */

import {
  CheckCircle as HealthyIcon,
  Error as CriticalIcon,
  Warning as WarningIcon,
  PowerOff as OfflineIcon,
  Build as MaintenanceIcon,
  Info as InfoIcon,
} from '@mui/icons-material'
import { Box, Grid, LinearProgress, Typography } from '@mui/material'
import { motion } from 'framer-motion'
import { CARD_STATUS_STYLES, CSS_VARS, type CardStatus } from '../constants/designSystem'
import type { HealthStatus, ProcessedMetric } from '../hooks/useDashboardData'
import type { BaseStation } from '../types'

// ============================================================================
// Helpers - exported alongside components for convenience
// ============================================================================

// eslint-disable-next-line react-refresh/only-export-components
export function formatMetricValue(value: number, unit: string): string {
  if (unit === '%') return value.toFixed(1)
  if (value >= 100) return value.toFixed(0)
  return value.toFixed(1)
}

// ============================================================================
// Status Styles
// ============================================================================

export const STATUS_STYLES = {
  healthy: {
    ...CARD_STATUS_STYLES.healthy,
    icon: HealthyIcon,
  },
  warning: {
    ...CARD_STATUS_STYLES.warning,
    icon: WarningIcon,
  },
  critical: {
    ...CARD_STATUS_STYLES.critical,
    icon: CriticalIcon,
  },
  info: {
    ...CARD_STATUS_STYLES.info,
    icon: InfoIcon,
  },
} as const

export const STATION_ITEM_STYLES = {
  offline: {
    bg: CSS_VARS.statusErrorBgSubtle,
    hoverBg: CSS_VARS.statusErrorBg,
    iconBg: CSS_VARS.statusErrorBg,
    color: CSS_VARS.statusOffline,
    subColor: CSS_VARS.statusOffline,
    label: 'OFFLINE',
    Icon: OfflineIcon,
  },
  maintenance: {
    bg: CSS_VARS.statusWarningBgSubtle,
    hoverBg: CSS_VARS.statusWarningBg,
    iconBg: CSS_VARS.statusWarningBg,
    color: CSS_VARS.statusMaintenance,
    subColor: CSS_VARS.statusMaintenance,
    label: 'MAINTENANCE',
    Icon: MaintenanceIcon,
  },
} as const

export const FAB_STYLES = {
  alert: {
    background: CSS_VARS.statusOffline,
    hoverBg: CSS_VARS.statusOffline,
    boxShadow: `0 6px 24px ${CSS_VARS.statusErrorShadow}`,
    animation: 'pulse-glow 2s infinite',
    badgeBg: CSS_VARS.statusErrorBgSubtle,
    badgeColor: CSS_VARS.statusOffline,
  },
  normal: {
    background: 'var(--mono-800)',
    hoverBg: 'var(--mono-700)',
    boxShadow: '0 6px 24px rgba(0, 0, 0, 0.25)',
    animation: 'none',
    badgeBg: 'var(--mono-200)',
    badgeColor: 'var(--mono-600)',
  },
} as const

// ============================================================================
// Status Banner Component
// ============================================================================

interface StatusBannerProps {
  status: HealthStatus
  issueCount: number
  totalMetrics: number
}

export const StatusBanner = ({ status, issueCount, totalMetrics }: StatusBannerProps) => {
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
            background: styles.bg,
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
          background: styles.bg,
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

// ============================================================================
// Section Header Component
// ============================================================================

interface SectionHeaderProps {
  title: string
  subtitle?: string
  icon: React.ElementType
  status: HealthStatus
  compact?: boolean
}

export const SectionHeader = ({ title, subtitle, icon: Icon, status, compact }: SectionHeaderProps) => {
  const styles = STATUS_STYLES[status]

  if (compact) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '16px' }}>
        <Box
          sx={{
            width: 36,
            height: 36,
            borderRadius: '8px',
            background: 'var(--surface-subtle)',
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
              background: styles.bgSubtle,
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
            background: 'var(--surface-subtle)',
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

// ============================================================================
// Metric Card Component
// ============================================================================

interface MetricCardProps {
  label: string
  fullLabel: string
  value: number
  unit: string
  status: HealthStatus
  showProgress?: boolean
  progressMax?: number
  delay?: number
  format?: (v: number) => string
}

export const MetricCard = ({ label, fullLabel, value, unit, status, showProgress, progressMax = 100, delay = 0, format }: MetricCardProps) => {
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
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        '&:hover': {
          borderColor: styles.border,
          boxShadow: `0 8px 24px ${styles.shadow}`,
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
            background: styles.bgSubtle,
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
          {(() => {
            if (typeof value !== 'number') return value
            return format ? format(value) : formatMetricValue(value, unit)
          })()}
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
              backgroundColor: 'var(--surface-subtle)',
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

// ============================================================================
// Metric Section Component
// ============================================================================

interface MetricSectionProps {
  title: string
  subtitle?: string
  icon: React.ElementType
  status: HealthStatus
  metrics: ProcessedMetric[]
  baseDelay: number
  compact?: boolean
}

export const MetricSection = ({ title, subtitle, icon, status, metrics, baseDelay, compact }: MetricSectionProps) => {
  return (
    <Box sx={{ marginBottom: compact ? 0 : '32px' }}>
      <SectionHeader title={title} subtitle={subtitle} icon={icon} status={status} compact={compact} />
      {metrics.length === 0 ? (
        <Box
          sx={{
            padding: '48px 24px',
            textAlign: 'center',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: '12px',
          }}
        >
          <Typography sx={{ fontSize: '0.875rem', color: 'var(--mono-500)' }}>
            No data available yet. Waiting for live metrics...
          </Typography>
        </Box>
      ) : (
        <Grid container spacing={compact ? 1.5 : 2} alignItems="stretch">
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
                format={m.config.format}
              />
            </Grid>
          ))}
        </Grid>
      )}
    </Box>
  )
}

// ============================================================================
// Station Item Component
// ============================================================================

interface StationItemProps {
  station: BaseStation
  variant: 'offline' | 'maintenance'
  delay: number
}

export const StationItem = ({ station, variant, delay }: StationItemProps) => {
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

// ============================================================================
// Stations Attention List Component
// ============================================================================

interface StationsAttentionListProps {
  offlineStations: BaseStation[]
  maintenanceStations: BaseStation[]
}

export const StationsAttentionList = ({ offlineStations, maintenanceStations }: StationsAttentionListProps) => {
  const hasIssues = offlineStations.length > 0 || maintenanceStations.length > 0

  if (!hasIssues) {
    return (
      <Box sx={{ padding: '40px 24px', textAlign: 'center' }}>
        <HealthyIcon sx={{ fontSize: 48, color: CSS_VARS.statusActive, opacity: 0.6, mb: '12px' }} />
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

// ============================================================================
// Infrastructure Card Component
// ============================================================================

interface InfraCardProps {
  icon: React.ElementType
  label: string
  value: number
  status: CardStatus
  description: string
  delay?: number
}

export const InfraCard = ({ icon: Icon, label, value, status, description, delay = 0 }: InfraCardProps) => {
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
        height: '100%',
        '&:hover': {
          transform: 'translateY(-2px)',
          boxShadow: `0 8px 24px ${styles.shadow}`,
        },
      }}
    >
      <Box
        sx={{
          width: 52,
          height: 52,
          borderRadius: '12px',
          background: styles.bg,
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
