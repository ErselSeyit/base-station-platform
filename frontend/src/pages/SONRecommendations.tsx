import {
  AutoFixHigh as SONIcon,
  CheckCircle as ApproveIcon,
  Cancel as RejectIcon,
  Refresh as RefreshIcon,
  Undo as RollbackIcon,
  Speed as MLBIcon,
  SwapHoriz as MROIcon,
  CellTower as CCOIcon,
  BatteryChargingFull as ESIcon,
  Hub as ANRIcon,
  NetworkCheck as RAOIcon,
  SignalCellularAlt as ICICIcon,
  HourglassEmpty as PendingIcon,
  PlayArrow as ExecutingIcon,
  Done as ExecutedIcon,
  Error as FailedIcon,
  Block as RejectedIcon,
  History as ExpiredIcon,
} from '@mui/icons-material'
import {
  Box,
  Button,
  Card,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  IconButton,
  LinearProgress,
  Tab,
  Tabs,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import ErrorDisplay from '../components/ErrorDisplay'
import LoadingSpinner from '../components/LoadingSpinner'
import {
  WORKFLOW_STATUS_STYLES,
  CSS_VARS,
  RGBA,
  POLLING_INTERVALS,
  getConfidenceColor,
  getChipStyles,
  getIconBoxStyles,
  getActionButtonStyles,
} from '../constants/designSystem'
import { sonApi, SON_FUNCTION_NAMES } from '../services/api/son'
import { formatTimestamp } from '../utils/statusHelpers'
import { showToast } from '../utils/toast'
import type { SONRecommendation, SONFunction, SONStatus } from '../services/api/son'

// Function type icons
function getFunctionIcon(functionType: SONFunction) {
  const iconProps = { sx: { fontSize: 20 } }
  switch (functionType) {
    case 'MLB':
      return <MLBIcon {...iconProps} />
    case 'MRO':
      return <MROIcon {...iconProps} />
    case 'CCO':
      return <CCOIcon {...iconProps} />
    case 'ES':
      return <ESIcon {...iconProps} />
    case 'ANR':
      return <ANRIcon {...iconProps} />
    case 'RAO':
      return <RAOIcon {...iconProps} />
    case 'ICIC':
      return <ICICIcon {...iconProps} />
    default:
      return <SONIcon {...iconProps} />
  }
}

// Status icons for each workflow status
const STATUS_ICONS: Record<SONStatus, React.ReactElement> = {
  PENDING: <PendingIcon sx={{ fontSize: 14 }} />,
  APPROVED: <ApproveIcon sx={{ fontSize: 14 }} />,
  REJECTED: <RejectedIcon sx={{ fontSize: 14 }} />,
  EXECUTING: <ExecutingIcon sx={{ fontSize: 14 }} />,
  EXECUTED: <ExecutedIcon sx={{ fontSize: 14 }} />,
  FAILED: <FailedIcon sx={{ fontSize: 14 }} />,
  ROLLED_BACK: <RollbackIcon sx={{ fontSize: 14 }} />,
  EXPIRED: <ExpiredIcon sx={{ fontSize: 14 }} />,
}

// Status chip component - uses centralized design system
function StatusChip({ status }: Readonly<{ status: SONStatus }>) {
  const style = WORKFLOW_STATUS_STYLES[status]
  const icon = STATUS_ICONS[status]

  return (
    <Chip
      icon={icon}
      label={style.label}
      size="small"
      sx={getChipStyles(style)}
    />
  )
}

// Stat Card Component - uses centralized design system
function StatCard({ title, value, statusKey, icon }: Readonly<{
  title: string
  value: number | string
  statusKey: keyof typeof WORKFLOW_STATUS_STYLES
  icon: React.ReactNode
}>) {
  const style = WORKFLOW_STATUS_STYLES[statusKey]

  return (
    <Card
      sx={{
        p: { xs: 1.5, sm: 2, md: 2.5 },
        height: '100%',
        background: CSS_VARS.surfaceElevated,
        border: `1px solid ${CSS_VARS.mono400}`,
        borderRadius: { xs: '10px', sm: '12px' },
        transition: 'all 0.2s ease',
        '&:hover': {
          transform: 'translateY(-2px)',
          boxShadow: 'var(--shadow-md)',
        },
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1 }}>
        <Box>
          <Typography
            variant="overline"
            sx={{ color: CSS_VARS.mono500, fontSize: '0.6875rem', fontWeight: 600 }}
          >
            {title}
          </Typography>
          <Typography
            variant="h4"
            sx={{ fontWeight: 700, color: style.colorVar, fontSize: { xs: '1.5rem', sm: '1.75rem' } }}
          >
            {value}
          </Typography>
        </Box>
        <Box sx={getIconBoxStyles(style)}>
          {icon}
        </Box>
      </Box>
    </Card>
  )
}

// Custom Stat Card for non-workflow statuses (like Success Rate)
function CustomStatCard({ title, value, colorVar, bgColor, icon }: Readonly<{
  title: string
  value: number | string
  colorVar: string
  bgColor: string
  icon: React.ReactNode
}>) {
  return (
    <Card
      sx={{
        p: { xs: 1.5, sm: 2, md: 2.5 },
        height: '100%',
        background: CSS_VARS.surfaceElevated,
        border: `1px solid ${CSS_VARS.mono400}`,
        borderRadius: { xs: '10px', sm: '12px' },
        transition: 'all 0.2s ease',
        '&:hover': {
          transform: 'translateY(-2px)',
          boxShadow: 'var(--shadow-md)',
        },
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 1 }}>
        <Box>
          <Typography
            variant="overline"
            sx={{ color: CSS_VARS.mono500, fontSize: '0.6875rem', fontWeight: 600 }}
          >
            {title}
          </Typography>
          <Typography
            variant="h4"
            sx={{ fontWeight: 700, color: colorVar, fontSize: { xs: '1.5rem', sm: '1.75rem' } }}
          >
            {value}
          </Typography>
        </Box>
        <Box sx={{ p: 1, borderRadius: '8px', background: bgColor, color: colorVar, '& svg': { fontSize: 20 } }}>
          {icon}
        </Box>
      </Box>
    </Card>
  )
}

export default function SONRecommendations() {
  const [selectedTab, setSelectedTab] = useState(0)
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false)
  const [rollbackDialogOpen, setRollbackDialogOpen] = useState(false)
  const [selectedRecommendation, setSelectedRecommendation] = useState<SONRecommendation | null>(null)
  const [reason, setReason] = useState('')
  const queryClient = useQueryClient()

  // Queries
  const { data: stats, isLoading: statsLoading, error: statsError } = useQuery({
    queryKey: ['son-stats'],
    queryFn: async () => {
      const response = await sonApi.getStats()
      return response.data
    },
    refetchInterval: POLLING_INTERVALS.NORMAL,
  })

  const { data: pendingRecommendations = [], isLoading: pendingLoading, refetch, error: pendingError } = useQuery<SONRecommendation[]>({
    queryKey: ['son-pending'],
    queryFn: async () => {
      const response = await sonApi.getPending()
      return response.data
    },
    refetchInterval: POLLING_INTERVALS.STANDARD,
  })

  const { data: allRecommendations, error: allError } = useQuery<SONRecommendation[]>({
    queryKey: ['son-all'],
    queryFn: async () => {
      const response = await sonApi.getAll()
      return response.data.content
    },
    refetchInterval: POLLING_INTERVALS.NORMAL,
  })

  // Mutations
  const approveMutation = useMutation({
    mutationFn: (id: string) => sonApi.approve(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['son-pending'] })
      queryClient.invalidateQueries({ queryKey: ['son-stats'] })
      queryClient.invalidateQueries({ queryKey: ['son-all'] })
    },
    onError: (error: Error) => {
      showToast.error(`Failed to approve: ${error.message}`)
    },
  })

  const rejectMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => sonApi.reject(id, reason),
    onSuccess: () => {
      setRejectDialogOpen(false)
      setReason('')
      queryClient.invalidateQueries({ queryKey: ['son-pending'] })
      queryClient.invalidateQueries({ queryKey: ['son-stats'] })
      queryClient.invalidateQueries({ queryKey: ['son-all'] })
    },
    onError: (error: Error) => {
      showToast.error(`Failed to reject: ${error.message}`)
    },
  })

  const rollbackMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => sonApi.rollback(id, reason),
    onSuccess: () => {
      setRollbackDialogOpen(false)
      setReason('')
      queryClient.invalidateQueries({ queryKey: ['son-all'] })
      queryClient.invalidateQueries({ queryKey: ['son-stats'] })
    },
    onError: (error: Error) => {
      showToast.error(`Failed to rollback: ${error.message}`)
    },
  })

  const handleApprove = (rec: SONRecommendation) => {
    approveMutation.mutate(rec.id)
  }

  const handleRejectClick = (rec: SONRecommendation) => {
    setSelectedRecommendation(rec)
    setRejectDialogOpen(true)
  }

  const handleRejectConfirm = () => {
    if (selectedRecommendation && reason.trim()) {
      rejectMutation.mutate({ id: selectedRecommendation.id, reason: reason.trim() })
    }
  }

  const handleRollbackClick = (rec: SONRecommendation) => {
    setSelectedRecommendation(rec)
    setRollbackDialogOpen(true)
  }

  const handleRollbackConfirm = () => {
    if (selectedRecommendation && reason.trim()) {
      rollbackMutation.mutate({ id: selectedRecommendation.id, reason: reason.trim() })
    }
  }

  // Filter recommendations by tab - memoized to avoid recalculating on every render
  // Must be before early returns to comply with React hooks rules
  const displayedRecommendations = useMemo((): SONRecommendation[] => {
    if (!allRecommendations) return []
    switch (selectedTab) {
      case 0:
        return pendingRecommendations
      case 1:
        return allRecommendations.filter(r => r.status === 'EXECUTED')
      case 2:
        return allRecommendations.filter(r => r.status === 'FAILED' || r.status === 'REJECTED')
      default:
        return allRecommendations
    }
  }, [selectedTab, allRecommendations, pendingRecommendations])

  const isLoading = statsLoading || pendingLoading
  const error = statsError || pendingError || allError

  if (isLoading) {
    return <LoadingSpinner />
  }

  if (error) {
    return <ErrorDisplay title="Failed to load SON recommendations" message={error.message} />
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
              background: 'linear-gradient(135deg, var(--accent-primary) 0%, var(--color-blue-700) 100%)',
              color: 'white',
            }}
          >
            <SONIcon sx={{ fontSize: { xs: 24, sm: 28 } }} />
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
              SON Recommendations
            </Typography>
            <Typography variant="body2" sx={{ color: 'var(--mono-500)', fontSize: { xs: '0.8125rem', sm: '0.875rem' } }}>
              AI-powered network optimization with operator approval workflow
            </Typography>
          </Box>
        </Box>
        <Tooltip title="Refresh">
          <IconButton
            onClick={() => refetch()}
            sx={{
              width: 40,
              height: 40,
              background: 'var(--surface-elevated)',
              border: '1px solid var(--mono-400)',
              borderRadius: '10px',
              color: 'var(--mono-700)',
              '&:hover': {
                background: 'var(--surface-hover)',
              },
            }}
          >
            <RefreshIcon sx={{ fontSize: 20, animation: isLoading ? 'spin 1s linear infinite' : 'none' }} />
          </IconButton>
        </Tooltip>
      </Box>

      {/* Stats Cards */}
      <Grid container spacing={{ xs: 2, sm: 3 }} sx={{ mb: 4 }}>
        <Grid item xs={6} sm={4} md={2}>
          <StatCard
            title="Pending"
            value={stats?.pending ?? 0}
            statusKey="PENDING"
            icon={<PendingIcon />}
          />
        </Grid>
        <Grid item xs={6} sm={4} md={2}>
          <StatCard
            title="Approved"
            value={stats?.approved ?? 0}
            statusKey="APPROVED"
            icon={<ApproveIcon />}
          />
        </Grid>
        <Grid item xs={6} sm={4} md={2}>
          <StatCard
            title="Executed"
            value={stats?.executed ?? 0}
            statusKey="EXECUTED"
            icon={<ExecutedIcon />}
          />
        </Grid>
        <Grid item xs={6} sm={4} md={2}>
          <StatCard
            title="Failed"
            value={stats?.failed ?? 0}
            statusKey="FAILED"
            icon={<FailedIcon />}
          />
        </Grid>
        <Grid item xs={6} sm={4} md={2}>
          <StatCard
            title="Rejected"
            value={stats?.rejected ?? 0}
            statusKey="REJECTED"
            icon={<RejectedIcon />}
          />
        </Grid>
        <Grid item xs={6} sm={4} md={2}>
          <CustomStatCard
            title="Success Rate"
            value={stats?.successRate ?? '0%'}
            colorVar={CSS_VARS.accentInfo}
            bgColor={RGBA.cyanMedium}
            icon={<SONIcon />}
          />
        </Grid>
      </Grid>

      {/* Tabs */}
      <Card
        sx={{
          background: CSS_VARS.surfaceElevated,
          border: `1px solid ${CSS_VARS.mono400}`,
          borderRadius: '16px',
          overflow: 'hidden',
        }}
      >
        <Box sx={{ borderBottom: `1px solid ${CSS_VARS.mono400}` }}>
          <Tabs
            value={selectedTab}
            onChange={(_, newValue) => setSelectedTab(newValue)}
            sx={{
              px: 2,
              '& .MuiTab-root': {
                textTransform: 'none',
                fontWeight: 600,
                minWidth: 'auto',
                px: 3,
              },
            }}
          >
            <Tab label={`Pending (${pendingRecommendations.length})`} />
            <Tab label="Executed" />
            <Tab label="Failed/Rejected" />
            <Tab label="All" />
          </Tabs>
        </Box>

        {/* Recommendations Table */}
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow sx={{ backgroundColor: 'var(--surface-subtle)' }}>
                <TableCell sx={{ fontWeight: 600, color: 'var(--mono-700)' }}>Function</TableCell>
                <TableCell sx={{ fontWeight: 600, color: 'var(--mono-700)' }}>Station</TableCell>
                <TableCell sx={{ fontWeight: 600, color: 'var(--mono-700)' }}>Action</TableCell>
                <TableCell sx={{ fontWeight: 600, color: 'var(--mono-700)' }}>Confidence</TableCell>
                <TableCell sx={{ fontWeight: 600, color: 'var(--mono-700)' }}>Status</TableCell>
                <TableCell sx={{ fontWeight: 600, color: 'var(--mono-700)' }}>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {displayedRecommendations.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} sx={{ textAlign: 'center', py: 4 }}>
                    <Typography sx={{ color: 'var(--mono-500)' }}>
                      No recommendations found
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : (
                displayedRecommendations.map((rec) => (
                  <TableRow
                    key={rec.id}
                    sx={{
                      '&:hover': { backgroundColor: 'var(--surface-hover)' },
                      '& td': { borderBottom: '1px solid var(--mono-400)' },
                    }}
                  >
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                        <Box
                          sx={{
                            p: 0.75,
                            borderRadius: '8px',
                            background: 'var(--surface-subtle)',
                            color: 'var(--mono-700)',
                          }}
                        >
                          {getFunctionIcon(rec.functionType)}
                        </Box>
                        <Box>
                          <Typography variant="body2" sx={{ fontWeight: 600, color: 'var(--mono-950)' }}>
                            {rec.functionType}
                          </Typography>
                          <Typography variant="caption" sx={{ color: 'var(--mono-500)' }}>
                            {SON_FUNCTION_NAMES[rec.functionType]}
                          </Typography>
                        </Box>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 600, color: 'var(--mono-950)' }}>
                        Station {rec.stationId}
                      </Typography>
                      <Typography variant="caption" sx={{ color: 'var(--mono-500)' }}>
                        {formatTimestamp(rec.createdAt)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ color: 'var(--mono-800)', maxWidth: 250 }}>
                        {rec.actionType}
                      </Typography>
                      {rec.description && (
                        <Typography variant="caption" sx={{ color: 'var(--mono-500)', display: 'block', mt: 0.5 }}>
                          {rec.description}
                        </Typography>
                      )}
                      {rec.expectedImprovement && (
                        <Chip
                          label={`+${(rec.expectedImprovement * 100).toFixed(1)}% improvement`}
                          size="small"
                          sx={{
                            mt: 0.5,
                            backgroundColor: RGBA.activeMedium,
                            color: CSS_VARS.statusActive,
                            fontWeight: 600,
                            fontSize: '0.7rem',
                          }}
                        />
                      )}
                    </TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <LinearProgress
                          variant="determinate"
                          value={(rec.confidence ?? 0) * 100}
                          sx={{
                            width: 60,
                            height: 6,
                            borderRadius: 3,
                            backgroundColor: 'var(--mono-200)',
                            '& .MuiLinearProgress-bar': {
                              borderRadius: 3,
                              backgroundColor: getConfidenceColor(rec.confidence ?? 0),
                            },
                          }}
                        />
                        <Typography variant="caption" sx={{ fontWeight: 600, color: 'var(--mono-700)' }}>
                          {((rec.confidence ?? 0) * 100).toFixed(0)}%
                        </Typography>
                      </Box>
                    </TableCell>
                    <TableCell>
                      <StatusChip status={rec.status} />
                    </TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', gap: 1 }}>
                        {rec.status === 'PENDING' && (
                          <>
                            <Tooltip title="Approve">
                              <IconButton
                                size="small"
                                onClick={() => handleApprove(rec)}
                                disabled={approveMutation.isPending}
                                sx={getActionButtonStyles(WORKFLOW_STATUS_STYLES.EXECUTED)}
                              >
                                <ApproveIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="Reject">
                              <IconButton
                                size="small"
                                onClick={() => handleRejectClick(rec)}
                                sx={getActionButtonStyles(WORKFLOW_STATUS_STYLES.FAILED)}
                              >
                                <RejectIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          </>
                        )}
                        {rec.status === 'EXECUTED' && rec.rollbackAction && (
                          <Tooltip title="Rollback">
                            <IconButton
                              size="small"
                              onClick={() => handleRollbackClick(rec)}
                              sx={getActionButtonStyles(WORKFLOW_STATUS_STYLES.ROLLED_BACK)}
                            >
                              <RollbackIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        )}
                      </Box>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      {/* Reject Dialog */}
      <Dialog open={rejectDialogOpen} onClose={() => setRejectDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 600 }}>Reject Recommendation</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ mb: 2, color: 'var(--mono-600)' }}>
            Please provide a reason for rejecting this recommendation.
          </Typography>
          <TextField
            autoFocus
            fullWidth
            multiline
            rows={3}
            label="Rejection Reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Enter the reason for rejection..."
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setRejectDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            color="error"
            onClick={handleRejectConfirm}
            disabled={!reason.trim() || rejectMutation.isPending}
          >
            Reject
          </Button>
        </DialogActions>
      </Dialog>

      {/* Rollback Dialog */}
      <Dialog open={rollbackDialogOpen} onClose={() => setRollbackDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 600 }}>Rollback Changes</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ mb: 2, color: 'var(--mono-600)' }}>
            This will revert the changes made by this recommendation. Please provide a reason.
          </Typography>
          <TextField
            autoFocus
            fullWidth
            multiline
            rows={3}
            label="Rollback Reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Enter the reason for rollback..."
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setRollbackDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            sx={{ backgroundColor: CSS_VARS.statusMaintenance, '&:hover': { backgroundColor: CSS_VARS.accentWarning } }}
            onClick={handleRollbackConfirm}
            disabled={!reason.trim() || rollbackMutation.isPending}
          >
            Rollback
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
