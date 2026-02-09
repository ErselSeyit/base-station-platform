import {
  Psychology as AIIcon,
  Warning as WarningIcon,
  Timeline as TimelineIcon,
  Refresh as RefreshIcon,
  CheckCircle as CheckIcon,
  AutoFixHigh as AutoFixIcon,
  ClearAll as ClearIcon,
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
import { lazy, Suspense, useMemo, useState, useEffect, useRef } from 'react'
import { useQuery, useQueryClient, useInfiniteQuery } from '@tanstack/react-query'
import CircularProgress from '@mui/material/CircularProgress'
import Button from '@mui/material/Button'
import Skeleton from '@mui/material/Skeleton'
import useMediaQuery from '@mui/material/useMediaQuery'
import ErrorDisplay from '../components/ErrorDisplay'

// Lazy load below-the-fold components to reduce initial bundle
const FeedbackDialog = lazy(() => import('../components/FeedbackDialog'))
const LearningStatsCard = lazy(() => import('../components/LearningStatsCard'))
const PendingConfirmationsCard = lazy(() => import('../components/PendingConfirmationsCard'))
import { diagnosticsApi, DiagnosticSession } from '../services/api'
import type { DiagnosticStatusFilter } from '../services/api/diagnostics'
import { CSS_VARS, getConfidenceColor, POLLING_INTERVALS } from '../constants/designSystem'
import { formatTimestamp, getErrorMessage } from '../utils/statusHelpers'
import {
  DiagnosticLog,
  DiagnosticEvent,
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

// Display limits for performance
const DISPLAY_LIMITS = {
  /** Page size for infinite scroll */
  PAGE_SIZE: 20,
  /** Cache duration for session data in milliseconds */
  SESSION_STALE_TIME_MS: 30_000,
  /** Cache duration for learning stats in milliseconds */
  LEARNING_STATS_STALE_TIME_MS: 60_000,
} as const

// Placeholder value indicating missing metric data (set by backend)
const MISSING_METRIC_PLACEHOLDER = -1

/**
 * Transform sessions to DiagnosticLog format.
 * Extracts metric values from metricsSnapshot for display.
 */
function transformSessionsToLog(sessions: DiagnosticSession[]): DiagnosticLog {
  const events: DiagnosticEvent[] = sessions.map((session) => {
    // Extract metric value and threshold from metricsSnapshot
    // metricsSnapshot format: { "CPU_USAGE": 95, "threshold": 75 }
    // Backend uses -1.0 as placeholder for missing values
    const snapshot = session.metricsSnapshot || {}
    const metricKeys = Object.keys(snapshot).filter(k => k !== 'threshold')
    const rawMetricValue = metricKeys.length > 0 ? snapshot[metricKeys[0]] : null
    // Keep null for missing values - display logic will show "N/A"
    const metricValue = rawMetricValue != null && rawMetricValue !== MISSING_METRIC_PLACEHOLDER
      ? rawMetricValue
      : null
    const threshold = snapshot.threshold ?? null

    return {
      id: session.id,
      timestamp: session.createdAt,
      station_id: session.stationId,
      station_name: session.stationName || `Station ${session.stationId}`,
      problem_type: session.problemCode,
      problem_code: session.problemCode,
      category: session.category || 'SYSTEM',
      severity: session.severity,
      problem_description: session.message,
      metric_value: metricValue,
      threshold: threshold,
      ai_action: session.aiSolution?.action || 'Analyzing...',
      ai_commands: session.aiSolution?.commands || [],
      ai_confidence: session.aiSolution?.confidence || 0,
      remediation_type: session.aiSolution?.riskLevel || 'unknown',
      status: session.status,
      resolution_time: session.resolvedAt,
      notes: '',
      root_cause: session.aiSolution?.reasoning || '',
    }
  })

  return {
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
}

// Status filter options
const STATUS_FILTERS: { value: DiagnosticStatusFilter | 'ALL'; label: string; color: string }[] = [
  { value: 'ALL', label: 'All', color: 'var(--mono-600)' },
  { value: 'RESOLVED', label: 'Resolved', color: 'var(--status-active)' },
  { value: 'FAILED', label: 'Failed', color: 'var(--status-offline)' },
  { value: 'PENDING_CONFIRMATION', label: 'Pending', color: 'var(--status-maintenance)' },
  { value: 'DIAGNOSED', label: 'Diagnosed', color: 'var(--status-info)' },
  { value: 'DETECTED', label: 'Detected', color: 'var(--mono-500)' },
]

export default function AIDiagnostics() {
  const [feedbackDialogOpen, setFeedbackDialogOpen] = useState(false)
  const [selectedSession, setSelectedSession] = useState<DiagnosticSession | null>(null)
  // Filter to hide old sessions from display (data remains in DB for AI learning)
  const [clearAfter, setClearAfter] = useState<string | null>(null)
  // Status filter for the list
  const [statusFilter, setStatusFilter] = useState<DiagnosticStatusFilter | 'ALL'>('ALL')
  const queryClient = useQueryClient()
  // Only render mobile OR desktop view, not both (reduces DOM nodes and re-renders)
  const isDesktop = useMediaQuery('(min-width: 900px)')

  const loadMoreRef = useRef<HTMLDivElement>(null)
  // Track if we've already tried fetching at the bottom (prevents repeated attempts)
  const hasFetchedAtBottom = useRef(false)

  // Fetch diagnostic sessions with infinite scroll pagination
  // NOTE: refetchInterval is disabled - incompatible with infinite scroll
  // Use manual refresh button instead
  const {
    data,
    isLoading,
    error: sessionsError,
    refetch,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery({
    queryKey: ['diagnostic-sessions-paged', statusFilter],
    queryFn: async ({ pageParam = 0 }) => {
      const status = statusFilter === 'ALL' ? undefined : statusFilter
      const response = await diagnosticsApi.getPaged(pageParam, DISPLAY_LIMITS.PAGE_SIZE, status)
      return response.data
    },
    getNextPageParam: (lastPage) => {
      // Only provide next page if there's actually more data
      if (lastPage.last || lastPage.content.length === 0) return undefined
      return lastPage.number + 1
    },
    initialPageParam: 0,
    staleTime: DISPLAY_LIMITS.SESSION_STALE_TIME_MS,
  })

  // Flatten all pages into a single array
  const sessions = useMemo(() => {
    if (!data?.pages) return []
    return data.pages.flatMap((page) => page.content)
  }, [data])

  // Get total count from first page
  const totalCount = data?.pages[0]?.totalElements ?? 0

  // Reset the "fetched at bottom" flag only when filter changes (not on data changes)
  useEffect(() => {
    hasFetchedAtBottom.current = false
  }, [statusFilter])

  // Reset fetch flag after successful page load (not on scroll)
  useEffect(() => {
    if (!isFetchingNextPage && hasNextPage) {
      // A fetch completed successfully and there's more - allow next fetch
      hasFetchedAtBottom.current = false
    }
  }, [data, isFetchingNextPage, hasNextPage])

  // Intersection observer for infinite scroll
  // Only attempts fetch ONCE when scrolled to bottom, stops completely when no more data
  useEffect(() => {
    // Don't set up observer if:
    // - Loading
    // - No more pages
    // - Clear View is active (all server data would be filtered out anyway)
    // - Currently fetching next page
    if (isLoading || !hasNextPage || clearAfter || isFetchingNextPage) return

    const observer = new IntersectionObserver(
      (entries) => {
        const isAtBottom = entries[0].isIntersecting

        // Only fetch if at bottom and haven't tried yet
        // Note: We DON'T reset the flag on scroll-away to prevent race conditions
        if (isAtBottom && !hasFetchedAtBottom.current) {
          hasFetchedAtBottom.current = true
          fetchNextPage()
        }
      },
      { threshold: 0.1 }
    )

    const currentRef = loadMoreRef.current
    if (currentRef) {
      observer.observe(currentRef)
    }

    return () => {
      if (currentRef) {
        observer.unobserve(currentRef)
      }
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage, isLoading, clearAfter])

  // Memoize filtered sessions (apply clearAfter filter)
  const displayedSessions = useMemo(() => {
    if (!clearAfter) return sessions
    return sessions.filter(s => new Date(s.createdAt) > new Date(clearAfter))
  }, [sessions, clearAfter])

  // Memoize transformation to prevent recalculation on every render
  const diagnosticData = useMemo(
    () => transformSessionsToLog(displayedSessions),
    [displayedSessions]
  )
  const error = sessionsError ? getErrorMessage(sessionsError) : null

  // Fetch pending confirmations from the learning system
  const { data: pendingSessions = [], error: pendingError } = useQuery({
    queryKey: ['diagnostic-pending'],
    queryFn: async () => {
      const response = await diagnosticsApi.getPending()
      return response.data
    },
    refetchInterval: POLLING_INTERVALS.NORMAL,
    staleTime: DISPLAY_LIMITS.SESSION_STALE_TIME_MS,
  })

  // Fetch learning stats (less critical, longer cache)
  const { data: learningStats, error: learningError } = useQuery({
    queryKey: ['learning-stats'],
    queryFn: async () => {
      const response = await diagnosticsApi.getLearningStats()
      return response.data
    },
    refetchInterval: POLLING_INTERVALS.SLOW,
    staleTime: DISPLAY_LIMITS.LEARNING_STATS_STALE_TIME_MS,
  })

  const handleOpenFeedback = (session: DiagnosticSession) => {
    setSelectedSession(session)
    setFeedbackDialogOpen(true)
  }

  const handleFeedbackSubmit = () => {
    queryClient.invalidateQueries({ queryKey: ['diagnostic-sessions'] })
    queryClient.invalidateQueries({ queryKey: ['diagnostic-pending'] })
    queryClient.invalidateQueries({ queryKey: ['learning-stats'] })
  }

  // Refresh handler
  const handleRefresh = () => {
    refetch()
  }

  // Clear view handler - hides old sessions from display (data stays in DB for AI learning)
  const handleClearView = () => {
    setClearAfter(new Date().toISOString())
  }

  const stats = diagnosticData.stats
  const events = diagnosticData.events

  // Memoize resolution rate calculation
  const resolutionRate = useMemo(() =>
    stats.problems_detected > 0
      ? ((stats.problems_resolved / stats.problems_detected) * 100).toFixed(1)
      : '0',
    [stats.problems_detected, stats.problems_resolved]
  )

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
              {isLoading
                ? 'Loading diagnostic sessions...'
                : `Automated problem detection · ${totalCount.toLocaleString()} total · ${sessions.length.toLocaleString()} loaded`}
            </Typography>
          </Box>
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Tooltip title="Clear view (data stays in DB for AI learning)">
            <IconButton
              onClick={handleClearView}
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
              <ClearIcon sx={{ fontSize: 20, color: 'inherit' }} />
            </IconButton>
          </Tooltip>
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
      </Box>

      {/* Status Filter Chips */}
      <Box sx={{ display: 'flex', gap: 1, mb: 3, flexWrap: 'wrap' }}>
        {STATUS_FILTERS.map((filter) => (
          <Chip
            key={filter.value}
            label={filter.label}
            onClick={() => setStatusFilter(filter.value)}
            sx={{
              fontWeight: 500,
              fontSize: '0.8125rem',
              backgroundColor: statusFilter === filter.value ? filter.color : 'var(--surface-elevated)',
              color: statusFilter === filter.value ? 'white' : 'var(--mono-700)',
              border: statusFilter === filter.value ? 'none' : '1px solid var(--mono-400)',
              '&:hover': {
                backgroundColor: statusFilter === filter.value ? filter.color : 'var(--surface-hover)',
              },
            }}
          />
        ))}
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

      {/* Learning Stats - lazy loaded */}
      <Box sx={{ mb: 4 }}>
        <Suspense fallback={<Skeleton variant="rectangular" height={200} sx={{ borderRadius: '16px' }} />}>
          {learningStats && <LearningStatsCard stats={learningStats} />}
        </Suspense>
      </Box>

      {/* Diagnostic Events Section */}
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
            Diagnostic Events
            {statusFilter !== 'ALL' && (
              <Chip
                label={STATUS_FILTERS.find(f => f.value === statusFilter)?.label}
                size="small"
                sx={{
                  ml: 1.5,
                  backgroundColor: STATUS_FILTERS.find(f => f.value === statusFilter)?.color,
                  color: 'white',
                  fontWeight: 500,
                  fontSize: '0.75rem',
                }}
              />
            )}
          </Typography>
          <Typography variant="body2" sx={{ color: 'var(--mono-500)', mt: 0.5, fontSize: { xs: '0.8125rem', sm: '0.875rem' } }}>
            AI-powered problem detection and resolution
          </Typography>
        </Box>

        {/* Mobile Card View - only rendered on mobile */}
        {!isDesktop && (
        <Box sx={{ p: 2 }}>
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
                      {event.metric_value != null ? `${event.metric_value.toFixed(1)} / ${event.threshold} threshold` : 'N/A'}
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
        )}

        {/* Desktop Table View - only rendered on desktop */}
        {isDesktop && (
        <TableContainer>
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
                      {event.metric_value != null ? `${event.metric_value.toFixed(1)} / ${event.threshold}` : 'N/A'}
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
        )}

        {/* Load more trigger for infinite scroll */}
        <Box
          ref={loadMoreRef}
          sx={{
            padding: '16px',
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            minHeight: '60px',
          }}
        >
          {isFetchingNextPage ? (
            <CircularProgress size={24} />
          ) : hasNextPage ? (
            <Button
              variant="text"
              onClick={() => fetchNextPage()}
              sx={{ color: 'var(--mono-600)' }}
            >
              Load more
            </Button>
          ) : events.length > 0 ? (
            <Typography sx={{ fontSize: '0.875rem', color: 'var(--mono-500)' }}>
              All {totalCount.toLocaleString()} diagnostic events loaded
            </Typography>
          ) : null}
        </Box>
      </Card>

      {/* Pending Confirmations - lazy loaded */}
      <Suspense fallback={<Skeleton variant="rectangular" height={200} sx={{ mt: 4, borderRadius: '16px' }} />}>
        <PendingConfirmationsCard
          sessions={pendingSessions}
          onConfirmClick={handleOpenFeedback}
        />
      </Suspense>

      {/* Feedback Dialog - lazy loaded */}
      <Suspense fallback={null}>
        <FeedbackDialog
          open={feedbackDialogOpen}
          session={selectedSession}
          onClose={() => setFeedbackDialogOpen(false)}
          onSubmit={handleFeedbackSubmit}
        />
      </Suspense>

    </Box>
  )
}
