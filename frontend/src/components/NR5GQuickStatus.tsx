import { Box, Typography } from '@mui/material'
import { motion } from 'framer-motion'
import {
  CheckCircle as PassIcon,
  Cancel as FailIcon,
  Warning as WarnIcon,
} from '@mui/icons-material'
import { CSS_VARS, METRIC_STATUS_STYLES, type MetricStatusType } from '../constants/designSystem'
import {
  type MetricStatus,
  METRICS_CONFIG,
  SSV_METRIC_TYPES,
  countMetricStatuses,
} from '../constants/metricsConfig'

// Icons for metric status (component-specific, can't be in shared config)
const STATUS_ICONS = {
  pass: PassIcon,
  warning: WarnIcon,
  fail: FailIcon,
} as const

interface MetricValue {
  readonly type: string
  readonly value: number
}

interface NR5GQuickStatusProps {
  readonly metrics: readonly MetricValue[]
  readonly delay?: number
}

export default function NR5GQuickStatus({ metrics, delay = 0 }: NR5GQuickStatusProps) {
  if (metrics.length === 0) return null

  // Filter to only SSV metrics
  const ssvMetrics = metrics.filter(m => SSV_METRIC_TYPES.has(m.type))

  // Group by band
  const nr3500 = ssvMetrics.filter((m) => m.type.includes('NR3500'))
  const nr700 = ssvMetrics.filter((m) => m.type.includes('NR700'))
  const other = ssvMetrics.filter((m) => !m.type.includes('NR3500') && !m.type.includes('NR700'))

  // Calculate summary using centralized config
  const allStatuses: MetricStatus[] = ssvMetrics.map((m) => {
    const config = METRICS_CONFIG[m.type]
    return config ? config.getMetricStatus(m.value) : 'pass'
  })
  const { pass: passCount, warning: warnCount, fail: failCount } = countMetricStatuses(allStatuses)

  const renderMetricChip = (m: MetricValue, idx: number) => {
    const config = METRICS_CONFIG[m.type]
    if (!config) return null

    const status = config.getMetricStatus(m.value) as MetricStatusType
    const styles = METRIC_STATUS_STYLES[status]
    const Icon = STATUS_ICONS[status]

    return (
      <Box
        key={m.type}
        component={motion.div}
        initial={{ opacity: 0, scale: 0.9 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ delay: delay + idx * 0.03, duration: 0.2 }}
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          padding: '8px 12px',
          background: styles.bg,
          border: `1px solid ${styles.border}`,
          borderRadius: '8px',
          minWidth: 'fit-content',
        }}
      >
        <Icon sx={{ fontSize: 16, color: styles.color }} />
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
          <Typography
            sx={{
              fontSize: '0.625rem',
              fontWeight: 600,
              color: 'var(--mono-500)',
              textTransform: 'uppercase',
              letterSpacing: '0.03em',
              lineHeight: 1,
            }}
          >
            {config.label} {config.bandShort && <span style={{ opacity: 0.7 }}>({config.bandShort})</span>}
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'baseline', gap: '4px' }}>
            <Typography
              sx={{
                fontSize: '0.875rem',
                fontWeight: 700,
                fontFamily: "'JetBrains Mono', monospace",
                color: styles.color,
                lineHeight: 1.2,
              }}
            >
              {config.format(m.value)}
            </Typography>
            <Typography
              sx={{
                fontSize: '0.625rem',
                color: 'var(--mono-500)',
              }}
            >
              {config.unit}
            </Typography>
          </Box>
        </Box>
      </Box>
    )
  }

  return (
    <Box
      component={motion.div}
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.3 }}
    >
      {/* Header with Summary */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: '16px',
          flexWrap: 'wrap',
          gap: '12px',
        }}
      >
        <Typography
          sx={{
            fontSize: '0.875rem',
            fontWeight: 600,
            color: 'var(--mono-950)',
            letterSpacing: '-0.01em',
          }}
        >
          5G NR SSV Status
        </Typography>
        <Box sx={{ display: 'flex', gap: '12px' }}>
          {passCount > 0 && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              <PassIcon sx={{ fontSize: 14, color: CSS_VARS.statusActive }} />
              <Typography sx={{ fontSize: '0.75rem', fontWeight: 600, color: CSS_VARS.statusActive }}>
                {passCount} Pass
              </Typography>
            </Box>
          )}
          {warnCount > 0 && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              <WarnIcon sx={{ fontSize: 14, color: CSS_VARS.statusMaintenance }} />
              <Typography sx={{ fontSize: '0.75rem', fontWeight: 600, color: CSS_VARS.statusMaintenance }}>
                {warnCount} Warn
              </Typography>
            </Box>
          )}
          {failCount > 0 && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              <FailIcon sx={{ fontSize: 14, color: CSS_VARS.statusOffline }} />
              <Typography sx={{ fontSize: '0.75rem', fontWeight: 600, color: CSS_VARS.statusOffline }}>
                {failCount} Fail
              </Typography>
            </Box>
          )}
        </Box>
      </Box>

      {/* Metrics Grid */}
      <Box
        sx={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: '8px',
        }}
      >
        {/* n78 3.5GHz metrics */}
        {nr3500.map((m, idx) => renderMetricChip(m, idx))}

        {/* n28 700MHz metrics */}
        {nr700.map((m, idx) => renderMetricChip(m, nr3500.length + idx))}

        {/* Other metrics (Latency, TX Imbalance) */}
        {other.map((m, idx) => renderMetricChip(m, nr3500.length + nr700.length + idx))}
      </Box>
    </Box>
  )
}
