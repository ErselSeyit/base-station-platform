/**
 * Diagnostic Components
 *
 * Reusable components for the AI Diagnostics page.
 * Extracted to reduce file size and improve maintainability.
 */

import {
  CheckCircle as CheckIcon,
  Error as ErrorIcon,
  Warning as WarningIcon,
  AutoFixHigh as AutoFixIcon,
  Speed as SpeedIcon,
  Memory as MemoryIcon,
  Thermostat as TempIcon,
  SignalCellularAlt as SignalIcon,
} from '@mui/icons-material'
import { Box, Card, Chip, Typography } from '@mui/material'
import { CSS_VARS } from '../constants/designSystem'

// ============================================================================
// Types
// ============================================================================

export interface DiagnosticEvent {
  id: string
  timestamp: string
  station_id: number
  station_name: string
  problem_type: string
  problem_code: string
  category: string
  severity: string
  problem_description: string
  metric_value: number
  threshold: number
  ai_action: string
  ai_commands: string[]
  ai_confidence: number
  remediation_type: string
  status: string
  resolution_time?: string | null
  notes: string
  root_cause: string
}

export interface DiagnosticStats {
  total_checks: number
  problems_detected: number
  problems_diagnosed: number
  problems_resolved: number
  failed_diagnoses: number
}

export interface DiagnosticLog {
  generated_at: string
  stats: DiagnosticStats
  events: DiagnosticEvent[]
}

// ============================================================================
// Default State - exported alongside components for convenience
// ============================================================================

// eslint-disable-next-line react-refresh/only-export-components
export const defaultDiagnosticLog: DiagnosticLog = {
  generated_at: new Date().toISOString(),
  stats: {
    total_checks: 0,
    problems_detected: 0,
    problems_diagnosed: 0,
    problems_resolved: 0,
    failed_diagnoses: 0,
  },
  events: []
}

// ============================================================================
// Stat Card Component
// ============================================================================

export function StatCard({ title, value, subtitle, icon, color, bg }: Readonly<{
  title: string
  value: number | string
  subtitle: string
  icon: React.ReactNode
  color: string
  bg: string
}>) {
  return (
    <Card
      sx={{
        p: { xs: 1.5, sm: 2, md: 2.5, lg: 3 },
        height: '100%',
        background: CSS_VARS.surfaceElevated,
        border: `1px solid ${CSS_VARS.mono400}`,
        borderRadius: { xs: '10px', sm: '12px', lg: '16px' },
        transition: 'all 0.2s ease',
        '&:hover': {
          transform: 'translateY(-2px)',
          boxShadow: 'var(--shadow-lg)',
        },
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1 }}>
        <Box sx={{ minWidth: 0, flex: 1, overflow: 'hidden' }}>
          <Typography
            variant="overline"
            sx={{
              color: CSS_VARS.mono500,
              fontSize: { xs: '0.625rem', sm: '0.6875rem', lg: '0.75rem' },
              fontWeight: 600,
              lineHeight: 1.4,
              display: 'block',
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {title}
          </Typography>
          <Typography
            variant="h3"
            sx={{
              fontWeight: 700,
              color: color,
              fontSize: { xs: '1.25rem', sm: '1.5rem', md: '1.75rem', lg: '2rem' },
              letterSpacing: '-0.02em',
              mt: 0.5,
              lineHeight: 1.1,
            }}
          >
            {value}
          </Typography>
          <Typography
            variant="body2"
            sx={{
              color: CSS_VARS.mono500,
              mt: 0.5,
              fontSize: { xs: '0.6875rem', sm: '0.75rem', lg: '0.8125rem' },
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {subtitle}
          </Typography>
        </Box>
        <Box
          sx={{
            p: { xs: 0.75, sm: 1, lg: 1.5 },
            borderRadius: { xs: '6px', sm: '8px', lg: '12px' },
            background: bg,
            color: color,
            flexShrink: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            '& svg': {
              fontSize: { xs: 16, sm: 18, md: 20, lg: 24 },
            },
          }}
        >
          {icon}
        </Box>
      </Box>
    </Card>
  )
}

// ============================================================================
// Problem Type Icon - exported alongside components for convenience
// ============================================================================

// eslint-disable-next-line react-refresh/only-export-components
export function getProblemIcon(type: string) {
  switch (type) {
    case 'TEMPERATURE':
      return <TempIcon sx={{ color: CSS_VARS.colorRed500, fontSize: 20 }} />
    case 'CPU_USAGE':
      return <SpeedIcon sx={{ color: CSS_VARS.colorAmber500, fontSize: 20 }} />
    case 'MEMORY_USAGE':
      return <MemoryIcon sx={{ color: CSS_VARS.colorAmber500, fontSize: 20 }} />
    case 'SIGNAL_STRENGTH':
      return <SignalIcon sx={{ color: CSS_VARS.colorBlue500, fontSize: 20 }} />
    default:
      return <WarningIcon sx={{ color: CSS_VARS.mono500, fontSize: 20 }} />
  }
}

// ============================================================================
// Status Chip
// ============================================================================

const STATUS_CONFIGS: Record<string, { color: string; bg: string; border: string; icon: React.ReactElement }> = {
  RESOLVED: { color: CSS_VARS.statusActive, bg: CSS_VARS.statusActiveBg, border: CSS_VARS.statusActiveBorder, icon: <CheckIcon sx={{ fontSize: 14 }} /> },
  DIAGNOSED: { color: CSS_VARS.statusMaintenance, bg: CSS_VARS.statusWarningBg, border: CSS_VARS.statusWarningBorder, icon: <AutoFixIcon sx={{ fontSize: 14 }} /> },
  DETECTED: { color: CSS_VARS.statusOffline, bg: CSS_VARS.statusErrorBg, border: CSS_VARS.statusErrorBorder, icon: <ErrorIcon sx={{ fontSize: 14 }} /> },
  FAILED: { color: CSS_VARS.mono500, bg: CSS_VARS.mono100, border: CSS_VARS.mono400, icon: <ErrorIcon sx={{ fontSize: 14 }} /> },
}

export function StatusChip({ status }: Readonly<{ status: string }>) {
  const config = STATUS_CONFIGS[status] || STATUS_CONFIGS.FAILED

  return (
    <Chip
      icon={config.icon}
      label={status}
      size="small"
      sx={{
        backgroundColor: config.bg,
        color: config.color,
        fontWeight: 600,
        fontSize: '0.75rem',
        border: `1px solid ${config.border}`,
        '& .MuiChip-icon': { color: `${config.color} !important` },
      }}
    />
  )
}

// ============================================================================
// Severity Chip
// ============================================================================

const SEVERITY_CONFIGS: Record<string, { color: string; bg: string; border: string }> = {
  CRITICAL: { color: CSS_VARS.statusOffline, bg: CSS_VARS.statusErrorBg, border: CSS_VARS.statusErrorBorder },
  WARNING: { color: CSS_VARS.statusMaintenance, bg: CSS_VARS.statusWarningBg, border: CSS_VARS.statusWarningBorder },
  INFO: { color: CSS_VARS.mono600, bg: CSS_VARS.mono100, border: CSS_VARS.mono400 },
}

export function SeverityChip({ severity }: Readonly<{ severity: string }>) {
  const config = SEVERITY_CONFIGS[severity] || SEVERITY_CONFIGS.INFO

  return (
    <Chip
      label={severity}
      size="small"
      sx={{
        backgroundColor: config.bg,
        color: config.color,
        fontWeight: 600,
        fontSize: '0.7rem',
        border: `1px solid ${config.border}`,
      }}
    />
  )
}
