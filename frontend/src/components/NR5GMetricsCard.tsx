import { Box, Typography } from '@mui/material'
import { motion } from 'framer-motion'
import {
  TrendingUp,
  TrendingDown,
  SignalCellularAlt,
  Speed,
  Timer,
  SettingsInputAntenna,
} from '@mui/icons-material'
import { ReactNode } from 'react'
import { METRIC_STATUS_STYLES, GRID_AUTO_FIT_SX, type MetricStatusType } from '../constants/designSystem'
import {
  type MetricStatus,
  METRICS_CONFIG,
  SSV_METRIC_TYPES,
  getWorstMetricStatus,
  countMetricStatuses,
} from '../constants/metricsConfig'

// Icon mapping for SSV metrics (component-specific, icons can't be in shared config)
const METRIC_ICONS: Record<string, ReactNode> = {
  DL_THROUGHPUT_NR3500: <TrendingDown sx={{ fontSize: 20 }} />,
  UL_THROUGHPUT_NR3500: <TrendingUp sx={{ fontSize: 20 }} />,
  RSRP_NR3500: <SignalCellularAlt sx={{ fontSize: 20 }} />,
  SINR_NR3500: <SettingsInputAntenna sx={{ fontSize: 20 }} />,
  DL_THROUGHPUT_NR700: <TrendingDown sx={{ fontSize: 20 }} />,
  UL_THROUGHPUT_NR700: <TrendingUp sx={{ fontSize: 20 }} />,
  LATENCY_PING: <Timer sx={{ fontSize: 20 }} />,
  TX_IMBALANCE: <Speed sx={{ fontSize: 20 }} />,
}

interface MetricValue {
  readonly type: string
  readonly value: number
}

interface NR5GMetricsCardProps {
  readonly metrics: readonly MetricValue[]
  readonly stationName?: string
  readonly delay?: number
}

interface SingleMetricProps {
  metricKey: string
  value: number
  delay: number
}

const SingleMetricCard = ({ metricKey, value, delay }: Readonly<SingleMetricProps>) => {
  const config = METRICS_CONFIG[metricKey]
  if (!config) return null

  const status = config.getMetricStatus(value) as MetricStatusType
  const styles = METRIC_STATUS_STYLES[status]
  const icon = METRIC_ICONS[metricKey]

  return (
    <Box
      component={motion.div}
      initial={{ opacity: 0, y: 16, scale: 0.95 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ delay, duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
      sx={{
        position: 'relative',
        padding: '20px',
        background: styles.bgSubtle,
        border: `1px solid ${styles.border}`,
        borderRadius: '16px',
        overflow: 'hidden',
        transition: 'all 0.3s cubic-bezier(0.16, 1, 0.3, 1)',
        '&:hover': {
          transform: 'translateY(-2px)',
          boxShadow: `0 8px 24px ${styles.shadow}`,
          borderColor: styles.color,
        },
        '&::before': {
          content: '""',
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          height: '3px',
          background: styles.color,
          borderRadius: '16px 16px 0 0',
        },
      }}
    >
      {/* Header with Icon and Band */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: '10px', mb: '12px' }}>
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 36,
            height: 36,
            borderRadius: '10px',
            background: styles.bgSubtle,
            color: styles.color,
          }}
        >
          {icon}
        </Box>
        <Box sx={{ flex: 1 }}>
          <Typography
            sx={{
              fontSize: '0.75rem',
              fontWeight: 600,
              color: 'var(--mono-600)',
              textTransform: 'uppercase',
              letterSpacing: '0.04em',
              lineHeight: 1.2,
            }}
          >
            {config.label}
          </Typography>
          {config.band && (
            <Typography
              sx={{
                fontSize: '0.625rem',
                color: 'var(--mono-500)',
                fontWeight: 500,
              }}
            >
              {config.band}
            </Typography>
          )}
        </Box>
        {/* Status Badge */}
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: '4px',
            padding: '3px 8px',
            borderRadius: '6px',
            background: styles.color,
            color: 'var(--mono-50)',
            fontSize: '0.625rem',
            fontWeight: 600,
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
          }}
        >
          {status === 'pass' && 'PASS'}
          {status === 'warning' && 'WARN'}
          {status === 'fail' && 'FAIL'}
        </Box>
      </Box>

      {/* Value Display */}
      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: '6px', mb: '8px' }}>
        <Typography
          sx={{
            fontSize: '2rem',
            fontWeight: 700,
            fontVariantNumeric: 'tabular-nums',
            fontFamily: "'JetBrains Mono', monospace",
            color: styles.color,
            letterSpacing: '-0.02em',
            lineHeight: 1,
          }}
        >
          {config.format(value)}
        </Typography>
        <Typography
          sx={{
            fontSize: '0.875rem',
            fontWeight: 500,
            color: 'var(--mono-500)',
          }}
        >
          {config.unit}
        </Typography>
      </Box>

      {/* Threshold Indicator */}
      <Typography
        sx={{
          fontSize: '0.6875rem',
          color: 'var(--mono-500)',
          display: 'flex',
          alignItems: 'center',
          gap: '4px',
        }}
      >
        <Box
          component="span"
          sx={{
            display: 'inline-block',
            width: 6,
            height: 6,
            borderRadius: '50%',
            background: METRIC_STATUS_STYLES.pass.color,
          }}
        />
        Target: {config.passThreshold}
      </Typography>
    </Box>
  )
}

