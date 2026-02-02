import {
  Psychology as AIIcon,
  Warning as WarningIcon,
  Timeline as TimelineIcon,
  Refresh as RefreshIcon,
  RateReview as FeedbackIcon,
  School as LearnIcon,
  CheckCircle as CheckIcon,
  AutoFixHigh as AutoFixIcon,
} from '@mui/icons-material'
import {
  Box,
  Button,
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
import { useQuery, useQueryClient } from '@tanstack/react-query'
import ErrorDisplay from '../components/ErrorDisplay'
import FeedbackDialog from '../components/FeedbackDialog'
import { diagnosticsApi, DiagnosticSession } from '../services/api'
import { CSS_VARS, getConfidenceColor, POLLING_INTERVALS } from '../constants/designSystem'
import { formatTimestamp } from '../utils/statusHelpers'
import {
  DiagnosticLog,
  DiagnosticEvent,
  defaultDiagnosticLog,
  StatCard,
  StatusChip,
  SeverityChip,
  getProblemIcon,
} from '../components/DiagnosticComponents'

// Progress bar scaling constants
const PROGRESS_SCALE = {
  PATTERNS_PER_10_PERCENT: 10,   // Each pattern = 10% progress
  FEEDBACK_PER_5_PERCENT: 5,    // Each feedback = 5% progress
  MAX_PERCENT: 100,
} as const

// Threshold for pattern success classification
const SUCCESS_RATE_THRESHOLD_PERCENT = 70

export default function AIDiagnostics() {
  const [diagnosticData, setDiagnosticData] = useState<DiagnosticLog>(defaultDiagnosticLog)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [feedbackDialogOpen, setFeedbackDialogOpen] = useState(false)
  const [selectedSession, setSelectedSession] = useState<DiagnosticSession | null>(null)
  const queryClient = useQueryClient()

  // Fetch pending confirmations from the learning system
  const { data: pendingSessions = [], error: pendingError } = useQuery({
    queryKey: ['diagnostic-pending'],
    queryFn: async () => {
      const response = await diagnosticsApi.getPending()
      return response.data
    },
    refetchInterval: POLLING_INTERVALS.NORMAL,
  })

  // Fetch learning stats
  const { data: learningStats, error: learningError } = useQuery({
    queryKey: ['learning-stats'],
    queryFn: async () => {
      const response = await diagnosticsApi.getLearningStats()
      return response.data
    },
    refetchInterval: POLLING_INTERVALS.SLOW,
  })

  const handleOpenFeedback = (session: DiagnosticSession) => {
    setSelectedSession(session)
    setFeedbackDialogOpen(true)
  }

  const handleFeedbackSubmit = () => {
    queryClient.invalidateQueries({ queryKey: ['diagnostic-pending'] })
    queryClient.invalidateQueries({ queryKey: ['learning-stats'] })
  }

  // Fetch diagnostic data from the API (diagnostic sessions from monitoring service)
  const fetchDiagnosticData = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      // Get diagnostic sessions from the monitoring service
      const response = await diagnosticsApi.getAll()
      const sessions: DiagnosticSession[] = response.data

      // Transform sessions to DiagnosticLog format
      const events: DiagnosticEvent[] = sessions.map((session) => ({
        id: session.id,
        timestamp: session.createdAt,
        station_id: session.stationId,
        station_name: session.stationName || `Station ${session.stationId}`,
        problem_type: session.problemCode.split('_')[0],
        problem_code: session.problemCode,
        category: session.category || 'SYSTEM',
        severity: session.severity,
        problem_description: session.message,
        metric_value: 0, // Not tracked in DiagnosticSession
        threshold: 0, // Not tracked in DiagnosticSession
        ai_action: session.aiSolution?.action || 'Analyzing...',
        ai_commands: session.aiSolution?.commands || [],
        ai_confidence: session.aiSolution?.confidence || 0,
        remediation_type: session.aiSolution?.riskLevel || 'unknown',
        status: session.status,
        resolution_time: session.resolvedAt,
        notes: '',
        root_cause: session.aiSolution?.reasoning || '',
      }))

      const data: DiagnosticLog = {
        generated_at: new Date().toISOString(),
        stats: {
          total_checks: sessions.length,
          problems_detected: sessions.length,
          problems_diagnosed: sessions.filter(s => s.aiSolution).length,
          problems_resolved: sessions.filter(s => s.status === 'RESOLVED').length,
          failed_diagnoses: sessions.filter(s => s.status === 'FAILED').length,
        },
        events,
      }
      setDiagnosticData(data)
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to fetch diagnostic data'
      setError(errorMessage)
      setDiagnosticData(defaultDiagnosticLog)
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

  const queryError = pendingError || learningError
  if (queryError) {
    return <ErrorDisplay title="Failed to load diagnostics data" message={queryError.message} />
  }

  return (
    <Box sx={{ p: { xs: 2, sm: 2.5, md: 3 }, maxWidth: '1400px', margin: '0 auto' }}>
      {/* Header */}
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: { xs: 3, md: 4 }, flexWrap: 'wrap', gap: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: { xs: 1.5, sm: 2 } }}>
          <Box
            sx={{
              p: { xs: 1, sm: 1.5 },
              borderRadius: '12px',
              background: CSS_VARS.gradientAi,
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
        <Tooltip title="Refresh diagnostics">
          <IconButton
            onClick={handleRefresh}
            sx={{
              width: 40,
              height: 40,
              background: 'var(--surface-elevated)',
              border: '1px solid var(--mono-400)',
              borderRadius: '10px',
              color: 'var(--mono-700)',
              '&:hover': {
                background: 'var(--surface-hover)',
                borderColor: 'var(--mono-400)',
                color: 'var(--mono-900)',
              },
            }}
          >
            <RefreshIcon sx={{ fontSize: 20, animation: isLoading ? 'spin 1s linear infinite' : 'none', color: 'inherit' }} />
          </IconButton>
        </Tooltip>
      </Box>

      {/* Error Display */}
      {error && (
        <Card
          sx={{
            p: 2,
            mb: 3,
            backgroundColor: CSS_VARS.statusErrorBg,
            border: `1px solid ${CSS_VARS.statusErrorBorder}`,
            borderRadius: '12px',
          }}
        >
          <Typography sx={{ color: CSS_VARS.statusOffline, fontWeight: 500 }}>
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
            color={CSS_VARS.colorBlue500}
            bg={CSS_VARS.colorBlueBg}
          />
        </Grid>
        <Grid item xs={6} sm={6} md={6} lg={3}>
          <StatCard
            title="Problems Detected"
            value={stats.problems_detected}
            subtitle="Issues found"
            icon={<WarningIcon />}
            color={CSS_VARS.colorAmber500}
            bg={CSS_VARS.colorAmberBg}
          />
        </Grid>
        <Grid item xs={6} sm={6} md={6} lg={3}>
          <StatCard
            title="Resolved"
            value={stats.problems_resolved}
            subtitle="Auto-fixed issues"
            icon={<CheckIcon />}
            color={CSS_VARS.colorEmerald500}
            bg={CSS_VARS.colorEmeraldBg}
          />
        </Grid>
        <Grid item xs={6} sm={6} md={6} lg={3}>
          <StatCard
            title="Resolution Rate"
            value={`${resolutionRate}%`}
            subtitle="Success rate"
            icon={<AutoFixIcon />}
            color={CSS_VARS.colorPurple500}
            bg={CSS_VARS.colorPurpleBg}
          />
        </Grid>
      </Grid>

      {/* AI Confidence Gauge - only show when we have learning stats */}
      {learningStats && learningStats.totalFeedback > 0 && (
        <Card
          sx={{
            p: 3,
            mb: 4,
            background: 'var(--surface-elevated)',
            border: '1px solid var(--mono-400)',
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
                    Success Rate
                  </Typography>
                  <Typography variant="body2" sx={{ fontWeight: 600, color: 'var(--mono-950)' }}>
                    {learningStats.successRate.toFixed(1)}%
                  </Typography>
                </Box>
                <LinearProgress
                  variant="determinate"
                  value={learningStats.successRate}
                  sx={{
                    height: 8,
                    borderRadius: 4,
                    backgroundColor: CSS_VARS.mono200,
                    '& .MuiLinearProgress-bar': {
                      borderRadius: 4,
                      background: CSS_VARS.gradientAi,
                    },
                  }}
                />
              </Box>
            </Grid>
            <Grid item xs={12} md={4}>
              <Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                  <Typography variant="body2" sx={{ color: 'var(--mono-600)' }}>
                    Patterns Learned
                  </Typography>
                  <Typography variant="body2" sx={{ fontWeight: 600, color: 'var(--mono-950)' }}>
                    {learningStats.topPatterns?.length || 0}
                  </Typography>
                </Box>
                <LinearProgress
                  variant="determinate"
                  value={Math.min((learningStats.topPatterns?.length || 0) * PROGRESS_SCALE.PATTERNS_PER_10_PERCENT, PROGRESS_SCALE.MAX_PERCENT)}
                  sx={{
                    height: 8,
                    borderRadius: 4,
                    backgroundColor: CSS_VARS.mono200,
                    '& .MuiLinearProgress-bar': {
                      borderRadius: 4,
                      background: CSS_VARS.gradientSuccess,
                    },
                  }}
                />
              </Box>
            </Grid>
            <Grid item xs={12} md={4}>
              <Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
                  <Typography variant="body2" sx={{ color: 'var(--mono-600)' }}>
                    Total Feedback
                  </Typography>
                  <Typography variant="body2" sx={{ fontWeight: 600, color: 'var(--mono-950)' }}>
                    {learningStats.totalFeedback}
                  </Typography>
                </Box>
                <LinearProgress
                  variant="determinate"
                  value={Math.min(learningStats.totalFeedback * PROGRESS_SCALE.FEEDBACK_PER_5_PERCENT, PROGRESS_SCALE.MAX_PERCENT)}
                  sx={{
                    height: 8,
                    borderRadius: 4,
                    backgroundColor: CSS_VARS.mono200,
                    '& .MuiLinearProgress-bar': {
                      borderRadius: 4,
                      background: CSS_VARS.gradientAmber,
                    },
                  }}
                />
              </Box>
            </Grid>
          </Grid>
        </Card>
      )}

      {/* Recent Events Section */}
      <Card
        sx={{
          background: 'var(--surface-elevated)',
          border: '1px solid var(--mono-400)',
          borderRadius: '16px',
          overflow: 'hidden',
        }}
      >
        <Box sx={{ p: { xs: 2, sm: 3 }, borderBottom: '1px solid var(--mono-400)' }}>
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
                    border: '1px solid var(--mono-400)',
                    borderRadius: '12px',
                    transition: 'all 0.2s ease',
                    '&:hover': {
                      boxShadow: 'var(--shadow-sm)',
                      borderColor: 'var(--mono-400)',
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
                          {formatTimestamp(event.timestamp)}
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
                        backgroundColor: 'var(--surface-subtle)',
                        color: 'var(--mono-700)',
                        fontWeight: 500,
                        fontSize: '0.7rem',
                      }}
                    />
                  </Box>

                  {/* Metrics */}
                  <Box sx={{ mb: 2, p: 1.5, background: 'var(--surface-elevated)', borderRadius: '8px' }}>
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
                          backgroundColor: getConfidenceColor(event.ai_confidence),
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
              <TableRow sx={{ backgroundColor: 'var(--surface-subtle)' }}>
                <TableCell sx={{ fontWeight: 600, color: 'var(--mono-700)', borderBottom: '2px solid var(--mono-400)', verticalAlign: 'top' }}>Station</TableCell>
                <TableCell sx={{ fontWeight: 600, color: 'var(--mono-700)', borderBottom: '2px solid var(--mono-400)', verticalAlign: 'top' }}>Problem</TableCell>
                <TableCell sx={{ fontWeight: 600, color: 'var(--mono-700)', borderBottom: '2px solid var(--mono-400)', verticalAlign: 'top' }}>Severity</TableCell>
                <TableCell sx={{ fontWeight: 600, color: 'var(--mono-700)', borderBottom: '2px solid var(--mono-400)', verticalAlign: 'top' }}>AI Action</TableCell>
                <TableCell sx={{ fontWeight: 600, color: 'var(--mono-700)', borderBottom: '2px solid var(--mono-400)', verticalAlign: 'top' }}>Confidence</TableCell>
                <TableCell sx={{ fontWeight: 600, color: 'var(--mono-700)', borderBottom: '2px solid var(--mono-400)', verticalAlign: 'top' }}>Status</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {events.map((event) => (
                <TableRow
                  key={event.id}
                  sx={{
                    '&:hover': { backgroundColor: 'var(--surface-hover)' },
                    transition: 'background-color 0.15s ease',
                    '& td': { borderBottom: '1px solid var(--mono-400)', verticalAlign: 'top' },
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
                          {formatTimestamp(event.timestamp)}
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
                            backgroundColor: getConfidenceColor(event.ai_confidence),
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

      {/* Pending Confirmations Section */}
      {pendingSessions.length > 0 && (
        <Card
          sx={{
            mt: 4,
            background: CSS_VARS.colorVioletBg,
            border: `1px solid ${CSS_VARS.colorVioletBorder}`,
            borderRadius: '16px',
            overflow: 'hidden',
          }}
        >
          <Box sx={{ p: { xs: 2, sm: 3 }, borderBottom: `1px solid ${CSS_VARS.mono400}` }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Box
                sx={{
                  p: 1,
                  borderRadius: '10px',
                  background: CSS_VARS.gradientAi,
                  color: 'white',
                }}
              >
                <FeedbackIcon sx={{ fontSize: 20 }} />
              </Box>
              <Box>
                <Typography
                  variant="h6"
                  sx={{ fontWeight: 600, color: 'var(--mono-950)', fontSize: { xs: '1rem', sm: '1.25rem' } }}
                >
                  Pending Confirmations
                </Typography>
                <Typography variant="body2" sx={{ color: 'var(--mono-500)', fontSize: { xs: '0.8125rem', sm: '0.875rem' } }}>
                  Help the AI learn by confirming if solutions worked
                </Typography>
              </Box>
            </Box>
          </Box>

          <Box sx={{ p: 2 }}>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              {pendingSessions.map((session) => (
                <Box
                  key={session.id}
                  sx={{
                    p: 2,
                    background: 'var(--surface-base)',
                    border: '1px solid var(--mono-400)',
                    borderRadius: '12px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    flexWrap: 'wrap',
                    gap: 2,
                  }}
                >
                  <Box sx={{ flex: 1, minWidth: 200 }}>
                    <Typography sx={{ fontWeight: 600, color: 'var(--mono-950)' }}>
                      {session.problemCode.replaceAll('_', ' ')}
                    </Typography>
                    <Typography variant="body2" sx={{ color: 'var(--mono-500)', mt: 0.5 }}>
                      {session.stationName} - {session.message}
                    </Typography>
                    {session.aiSolution && (
                      <Typography variant="body2" sx={{ color: 'var(--mono-600)', mt: 1, fontStyle: 'italic' }}>
                        AI Action: {session.aiSolution.action}
                      </Typography>
                    )}
                  </Box>
                  <Button
                    variant="contained"
                    startIcon={<FeedbackIcon />}
                    onClick={() => handleOpenFeedback(session)}
                    sx={{
                      background: CSS_VARS.gradientAi,
                      color: 'white',
                      textTransform: 'none',
                      fontWeight: 600,
                      px: 3,
                      '&:hover': {
                        background: CSS_VARS.gradientAiHover,
                      },
                    }}
                  >
                    Confirm Solution
                  </Button>
                </Box>
              ))}
            </Box>
          </Box>
        </Card>
      )}

      {/* Learning Stats Section */}
      {learningStats && learningStats.totalFeedback > 0 && (
        <Card
          sx={{
            mt: 4,
            background: 'var(--surface-elevated)',
            border: '1px solid var(--mono-400)',
            borderRadius: '16px',
            p: { xs: 2, sm: 3 },
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3 }}>
            <Box
              sx={{
                p: 1,
                borderRadius: '10px',
                background: CSS_VARS.gradientSuccess,
                color: 'white',
              }}
            >
              <LearnIcon sx={{ fontSize: 20 }} />
            </Box>
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 600, color: 'var(--mono-950)' }}>
                AI Learning Progress
              </Typography>
              <Typography variant="body2" sx={{ color: 'var(--mono-500)' }}>
                The AI improves based on your feedback
              </Typography>
            </Box>
          </Box>

          <Grid container spacing={3}>
            <Grid item xs={6} sm={2.4}>
              <Box sx={{ textAlign: 'center' }}>
                <Typography variant="h4" sx={{ fontWeight: 700, color: 'var(--mono-950)' }}>
                  {learningStats.totalFeedback}
                </Typography>
                <Typography variant="body2" sx={{ color: 'var(--mono-500)' }}>
                  Total Feedback
                </Typography>
              </Box>
            </Grid>
            <Grid item xs={6} sm={2.4}>
              <Box sx={{ textAlign: 'center' }}>
                <Typography variant="h4" sx={{ fontWeight: 700, color: 'var(--status-active)' }}>
                  {learningStats.resolved}
                </Typography>
                <Typography variant="body2" sx={{ color: 'var(--mono-500)' }}>
                  Successful
                </Typography>
              </Box>
            </Grid>
            <Grid item xs={6} sm={2.4}>
              <Box sx={{ textAlign: 'center' }}>
                <Typography variant="h4" sx={{ fontWeight: 700, color: 'var(--status-offline)' }}>
                  {learningStats.failed}
                </Typography>
                <Typography variant="body2" sx={{ color: 'var(--mono-500)' }}>
                  Failed
                </Typography>
              </Box>
            </Grid>
            <Grid item xs={6} sm={2.4}>
              <Box sx={{ textAlign: 'center' }}>
                <Tooltip title="Solutions auto-applied due to high AI confidence (95%+)">
                  <Typography variant="h4" sx={{ fontWeight: 700, color: CSS_VARS.colorPurple500, cursor: 'help' }}>
                    {learningStats.autoApplied || 0}
                  </Typography>
                </Tooltip>
                <Typography variant="body2" sx={{ color: 'var(--mono-500)' }}>
                  Auto-Applied
                </Typography>
              </Box>
            </Grid>
            <Grid item xs={6} sm={2.4}>
              <Box sx={{ textAlign: 'center' }}>
                <Typography variant="h4" sx={{ fontWeight: 700, color: 'var(--color-violet-500)' }}>
                  {learningStats.successRate.toFixed(1)}%
                </Typography>
                <Typography variant="body2" sx={{ color: 'var(--mono-500)' }}>
                  Success Rate
                </Typography>
              </Box>
            </Grid>
          </Grid>

          {learningStats.topPatterns && learningStats.topPatterns.length > 0 && (
            <Box sx={{ mt: 3, pt: 3, borderTop: '1px solid var(--mono-400)' }}>
              <Typography variant="subtitle2" sx={{ color: 'var(--mono-600)', mb: 2 }}>
                Top Learned Patterns
              </Typography>
              <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                {learningStats.topPatterns.map((pattern) => (
                  <Chip
                    key={pattern.problemCode}
                    label={`${pattern.problemCode.replaceAll('_', ' ')} (${pattern.successRate.toFixed(0)}%)`}
                    size="small"
                    sx={{
                      backgroundColor: pattern.successRate >= SUCCESS_RATE_THRESHOLD_PERCENT ? CSS_VARS.colorEmeraldBg : CSS_VARS.colorAmberBg,
                      color: pattern.successRate >= SUCCESS_RATE_THRESHOLD_PERCENT ? CSS_VARS.colorEmerald600 : CSS_VARS.colorAmber600,
                      fontWeight: 600,
                    }}
                  />
                ))}
              </Box>
            </Box>
          )}
        </Card>
      )}

      {/* Feedback Dialog */}
      <FeedbackDialog
        open={feedbackDialogOpen}
        session={selectedSession}
        onClose={() => setFeedbackDialogOpen(false)}
        onSubmit={handleFeedbackSubmit}
      />

    </Box>
  )
}
