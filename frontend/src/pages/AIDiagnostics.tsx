import {
  Psychology as AIIcon,
  CheckCircle as CheckIcon,
  Error as ErrorIcon,
  Warning as WarningIcon,
  AutoFixHigh as AutoFixIcon,
  Timeline as TimelineIcon,
  Speed as SpeedIcon,
  Memory as MemoryIcon,
  Thermostat as TempIcon,
  SignalCellularAlt as SignalIcon,
  Refresh as RefreshIcon,
} from '@mui/icons-material'
import {
  Box,
  Card,
  Chip,
  Grid,
  IconButton,
  LinearProgress,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material'
import { useState, useEffect, useCallback } from 'react'

// Types
interface DiagnosticEvent {
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

interface DiagnosticStats {
  total_checks: number
  problems_detected: number
  problems_diagnosed: number
  problems_resolved: number
  failed_diagnoses: number
}

interface DiagnosticLog {
  generated_at: string
  stats: DiagnosticStats
  events: DiagnosticEvent[]
}

// Default empty state
const defaultDiagnosticLog: DiagnosticLog = {
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

// Stat Card Component - responsive for all screen sizes
function StatCard({ title, value, subtitle, icon, color }: {
  title: string
  value: number | string
  subtitle: string
  icon: React.ReactNode
  color: string
}) {
  return (
    <Card
      sx={{
        p: { xs: 1.5, sm: 2, md: 2.5, lg: 3 },
        height: '100%',
        background: 'var(--surface-elevated)',
        border: '1px solid var(--surface-border)',
        borderRadius: { xs: '10px', sm: '12px', lg: '16px' },
        transition: 'all 0.2s ease',
        '&:hover': {
          transform: 'translateY(-2px)',
          boxShadow: '0 8px 24px rgba(0,0,0,0.08)',
        },
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1 }}>
        <Box sx={{ minWidth: 0, flex: 1, overflow: 'hidden' }}>
          <Typography
            variant="overline"
            sx={{
              color: 'var(--mono-500)',
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
              color: 'var(--mono-500)',
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
            background: `${color}15`,
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

// Problem Type Icon - with colors that work in dark mode
function getProblemIcon(type: string) {
  switch (type) {
    case 'TEMPERATURE':
      return <TempIcon sx={{ color: '#ef4444', fontSize: 20 }} />
    case 'CPU_USAGE':
      return <SpeedIcon sx={{ color: '#f59e0b', fontSize: 20 }} />
    case 'MEMORY_USAGE':
      return <MemoryIcon sx={{ color: '#f59e0b', fontSize: 20 }} />
    case 'SIGNAL_STRENGTH':
      return <SignalIcon sx={{ color: '#3b82f6', fontSize: 20 }} />
    default:
      return <WarningIcon sx={{ color: 'var(--mono-500)', fontSize: 20 }} />
  }
}

// Status Chip - with dark mode support
function StatusChip({ status }: { status: string }) {
  const configs: Record<string, { color: string; bg: string; darkBg: string; icon: React.ReactElement }> = {
    RESOLVED: { color: 'var(--status-active)', bg: 'rgba(22, 163, 74, 0.15)', darkBg: 'rgba(22, 163, 74, 0.25)', icon: <CheckIcon sx={{ fontSize: 14 }} /> },
    DIAGNOSED: { color: 'var(--status-maintenance)', bg: 'rgba(234, 88, 12, 0.15)', darkBg: 'rgba(234, 88, 12, 0.25)', icon: <AutoFixIcon sx={{ fontSize: 14 }} /> },
    DETECTED: { color: 'var(--status-offline)', bg: 'rgba(220, 38, 38, 0.15)', darkBg: 'rgba(220, 38, 38, 0.25)', icon: <ErrorIcon sx={{ fontSize: 14 }} /> },
    FAILED: { color: 'var(--mono-500)', bg: 'var(--mono-100)', darkBg: 'var(--mono-200)', icon: <ErrorIcon sx={{ fontSize: 14 }} /> },
  }
  const config = configs[status] || configs.FAILED

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
        border: `1px solid ${config.color}30`,
        '& .MuiChip-icon': { color: `${config.color} !important` },
      }}
    />
  )
}

// Severity Chip - with dark mode support
function SeverityChip({ severity }: { severity: string }) {
  const configs: Record<string, { color: string; bg: string }> = {
    CRITICAL: { color: 'var(--status-offline)', bg: 'rgba(220, 38, 38, 0.15)' },
    WARNING: { color: 'var(--status-maintenance)', bg: 'rgba(234, 88, 12, 0.15)' },
    INFO: { color: 'var(--mono-600)', bg: 'var(--mono-100)' },
  }
  const config = configs[severity] || configs.INFO

  return (
    <Chip
      label={severity}
      size="small"
      sx={{
        backgroundColor: config.bg,
        color: config.color,
        fontWeight: 600,
        fontSize: '0.7rem',
        border: `1px solid ${config.color}30`,
      }}
    />
  )
}

export default function AIDiagnostics() {
  const [diagnosticData, setDiagnosticData] = useState<DiagnosticLog>(defaultDiagnosticLog)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Fetch diagnostic data from the JSON file
  const fetchDiagnosticData = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const response = await fetch('/ai-diagnose-log.json')
      if (!response.ok) {
        throw new Error(`Failed to fetch: ${response.status}`)
      }
      const data: DiagnosticLog = await response.json()
      setDiagnosticData(data)
    } catch (err) {
      console.error('Error fetching diagnostic data:', err)
      setError(err instanceof Error ? err.message : 'Failed to load diagnostic data')
    } finally {
      setIsLoading(false)
    }
  }, [])

  // Initial fetch
  useEffect(() => {
    fetchDiagnosticData()
  }, [fetchDiagnosticData])

  // Refresh handler
  const handleRefresh = () => {
    fetchDiagnosticData()
  }

  const stats = diagnosticData.stats
  const events = diagnosticData.events

  const resolutionRate = stats.problems_detected > 0
    ? ((stats.problems_resolved / stats.problems_detected) * 100).toFixed(1)
    : '0'

  return (
    <Box sx={{ p: { xs: 2, sm: 2.5, md: 3 }, maxWidth: '1400px', margin: '0 auto' }}>
      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: { xs: 3, md: 4 }, flexWrap: 'wrap', gap: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: { xs: 1.5, sm: 2 } }}>
          <Box
            sx={{
              p: { xs: 1, sm: 1.5 },
              borderRadius: '12px',
              background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
              color: 'white',
            }}
          >
            <AIIcon sx={{ fontSize: { xs: 24, sm: 28 } }} />
          </Box>
          <Box>
            <Typography
              variant="h4"
              sx={{
                fontWeight: 700,
                color: 'var(--mono-950)',
                letterSpacing: '-0.02em',
                fontSize: { xs: '1.5rem', sm: '1.75rem', md: '2.125rem' },
              }}
            >
              AI Diagnostics
            </Typography>
            <Typography variant="body2" sx={{ color: 'var(--mono-500)', fontSize: { xs: '0.8125rem', sm: '0.875rem' } }}>
              Automated problem detection and resolution
            </Typography>
          </Box>
        </Box>
        <Tooltip title="Refresh data">
          <IconButton
            onClick={handleRefresh}
            sx={{
              background: 'var(--surface-elevated)',
              border: '1px solid var(--surface-border)',
              '&:hover': { background: 'var(--mono-100)' },
            }}
          >
            <RefreshIcon sx={{ animation: isLoading ? 'spin 1s linear infinite' : 'none' }} />
          </IconButton>
        </Tooltip>
      </Box>

      {/* Error Display */}
      {error && (
        <Card
          sx={{
            p: 2,
            mb: 3,
            backgroundColor: '#fee2e2',
            border: '1px solid #fca5a5',
            borderRadius: '12px',
          }}
        >
          <Typography sx={{ color: '#dc2626', fontWeight: 500 }}>
            {error}
          </Typography>
        </Card>
      )}

      {/* Stats Cards - use 2 columns at md, 4 columns at lg */}
      <Grid container spacing={{ xs: 2, sm: 3 }} sx={{ mb: 4 }}>
        <Grid item xs={6} sm={6} md={6} lg={3}>
          <StatCard
            title="Total Checks"
            value={stats.total_checks}
            subtitle="Monitoring cycles"
            icon={<TimelineIcon />}
            color="#3b82f6"
          />
        </Grid>
        <Grid item xs={6} sm={6} md={6} lg={3}>
          <StatCard
            title="Problems Detected"
            value={stats.problems_detected}
            subtitle="Issues found"
            icon={<WarningIcon />}
            color="#f59e0b"
          />
        </Grid>
        <Grid item xs={6} sm={6} md={6} lg={3}>
          <StatCard
            title="Resolved"
            value={stats.problems_resolved}
            subtitle="Auto-fixed issues"
            icon={<CheckIcon />}
            color="#10b981"
          />
        </Grid>
        <Grid item xs={6} sm={6} md={6} lg={3}>
          <StatCard
            title="Resolution Rate"
            value={`${resolutionRate}%`}
            subtitle="Success rate"
            icon={<AutoFixIcon />}
            color="#8b5cf6"
          />
        </Grid>
      </Grid>

      {/* AI Confidence Gauge */}
      <Card
        sx={{
          p: 3,
          mb: 4,
          background: 'var(--surface-elevated)',
          border: '1px solid var(--surface-border)',
          borderRadius: '16px',
        }}
      >
        <Typography
          variant="h6"
          sx={{ fontWeight: 600, mb: 3, color: 'var(--mono-950)' }}
        >
          AI System Performance
        </Typography>
        <Grid container spacing={4}>
          <Grid item xs={12} md={4}>
            <Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                <Typography variant="body2" sx={{ color: 'var(--mono-600)' }}>
                  Average Confidence
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 600, color: 'var(--mono-950)' }}>
                  78%
                </Typography>
              </Box>
              <LinearProgress
                variant="determinate"
                value={78}
                sx={{
                  height: 8,
                  borderRadius: 4,
                  backgroundColor: 'var(--mono-200)',
                  '& .MuiLinearProgress-bar': {
                    borderRadius: 4,
                    background: 'linear-gradient(90deg, #667eea 0%, #764ba2 100%)',
                  },
                }}
              />
            </Box>
          </Grid>
          <Grid item xs={12} md={4}>
            <Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                <Typography variant="body2" sx={{ color: 'var(--mono-600)' }}>
                  Diagnosis Accuracy
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 600, color: 'var(--mono-950)' }}>
                  91%
                </Typography>
              </Box>
              <LinearProgress
                variant="determinate"
                value={91}
                sx={{
                  height: 8,
                  borderRadius: 4,
                  backgroundColor: 'var(--mono-200)',
                  '& .MuiLinearProgress-bar': {
                    borderRadius: 4,
                    background: 'linear-gradient(90deg, #10b981 0%, #059669 100%)',
                  },
                }}
              />
            </Box>
          </Grid>
          <Grid item xs={12} md={4}>
            <Box>
              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                <Typography variant="body2" sx={{ color: 'var(--mono-600)' }}>
                  Response Time
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 600, color: 'var(--mono-950)' }}>
                  1.2s avg
                </Typography>
              </Box>
              <LinearProgress
                variant="determinate"
                value={85}
                sx={{
                  height: 8,
                  borderRadius: 4,
                  backgroundColor: 'var(--mono-200)',
                  '& .MuiLinearProgress-bar': {
                    borderRadius: 4,
                    background: 'linear-gradient(90deg, #f59e0b 0%, #d97706 100%)',
                  },
                }}
              />
            </Box>
          </Grid>
        </Grid>
      </Card>

      {/* Recent Events Section */}
      <Card
        sx={{
          background: 'var(--surface-elevated)',
          border: '1px solid var(--surface-border)',
          borderRadius: '16px',
          overflow: 'hidden',
        }}
      >
        <Box sx={{ p: { xs: 2, sm: 3 }, borderBottom: '1px solid var(--surface-border)' }}>
          <Typography
            variant="h6"
            sx={{ fontWeight: 600, color: 'var(--mono-950)', fontSize: { xs: '1rem', sm: '1.25rem' } }}
          >
            Recent Diagnostic Events
          </Typography>
          <Typography variant="body2" sx={{ color: 'var(--mono-500)', mt: 0.5, fontSize: { xs: '0.8125rem', sm: '0.875rem' } }}>
            Real-time AI-powered problem detection and resolution
          </Typography>
        </Box>

        {/* Mobile Card View */}
        <Box sx={{ display: { xs: 'block', md: 'none' }, p: 2 }}>
          {events.length === 0 ? (
            <Box sx={{ textAlign: 'center', py: 4 }}>
              <Typography sx={{ color: 'var(--mono-500)' }}>No diagnostic events</Typography>
            </Box>
          ) : (
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              {events.map((event) => (
                <Box
                  key={event.id}
                  sx={{
                    p: 2,
                    background: 'var(--surface-base)',
                    border: '1px solid var(--surface-border)',
                    borderRadius: '12px',
                    transition: 'all 0.2s ease',
                    '&:hover': {
                      boxShadow: 'var(--shadow-sm)',
                      borderColor: 'var(--mono-300)',
                    },
                  }}
                >
                  {/* Card Header */}
                  <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 2 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      {getProblemIcon(event.problem_type)}
                      <Box>
                        <Typography sx={{ fontWeight: 600, color: 'var(--mono-950)', fontSize: '0.9375rem' }}>
                          {event.station_name}
                        </Typography>
                        <Typography sx={{ color: 'var(--mono-500)', fontSize: '0.75rem' }}>
                          {new Date(event.timestamp).toLocaleString()}
                        </Typography>
                      </Box>
                    </Box>
                    <StatusChip status={event.status} />
                  </Box>

                  {/* Problem Details */}
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 2 }}>
                    <SeverityChip severity={event.severity} />
                    <Chip
                      label={event.problem_type.replace('_', ' ')}
                      size="small"
                      sx={{
                        backgroundColor: 'var(--mono-100)',
                        color: 'var(--mono-700)',
                        fontWeight: 500,
                        fontSize: '0.7rem',
                      }}
                    />
                  </Box>

                  {/* Metrics */}
                  <Box sx={{ mb: 2, p: 1.5, background: 'var(--mono-50)', borderRadius: '8px' }}>
                    <Typography sx={{ color: 'var(--mono-600)', fontSize: '0.75rem', mb: 0.5 }}>
                      Metric Value
                    </Typography>
                    <Typography sx={{ fontFamily: "'JetBrains Mono', monospace", color: 'var(--mono-950)', fontWeight: 600 }}>
                      {event.metric_value.toFixed(1)} / {event.threshold} threshold
                    </Typography>
                  </Box>

                  {/* AI Action */}
                  <Box sx={{ mb: 2 }}>
                    <Typography sx={{ color: 'var(--mono-600)', fontSize: '0.75rem', mb: 0.5 }}>
                      AI Action
                    </Typography>
                    <Typography sx={{ color: 'var(--mono-800)', fontSize: '0.8125rem' }}>
                      {event.ai_action}
                    </Typography>
                  </Box>

                  {/* Confidence */}
                  <Box>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                      <Typography sx={{ color: 'var(--mono-600)', fontSize: '0.75rem' }}>
                        AI Confidence
                      </Typography>
                      <Typography sx={{ fontWeight: 600, color: 'var(--mono-950)', fontSize: '0.75rem' }}>
                        {(event.ai_confidence * 100).toFixed(0)}%
                      </Typography>
                    </Box>
                    <LinearProgress
                      variant="determinate"
                      value={event.ai_confidence * 100}
                      sx={{
                        height: 6,
                        borderRadius: 3,
                        backgroundColor: 'var(--mono-200)',
                        '& .MuiLinearProgress-bar': {
                          borderRadius: 3,
                          backgroundColor: event.ai_confidence > 0.8 ? '#10b981' : event.ai_confidence > 0.6 ? '#f59e0b' : '#ef4444',
                        },
                      }}
                    />
                  </Box>
                </Box>
              ))}
            </Box>
          )}
        </Box>

        {/* Desktop Table View */}
        <TableContainer sx={{ display: { xs: 'none', md: 'block' } }}>
          <Table>
            <TableHead>
              <TableRow sx={{ backgroundColor: 'var(--mono-50)' }}>
                <TableCell sx={{ fontWeight: 600, color: 'var(--mono-700)' }}>Station</TableCell>
                <TableCell sx={{ fontWeight: 600, color: 'var(--mono-700)' }}>Problem</TableCell>
                <TableCell sx={{ fontWeight: 600, color: 'var(--mono-700)' }}>Severity</TableCell>
                <TableCell sx={{ fontWeight: 600, color: 'var(--mono-700)' }}>AI Action</TableCell>
                <TableCell sx={{ fontWeight: 600, color: 'var(--mono-700)' }}>Confidence</TableCell>
                <TableCell sx={{ fontWeight: 600, color: 'var(--mono-700)' }}>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {events.map((event) => (
                <TableRow
                  key={event.id}
                  sx={{
                    '&:hover': { backgroundColor: 'var(--mono-50)' },
                    transition: 'background-color 0.15s ease',
                  }}
                >
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      {getProblemIcon(event.problem_type)}
                      <Box>
                        <Typography variant="body2" sx={{ fontWeight: 600, color: 'var(--mono-950)' }}>
                          {event.station_name}
                        </Typography>
                        <Typography variant="caption" sx={{ color: 'var(--mono-500)' }}>
                          {new Date(event.timestamp).toLocaleTimeString()}
                        </Typography>
                      </Box>
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" sx={{ color: 'var(--mono-800)' }}>
                      {event.problem_type.replace('_', ' ')}
                    </Typography>
                    <Typography variant="caption" sx={{ color: 'var(--mono-500)' }}>
                      {event.metric_value.toFixed(1)} / {event.threshold}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <SeverityChip severity={event.severity} />
                  </TableCell>
                  <TableCell>
                    <Tooltip title={event.ai_commands.join(' && ')} arrow>
                      <Typography
                        variant="body2"
                        sx={{
                          color: 'var(--mono-700)',
                          maxWidth: 200,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                          cursor: 'pointer',
                        }}
                      >
                        {event.ai_action}
                      </Typography>
                    </Tooltip>
                  </TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <LinearProgress
                        variant="determinate"
                        value={event.ai_confidence * 100}
                        sx={{
                          width: 60,
                          height: 6,
                          borderRadius: 3,
                          backgroundColor: 'var(--mono-200)',
                          '& .MuiLinearProgress-bar': {
                            borderRadius: 3,
                            backgroundColor: event.ai_confidence > 0.8 ? '#10b981' : event.ai_confidence > 0.6 ? '#f59e0b' : '#ef4444',
                          },
                        }}
                      />
                      <Typography variant="caption" sx={{ fontWeight: 600, color: 'var(--mono-700)' }}>
                        {(event.ai_confidence * 100).toFixed(0)}%
                      </Typography>
                    </Box>
                  </TableCell>
                  <TableCell>
                    <StatusChip status={event.status} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {/* CSS for spin animation */}
      <style>{`
        @keyframes spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
      `}</style>
    </Box>
  )
}