export default function NR5GMetricsCard({ metrics, stationName, delay = 0 }: NR5GMetricsCardProps) {
  // Filter to only SSV metrics
  const ssvMetrics = metrics.filter(m => SSV_METRIC_TYPES.has(m.type))

  // Group metrics by band
  const nr3500Metrics = ssvMetrics.filter((m) => m.type.includes('NR3500'))
  const nr700Metrics = ssvMetrics.filter((m) => m.type.includes('NR700'))
  const otherMetrics = ssvMetrics.filter(
    (m) => !m.type.includes('NR3500') && !m.type.includes('NR700')
  )

  // Calculate overall status using centralized config
  const allStatuses: MetricStatus[] = ssvMetrics.map((m) => {
    const config = METRICS_CONFIG[m.type]
    return config ? config.getMetricStatus(m.value) : 'pass'
  })
  const overallStatus = getWorstMetricStatus(allStatuses)
  const { pass: passCount } = countMetricStatuses(allStatuses)

  const overallStyles = METRIC_STATUS_STYLES[overallStatus as MetricStatusType]

  return (
    <Box
      component={motion.div}
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
    >
      {/* Section Header */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          mb: '20px',
          flexWrap: 'wrap',
          gap: '12px',
        }}
      >
        <Box>
          <Typography
            sx={{
              fontSize: '1.125rem',
              fontWeight: 600,
              color: 'var(--mono-950)',
              letterSpacing: '-0.01em',
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
            }}
          >
            5G NR Metrics
            {stationName && (
              <Box
                component="span"
                sx={{
                  fontSize: '0.75rem',
                  fontWeight: 500,
                  color: 'var(--mono-500)',
                  padding: '2px 8px',
                  background: 'var(--surface-subtle)',
                  borderRadius: '4px',
                }}
              >
                {stationName}
              </Box>
            )}
          </Typography>
          <Typography
            sx={{
              fontSize: '0.8125rem',
              color: 'var(--mono-500)',
              mt: '4px',
            }}
          >
            Single Site Verification compliance status
          </Typography>
        </Box>

        {/* Overall Status Summary */}
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
            padding: '8px 16px',
            background: overallStyles.bgSubtle,
            border: `1px solid ${overallStyles.border}`,
            borderRadius: '10px',
          }}
        >
          <Box
            sx={{
              width: 10,
              height: 10,
              borderRadius: '50%',
              background: overallStyles.color,
              boxShadow: `0 0 8px ${overallStyles.shadow}`,
            }}
          />
          <Typography
            sx={{
              fontSize: '0.8125rem',
              fontWeight: 600,
              color: overallStyles.color,
            }}
          >
            {passCount}/{ssvMetrics.length} Passing
          </Typography>
        </Box>
      </Box>

      {/* n78 (3.5GHz) Section */}
      {nr3500Metrics.length > 0 && (
        <Box sx={{ mb: '24px' }}>
          <Typography
            sx={{
              fontSize: '0.6875rem',
              fontWeight: 600,
              color: 'var(--mono-500)',
              textTransform: 'uppercase',
              letterSpacing: '0.08em',
              mb: '12px',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
            }}
          >
            <Box
              sx={{
                width: 16,
                height: 2,
                background: 'var(--accent-primary)',
                borderRadius: 1,
              }}
            />
            Band n78 (3.5GHz) - High Capacity
          </Typography>
          <Box
            sx={GRID_AUTO_FIT_SX}
          >
            {nr3500Metrics.map((m, idx) => (
              <SingleMetricCard
                key={m.type}
                metricKey={m.type}
                value={m.value}
                delay={delay + 0.05 + idx * 0.05}
              />
            ))}
          </Box>
        </Box>
      )}

      {/* n28 (700MHz) Section */}
      {nr700Metrics.length > 0 && (
        <Box sx={{ mb: '24px' }}>
          <Typography
            sx={{
              fontSize: '0.6875rem',
              fontWeight: 600,
              color: 'var(--mono-500)',
              textTransform: 'uppercase',
              letterSpacing: '0.08em',
              mb: '12px',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
            }}
          >
            <Box
              sx={{
                width: 16,
                height: 2,
                background: 'var(--accent-info)',
                borderRadius: 1,
              }}
            />
            Band n28 (700MHz) - Extended Coverage
          </Typography>
          <Box
            sx={GRID_AUTO_FIT_SX}
          >
            {nr700Metrics.map((m, idx) => (
              <SingleMetricCard
                key={m.type}
                metricKey={m.type}
                value={m.value}
                delay={delay + 0.15 + idx * 0.05}
              />
            ))}
          </Box>
        </Box>
      )}

      {/* Other Metrics (Latency, TX Imbalance) */}
      {otherMetrics.length > 0 && (
        <Box>
          <Typography
            sx={{
              fontSize: '0.6875rem',
              fontWeight: 600,
              color: 'var(--mono-500)',
              textTransform: 'uppercase',
              letterSpacing: '0.08em',
              mb: '12px',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
            }}
          >
            <Box
              sx={{
                width: 16,
                height: 2,
                background: 'var(--mono-400)',
                borderRadius: 1,
              }}
            />
            System Performance
          </Typography>
          <Box
            sx={GRID_AUTO_FIT_SX}
          >
            {otherMetrics.map((m, idx) => (
              <SingleMetricCard
                key={m.type}
                metricKey={m.type}
                value={m.value}
                delay={delay + 0.25 + idx * 0.05}
              />
            ))}
          </Box>
        </Box>
      )}
    </Box>
  )
}
