import { Box, Typography } from '@mui/material'
import { motion } from 'framer-motion'
import {
  CheckCircle as PassIcon,
  Cancel as FailIcon,
  Warning as WarnIcon,
} from '@mui/icons-material'

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

// SSV Thresholds for 5G metrics
const SSV_CONFIG: Record<string, {
  label: string
  unit: string
  band?: string
  getStatus: (v: number) => MetricStatus
  threshold: string
}> = {
  DL_THROUGHPUT_NR3500: {
    label: 'DL',
    unit: 'Mbps',
    band: '3.5G',
    getStatus: (v) => evalHigher(v, 1000, 500),
    threshold: '≥1000',
  },
  UL_THROUGHPUT_NR3500: {
    label: 'UL',
    unit: 'Mbps',
    band: '3.5G',
    getStatus: (v) => evalHigher(v, 75, 40),
    threshold: '≥75',
  },
  RSRP_NR3500: {
    label: 'RSRP',
    unit: 'dBm',
    band: '3.5G',
    getStatus: (v) => evalHigher(v, -85, -100),
    threshold: '≥-85',
  },
  SINR_NR3500: {
    label: 'SINR',
    unit: 'dB',
    band: '3.5G',
    getStatus: (v) => evalHigher(v, 10, 5),
    threshold: '≥10',
  },
  DL_THROUGHPUT_NR700: {
    label: 'DL',
    unit: 'Mbps',
    band: '700M',
    getStatus: (v) => evalHigher(v, 50, 25),
    threshold: '≥50',
  },
  UL_THROUGHPUT_NR700: {
    label: 'UL',
    unit: 'Mbps',
    band: '700M',
    getStatus: (v) => evalHigher(v, 20, 10),
    threshold: '≥20',
  },
  LATENCY_PING: {
    label: 'Latency',
    unit: 'ms',
    getStatus: (v) => evalLower(v, 15, 30),
    threshold: '≤15',
  },
  TX_IMBALANCE: {
    label: 'TX Imbal',
    unit: 'dB',
    getStatus: (v) => evalLower(Math.abs(v), 4, 6),
    threshold: '≤4',
  },
}

const STATUS_STYLES = {
  pass: {
    bg: 'rgba(22, 163, 74, 0.1)',
    border: 'rgba(22, 163, 74, 0.3)',
    color: '#16a34a',
    icon: PassIcon,
  },
  warning: {
    bg: 'rgba(234, 88, 12, 0.1)',
    border: 'rgba(234, 88, 12, 0.3)',
    color: '#ea580c',
    icon: WarnIcon,
  },
  fail: {
    bg: 'rgba(220, 38, 38, 0.1)',
    border: 'rgba(220, 38, 38, 0.3)',
    color: '#dc2626',
    icon: FailIcon,
  },
}

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

  // Group by band
  const nr3500 = metrics.filter((m) => m.type.includes('NR3500'))
  const nr700 = metrics.filter((m) => m.type.includes('NR700'))
  const other = metrics.filter((m) => !m.type.includes('NR3500') && !m.type.includes('NR700'))

  // Calculate summary
  const allStatuses = metrics.map((m) => {
    const config = SSV_CONFIG[m.type]
    return config ? config.getStatus(m.value) : 'pass'
  })
  const passCount = allStatuses.filter((s) => s === 'pass').length
  const warnCount = allStatuses.filter((s) => s === 'warning').length
  const failCount = allStatuses.filter((s) => s === 'fail').length

  const renderMetricChip = (m: MetricValue, idx: number) => {
    const config = SSV_CONFIG[m.type]
    if (!config) return null

    const status = config.getStatus(m.value)
    const styles = STATUS_STYLES[status]
    const Icon = styles.icon

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
            {config.label} {config.band && <span style={{ opacity: 0.7 }}>({config.band})</span>}
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
              {m.value.toFixed(1)}
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
              <PassIcon sx={{ fontSize: 14, color: '#16a34a' }} />
              <Typography sx={{ fontSize: '0.75rem', fontWeight: 600, color: '#16a34a' }}>
                {passCount} Pass
              </Typography>
            </Box>
          )}
          {warnCount > 0 && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              <WarnIcon sx={{ fontSize: 14, color: '#ea580c' }} />
              <Typography sx={{ fontSize: '0.75rem', fontWeight: 600, color: '#ea580c' }}>
                {warnCount} Warn
              </Typography>
            </Box>
          )}
          {failCount > 0 && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
              <FailIcon sx={{ fontSize: 14, color: '#dc2626' }} />
              <Typography sx={{ fontSize: '0.75rem', fontWeight: 600, color: '#dc2626' }}>
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
