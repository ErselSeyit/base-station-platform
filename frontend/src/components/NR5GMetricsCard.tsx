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

type MetricStatus = 'pass' | 'warning' | 'fail'

// Helper to evaluate thresholds (higher is better)
function evalHigher(v: number, passThreshold: number, warnThreshold: number): MetricStatus {
  if (v >= passThreshold) return 'pass'
  if (v >= warnThreshold) return 'warning'
  return 'fail'
}

// Helper to evaluate thresholds (lower is better)
function evalLower(v: number, passThreshold: number, warnThreshold: number): MetricStatus {
  if (v <= passThreshold) return 'pass'
  if (v <= warnThreshold) return 'warning'
  return 'fail'
}

// SSV Threshold Configuration for 5G NR Metrics
interface SSVThreshold {
  label: string
  unit: string
  icon: ReactNode
  band?: string
  getStatus: (value: number) => MetricStatus
  passThreshold: string
}

const SSV_THRESHOLDS: Record<string, SSVThreshold> = {
  DL_THROUGHPUT_NR3500: {
    label: 'DL Throughput',
    unit: 'Mbps',
    band: 'n78 (3.5GHz)',
    icon: <TrendingDown sx={{ fontSize: 20 }} />,
    getStatus: (v) => evalHigher(v, 1000, 500),
    passThreshold: '≥1000 Mbps',
  },
  UL_THROUGHPUT_NR3500: {
    label: 'UL Throughput',
    unit: 'Mbps',
    band: 'n78 (3.5GHz)',
    icon: <TrendingUp sx={{ fontSize: 20 }} />,
    getStatus: (v) => evalHigher(v, 75, 40),
    passThreshold: '≥75 Mbps',
  },
  RSRP_NR3500: {
    label: 'RSRP',
    unit: 'dBm',
    band: 'n78 (3.5GHz)',
    icon: <SignalCellularAlt sx={{ fontSize: 20 }} />,
    getStatus: (v) => evalHigher(v, -85, -100),
    passThreshold: '≥-85 dBm',
  },
  SINR_NR3500: {
    label: 'SINR',
    unit: 'dB',
    band: 'n78 (3.5GHz)',
    icon: <SettingsInputAntenna sx={{ fontSize: 20 }} />,
    getStatus: (v) => evalHigher(v, 10, 5),
    passThreshold: '≥10 dB',
  },
  DL_THROUGHPUT_NR700: {
    label: 'DL Throughput',
    unit: 'Mbps',
    band: 'n28 (700MHz)',
    icon: <TrendingDown sx={{ fontSize: 20 }} />,
    getStatus: (v) => evalHigher(v, 50, 25),
    passThreshold: '≥50 Mbps',
  },
  UL_THROUGHPUT_NR700: {
    label: 'UL Throughput',
    unit: 'Mbps',
    band: 'n28 (700MHz)',
    icon: <TrendingUp sx={{ fontSize: 20 }} />,
    getStatus: (v) => evalHigher(v, 20, 10),
    passThreshold: '≥20 Mbps',
  },
  LATENCY_PING: {
    label: 'Latency',
    unit: 'ms',
    icon: <Timer sx={{ fontSize: 20 }} />,
    getStatus: (v) => evalLower(v, 15, 30),
    passThreshold: '≤15 ms',
  },
  TX_IMBALANCE: {
    label: 'TX Imbalance',
    unit: 'dB',
    icon: <Speed sx={{ fontSize: 20 }} />,
    getStatus: (v) => evalLower(Math.abs(v), 4, 6),
    passThreshold: '≤4 dB',
  },
}

const STATUS_COLORS = {
  pass: {
    bg: 'rgba(22, 163, 74, 0.08)',
    border: 'rgba(22, 163, 74, 0.25)',
    accent: '#16a34a',
    text: '#15803d',
    glow: 'rgba(22, 163, 74, 0.15)',
  },
  warning: {
    bg: 'rgba(234, 88, 12, 0.08)',
    border: 'rgba(234, 88, 12, 0.25)',
    accent: '#ea580c',
    text: '#c2410c',
    glow: 'rgba(234, 88, 12, 0.15)',
  },
  fail: {
    bg: 'rgba(220, 38, 38, 0.08)',
    border: 'rgba(220, 38, 38, 0.25)',
    accent: '#dc2626',
    text: '#b91c1c',
    glow: 'rgba(220, 38, 38, 0.15)',
  },
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

const SingleMetricCard = ({ metricKey, value, delay }: SingleMetricProps) => {
  const config = SSV_THRESHOLDS[metricKey]
  if (!config) return null

  const status = config.getStatus(value)
  const colors = STATUS_COLORS[status]

  return (
    <Box
      component={motion.div}
      initial={{ opacity: 0, y: 16, scale: 0.95 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ delay, duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
      sx={{
        position: 'relative',
        padding: '20px',
        background: colors.bg,
        border: `1px solid ${colors.border}`,
        borderRadius: '16px',
        overflow: 'hidden',
        transition: 'all 0.3s cubic-bezier(0.16, 1, 0.3, 1)',
        '&:hover': {
          transform: 'translateY(-2px)',
          boxShadow: `0 8px 24px ${colors.glow}`,
          borderColor: colors.accent,
        },
        '&::before': {
          content: '""',
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          height: '3px',
          background: colors.accent,
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
            background: `linear-gradient(135deg, ${colors.accent}22, ${colors.accent}11)`,
            color: colors.accent,
          }}
        >
          {config.icon}
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
            background: colors.accent,
            color: '#fff',
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
            color: colors.accent,
            letterSpacing: '-0.02em',
            lineHeight: 1,
          }}
        >
          {value.toFixed(2)}
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
            background: STATUS_COLORS.pass.accent,
          }}
        />
        Target: {config.passThreshold}
      </Typography>
    </Box>
  )
}

export default function NR5GMetricsCard({ metrics, stationName, delay = 0 }: NR5GMetricsCardProps) {
  // Group metrics by band
  const nr3500Metrics = metrics.filter((m) => m.type.includes('NR3500'))
  const nr700Metrics = metrics.filter((m) => m.type.includes('NR700'))
  const otherMetrics = metrics.filter(
    (m) => !m.type.includes('NR3500') && !m.type.includes('NR700')
  )

  // Calculate overall status
  const allStatuses = metrics.map((m) => {
    const config = SSV_THRESHOLDS[m.type]
    return config ? config.getStatus(m.value) : 'pass'
  })
  const hasFailure = allStatuses.includes('fail')
  const hasWarning = allStatuses.includes('warning')
  const passCount = allStatuses.filter((s) => s === 'pass').length

  // Determine overall status (avoid nested ternary)
  let overallStatus: MetricStatus = 'pass'
  if (hasFailure) {
    overallStatus = 'fail'
  } else if (hasWarning) {
    overallStatus = 'warning'
  }

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
                  background: 'var(--mono-100)',
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
            background: STATUS_COLORS[overallStatus].bg,
            border: `1px solid ${STATUS_COLORS[overallStatus].border}`,
            borderRadius: '10px',
          }}
        >
          <Box
            sx={{
              width: 10,
              height: 10,
              borderRadius: '50%',
              background: STATUS_COLORS[overallStatus].accent,
              boxShadow: `0 0 8px ${STATUS_COLORS[overallStatus].glow}`,
            }}
          />
          <Typography
            sx={{
              fontSize: '0.8125rem',
              fontWeight: 600,
              color: STATUS_COLORS[overallStatus].accent,
            }}
          >
            {passCount}/{metrics.length} Passing
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
            sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
              gap: '16px',
            }}
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
            sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
              gap: '16px',
            }}
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
            sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
              gap: '16px',
            }}
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
