import {
  FiveG as FiveGIcon,
  Speed as SpeedIcon,
  SignalCellularAlt as SignalIcon,
  NetworkCheck as QualityIcon,
  Timer as LatencyIcon,
  TrendingUp as TrendingUpIcon,
  TrendingDown as TrendingDownIcon,
  CellTower as CellIcon,
  Refresh as RefreshIcon,
  ExpandMore as ExpandIcon,
} from '@mui/icons-material'
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Button,
  Chip,
  Grid,
  LinearProgress,
  Paper,
  Tab,
  Tabs,
  Typography,
} from '@mui/material'
import { motion } from 'framer-motion'
import { useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import ErrorDisplay from '../components/ErrorDisplay'
import LoadingSpinner from '../components/LoadingSpinner'
import MetricsChart from '../components/MetricsChart'
import { CARD_STATUS_STYLES, CSS_VARS, POLLING_INTERVALS } from '../constants/designSystem'
import { metricsApi, stationApi } from '../services/api'
import { BaseStation, MetricData, StationStatus } from '../types'
import { ensureArray, avgNumbers, avg } from '../utils/arrayUtils'
import {
  type HealthStatus,
  getSignalStatus,
  getSinrStatus,
  getLatencyStatus,
  getThroughputStatus,
  getHealthRatioStatus,
  getWorstHealthStatus,
} from '../utils/metricEvaluators'
import { getErrorMessage } from '../utils/statusHelpers'

// ============================================================================
// Types
// ============================================================================

type BandType = 'n78' | 'n28' | 'all'
type TrendDirection = 'up' | 'down' | 'stable'

interface CellMetrics {
  stationId: number
  stationName: string
  location: string
  status: StationStatus
  n78: {
    dlThroughput: number
    ulThroughput: number
    rsrp: number
    sinr: number
  }
  n28: {
    dlThroughput: number
    ulThroughput: number
  }
  latency: number
  txImbalance: number
}

// ============================================================================
// Constants
// ============================================================================

const BAND_INFO = {
  n78: {
    name: 'n78 (3.5 GHz)',
    description: 'High-speed urban coverage',
    color: CSS_VARS.colorPurple500,
    bg: CSS_VARS.colorPurpleBg,
    maxDl: 2000,
    maxUl: 200,
  },
  n28: {
    name: 'n28 (700 MHz)',
    description: 'Extended coverage band',
    color: CSS_VARS.statusActive,
    bg: CSS_VARS.statusActiveBg,
    maxDl: 100,
    maxUl: 50,
  },
}

// Use CARD_STATUS_STYLES from designSystem.ts for dark mode compatibility

// ============================================================================
// Helper Functions
// ============================================================================

function processCellMetrics(
  stations: BaseStation[],
  metrics: MetricData[]
): CellMetrics[] {
  // Group metrics by station and type (immutable collection)
  const metricsByStation = metrics.reduce<Map<number, Map<string, number[]>>>(
    (acc, m) => {
      if (!acc.has(m.stationId)) {
        acc.set(m.stationId, new Map())
      }
      const stationMetrics = acc.get(m.stationId)!
      if (!stationMetrics.has(m.metricType)) {
        stationMetrics.set(m.metricType, [])
      }
      stationMetrics.get(m.metricType)!.push(m.value)
      return acc
    },
    new Map()
  )

  // Create CellMetrics immutably for each station
  return stations
    .filter((station): station is BaseStation & { id: number } => station.id !== undefined)
    .map((station) => {
      const stationMetrics = metricsByStation.get(station.id)
      return {
        stationId: station.id,
        stationName: station.stationName,
        location: station.location,
        status: station.status ?? StationStatus.OFFLINE,
        n78: {
          dlThroughput: avgNumbers(stationMetrics?.get('DL_THROUGHPUT_NR3500')),
          ulThroughput: avgNumbers(stationMetrics?.get('UL_THROUGHPUT_NR3500')),
          rsrp: avgNumbers(stationMetrics?.get('RSRP_NR3500'), -120),
          sinr: avgNumbers(stationMetrics?.get('SINR_NR3500')),
        },
        n28: {
          dlThroughput: avgNumbers(stationMetrics?.get('DL_THROUGHPUT_NR700')),
          ulThroughput: avgNumbers(stationMetrics?.get('UL_THROUGHPUT_NR700')),
        },
        latency: avgNumbers(stationMetrics?.get('LATENCY_PING')),
        txImbalance: avgNumbers(stationMetrics?.get('TX_IMBALANCE')),
      }
    })
}

// ============================================================================
// Components
// ============================================================================

interface BandSummaryCardProps {
  band: 'n78' | 'n28'
  avgDl: number
  avgUl: number
  cellCount: number
  healthyCells: number
}

function BandSummaryCard({ band, avgDl, avgUl, cellCount, healthyCells }: Readonly<BandSummaryCardProps>) {
  const info = BAND_INFO[band]
  const healthRatio = cellCount > 0 ? healthyCells / cellCount : 0
  const status: HealthStatus = getHealthRatioStatus(healthRatio)
  const styles = CARD_STATUS_STYLES[status]

  return (
    <Paper
      component={motion.div}
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      sx={{
        p: 3,
        borderRadius: 3,
        background: 'var(--surface-base)',
        border: '1px solid var(--surface-border)',
        '&:hover': { boxShadow: `0 8px 24px var(--surface-border)` },
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
        <Box
          sx={{
            width: 48,
            height: 48,
            borderRadius: 2,
            background: info.bg,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <FiveGIcon sx={{ color: info.color, fontSize: 28 }} />
        </Box>
        <Box>
          <Typography sx={{ fontWeight: 700, fontSize: '1.125rem', color: 'var(--mono-950)' }}>
            {info.name}
          </Typography>
          <Typography sx={{ color: 'var(--mono-500)', fontSize: '0.875rem' }}>
            {info.description}
          </Typography>
        </Box>
      </Box>

      <Grid container spacing={2}>
        <Grid item xs={6}>
          <Typography sx={{ color: 'var(--mono-500)', fontSize: '0.75rem', mb: 0.5 }}>
            Avg Download
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.5 }}>
            <Typography sx={{ fontFamily: "'JetBrains Mono'", fontWeight: 700, fontSize: '1.5rem', color: 'var(--mono-950)' }}>
              {avgDl.toFixed(0)}
            </Typography>
            <Typography sx={{ color: 'var(--mono-400)', fontSize: '0.875rem' }}>Mbps</Typography>
          </Box>
        </Grid>
        <Grid item xs={6}>
          <Typography sx={{ color: 'var(--mono-500)', fontSize: '0.75rem', mb: 0.5 }}>
            Avg Upload
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.5 }}>
            <Typography sx={{ fontFamily: "'JetBrains Mono'", fontWeight: 700, fontSize: '1.5rem', color: 'var(--mono-950)' }}>
              {avgUl.toFixed(0)}
            </Typography>
            <Typography sx={{ color: 'var(--mono-400)', fontSize: '0.875rem' }}>Mbps</Typography>
          </Box>
        </Grid>
      </Grid>

      <Box sx={{ mt: 2, pt: 2, borderTop: '1px solid var(--surface-border)' }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
          <Typography sx={{ fontSize: '0.75rem', color: 'var(--mono-500)' }}>
            Cell Health
          </Typography>
          <Typography sx={{ fontSize: '0.75rem', fontWeight: 600, color: styles.color }}>
            {healthyCells}/{cellCount} healthy
          </Typography>
        </Box>
        <LinearProgress
          variant="determinate"
          value={healthRatio * 100}
          sx={{
            height: 6,
            borderRadius: 3,
            backgroundColor: 'var(--surface-subtle)',
            '& .MuiLinearProgress-bar': { backgroundColor: styles.color, borderRadius: 3 },
          }}
        />
      </Box>
    </Paper>
  )
}

interface MetricGaugeProps {
  label: string
  value: number
  unit: string
  status: HealthStatus
  icon: React.ElementType
  trend?: TrendDirection
}

function getTrendIcon(trend: TrendDirection) {
  if (trend === 'up') return <TrendingUpIcon />
  if (trend === 'down') return <TrendingDownIcon />
  return undefined
}

function getTrendColors(trend: TrendDirection) {
  if (trend === 'up') return { bg: CSS_VARS.statusActiveBg, color: CSS_VARS.statusActive }
  if (trend === 'down') return { bg: CSS_VARS.statusErrorBg, color: CSS_VARS.statusOffline }
  return { bg: CSS_VARS.statusInfoBg, color: CSS_VARS.statusInfo }
}

function MetricGauge({ label, value, unit, status, icon: Icon, trend }: Readonly<MetricGaugeProps>) {
  const styles = CARD_STATUS_STYLES[status]
  const trendColors = trend ? getTrendColors(trend) : null

  return (
    <Box
      sx={{
        p: 2,
        borderRadius: 2,
        background: styles.bg,
        border: `1px solid ${styles.border}`,
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Icon sx={{ fontSize: 18, color: styles.color }} />
          <Typography sx={{ fontSize: '0.8125rem', fontWeight: 600, color: 'var(--mono-600)' }}>
            {label}
          </Typography>
        </Box>
        {trend && trendColors && (
          <Chip
            size="small"
            icon={getTrendIcon(trend)}
            label={trend}
            sx={{
              height: 20,
              fontSize: '0.625rem',
              background: trendColors.bg,
              color: trendColors.color,
              '& .MuiChip-icon': { fontSize: 14 },
            }}
          />
        )}
      </Box>
      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.5 }}>
        <Typography sx={{ fontFamily: "'JetBrains Mono'", fontWeight: 800, fontSize: '1.75rem', color: styles.color }}>
          {typeof value === 'number' ? value.toFixed(1) : value}
        </Typography>
        <Typography sx={{ fontSize: '0.875rem', color: 'var(--mono-400)' }}>{unit}</Typography>
      </Box>
    </Box>
  )
}

interface CellRowProps {
  cell: CellMetrics
  expanded: boolean
  onToggle: () => void
}

function CellRow({ cell, expanded, onToggle }: Readonly<CellRowProps>) {
  const rsrpStatus = getSignalStatus(cell.n78.rsrp)
  const sinrStatus = getSinrStatus(cell.n78.sinr)
  const latencyStatus = getLatencyStatus(cell.latency)
  const overallStatus = getWorstHealthStatus([rsrpStatus, sinrStatus, latencyStatus])
  const styles = CARD_STATUS_STYLES[overallStatus]

  return (
    <Accordion
      expanded={expanded}
      onChange={onToggle}
      sx={{
        background: 'var(--surface-base)',
        border: '1px solid var(--surface-border)',
        borderRadius: '12px !important',
        mb: 1,
        '&:before': { display: 'none' },
        '&.Mui-expanded': { margin: '0 0 8px 0' },
      }}
    >
      <AccordionSummary expandIcon={<ExpandIcon />} sx={{ px: 2.5 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flex: 1 }}>
          <Box
            sx={{
              width: 40,
              height: 40,
              borderRadius: 2,
              background: styles.bg,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <CellIcon sx={{ color: styles.color }} />
          </Box>
          <Box sx={{ flex: 1 }}>
            <Typography sx={{ fontWeight: 600, color: 'var(--mono-950)' }}>{cell.stationName}</Typography>
            <Typography sx={{ fontSize: '0.8125rem', color: 'var(--mono-500)' }}>
              {cell.location}
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', gap: 2, mr: 2 }}>
            <Box sx={{ textAlign: 'right' }}>
              <Typography sx={{ fontSize: '0.6875rem', color: 'var(--mono-400)' }}>n78 DL</Typography>
              <Typography sx={{ fontFamily: "'JetBrains Mono'", fontWeight: 600, color: 'var(--mono-950)' }}>
                {cell.n78.dlThroughput.toFixed(0)} Mbps
              </Typography>
            </Box>
            <Box sx={{ textAlign: 'right' }}>
              <Typography sx={{ fontSize: '0.6875rem', color: 'var(--mono-400)' }}>RSRP</Typography>
              <Typography
                sx={{ fontFamily: "'JetBrains Mono'", fontWeight: 600, color: CARD_STATUS_STYLES[rsrpStatus].color }}
              >
                {cell.n78.rsrp.toFixed(0)} dBm
              </Typography>
            </Box>
            <Box sx={{ textAlign: 'right' }}>
              <Typography sx={{ fontSize: '0.6875rem', color: 'var(--mono-400)' }}>Latency</Typography>
              <Typography
                sx={{ fontFamily: "'JetBrains Mono'", fontWeight: 600, color: CARD_STATUS_STYLES[latencyStatus].color }}
              >
                {cell.latency.toFixed(1)} ms
              </Typography>
            </Box>
          </Box>
          <Chip
            size="small"
            label={cell.status}
            sx={{
              background: cell.status === 'ACTIVE' ? CSS_VARS.statusActiveBg : CSS_VARS.statusErrorBg,
              color: cell.status === 'ACTIVE' ? CSS_VARS.statusActive : CSS_VARS.statusOffline,
              fontWeight: 600,
              fontSize: '0.6875rem',
            }}
          />
        </Box>
      </AccordionSummary>
      <AccordionDetails sx={{ px: 2.5, pb: 2.5 }}>
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <Typography sx={{ fontWeight: 600, mb: 1.5, fontSize: '0.875rem', color: 'var(--mono-950)' }}>n78 Band (3.5 GHz)</Typography>
            <Grid container spacing={1.5}>
              <Grid item xs={6}>
                <MetricGauge
                  label="Download"
                  value={cell.n78.dlThroughput}
                  unit="Mbps"
                  status={getThroughputStatus(cell.n78.dlThroughput, BAND_INFO.n78.maxDl)}
                  icon={SpeedIcon}
                />
              </Grid>
              <Grid item xs={6}>
                <MetricGauge
                  label="Upload"
                  value={cell.n78.ulThroughput}
                  unit="Mbps"
                  status={getThroughputStatus(cell.n78.ulThroughput, BAND_INFO.n78.maxUl)}
                  icon={SpeedIcon}
                />
              </Grid>
              <Grid item xs={6}>
                <MetricGauge label="RSRP" value={cell.n78.rsrp} unit="dBm" status={rsrpStatus} icon={SignalIcon} />
              </Grid>
              <Grid item xs={6}>
                <MetricGauge label="SINR" value={cell.n78.sinr} unit="dB" status={sinrStatus} icon={QualityIcon} />
              </Grid>
            </Grid>
          </Grid>
          <Grid item xs={12} md={6}>
            <Typography sx={{ fontWeight: 600, mb: 1.5, fontSize: '0.875rem', color: 'var(--mono-950)' }}>n28 Band (700 MHz)</Typography>
            <Grid container spacing={1.5}>
              <Grid item xs={6}>
                <MetricGauge
                  label="Download"
                  value={cell.n28.dlThroughput}
                  unit="Mbps"
                  status={getThroughputStatus(cell.n28.dlThroughput, BAND_INFO.n28.maxDl)}
                  icon={SpeedIcon}
                />
              </Grid>
              <Grid item xs={6}>
                <MetricGauge
                  label="Upload"
                  value={cell.n28.ulThroughput}
                  unit="Mbps"
                  status={getThroughputStatus(cell.n28.ulThroughput, BAND_INFO.n28.maxUl)}
                  icon={SpeedIcon}
                />
              </Grid>
              <Grid item xs={6}>
                <MetricGauge label="Latency" value={cell.latency} unit="ms" status={latencyStatus} icon={LatencyIcon} />
              </Grid>
              <Grid item xs={6}>
                <MetricGauge
                  label="TX Imbalance"
                  value={Math.abs(cell.txImbalance)}
                  unit="dB"
                  status={Math.abs(cell.txImbalance) <= 4 ? 'healthy' : 'warning'}
                  icon={QualityIcon}
                />
              </Grid>
            </Grid>
          </Grid>
        </Grid>
      </AccordionDetails>
    </Accordion>
  )
}

// ============================================================================
// Main Component
// ============================================================================

export default function FiveGDashboard() {
  const [selectedBand, setSelectedBand] = useState<BandType>('all')
  const [expandedCell, setExpandedCell] = useState<number | null>(null)

  // Fetch stations
  const { data: stationsData, isLoading: stationsLoading, error: stationsError } = useQuery({
    queryKey: ['stations'],
    queryFn: async () => {
      const response = await stationApi.getAll()
      return response.data
    },
  })

  // Fetch metrics
  const {
    data: metricsData,
    isLoading: metricsLoading,
    error: metricsError,
    refetch,
  } = useQuery({
    queryKey: ['5g-metrics'],
    queryFn: async () => {
      const response = await metricsApi.getAll({
        startTime: new Date(Date.now() - 3600000).toISOString(),
      })
      return response.data
    },
    refetchInterval: POLLING_INTERVALS.NORMAL,
  })

  // Process cell metrics
  const cellMetrics = useMemo(() => {
    const stations = ensureArray(stationsData as BaseStation[])
    const metrics = ensureArray(metricsData as MetricData[])
    return processCellMetrics(stations, metrics)
  }, [stationsData, metricsData])

  // Memoize active cells filter - used in multiple places
  const activeCells = useMemo(
    () => cellMetrics.filter(c => c.status === StationStatus.ACTIVE),
    [cellMetrics]
  )

  // Calculate band summaries
  const bandSummary = useMemo(() => {
    const n78Healthy = activeCells.filter(c => getSignalStatus(c.n78.rsrp) === 'healthy').length
    const n28Healthy = activeCells.filter(
      c => getThroughputStatus(c.n28.dlThroughput, BAND_INFO.n28.maxDl) === 'healthy'
    ).length

    return {
      n78: {
        avgDl: avg(activeCells, c => c.n78.dlThroughput),
        avgUl: avg(activeCells, c => c.n78.ulThroughput),
        cellCount: activeCells.length,
        healthyCells: n78Healthy,
      },
      n28: {
        avgDl: avg(activeCells, c => c.n28.dlThroughput),
        avgUl: avg(activeCells, c => c.n28.ulThroughput),
        cellCount: activeCells.length,
        healthyCells: n28Healthy,
      },
    }
  }, [activeCells])

  // Network-wide averages
  const networkAvg = useMemo(() => ({
    rsrp: avg(activeCells, c => c.n78.rsrp, -120),
    sinr: avg(activeCells, c => c.n78.sinr, 0),
    latency: avg(activeCells, c => c.latency, 0),
  }), [activeCells])

  if (stationsLoading || metricsLoading) {
    return <LoadingSpinner />
  }

  const error = stationsError || metricsError
  if (error) {
    return <ErrorDisplay title="Failed to load 5G network data" message={getErrorMessage(error)} />
  }

  return (
    <Box sx={{ maxWidth: 1600, mx: 'auto', p: { xs: 2, sm: 3, md: 4 } }}>
      {/* Header */}
      <Box
        component={motion.div}
        initial={{ opacity: 0, y: -12 }}
        animate={{ opacity: 1, y: 0 }}
        sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}
      >
        <Box>
          <Typography variant="h1" sx={{ fontSize: { xs: '1.5rem', md: '2rem' }, fontWeight: 800, color: 'var(--mono-950)' }}>
            5G Network Dashboard
          </Typography>
          <Typography sx={{ color: 'var(--mono-500)', mt: 0.5 }}>
            Real-time NR band performance and cell metrics
          </Typography>
        </Box>
        <Button
          aria-label="Refresh 5G metrics data"
          startIcon={<RefreshIcon />}
          onClick={() => refetch()}
          sx={{
            background: 'var(--surface-subtle)',
            color: 'var(--mono-700)',
            '&:hover': { background: 'var(--surface-hover)' },
          }}
        >
          Refresh
        </Button>
      </Box>

      {/* Band Summary Cards */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid item xs={12} md={6}>
          <BandSummaryCard band="n78" {...bandSummary.n78} />
        </Grid>
        <Grid item xs={12} md={6}>
          <BandSummaryCard band="n28" {...bandSummary.n28} />
        </Grid>
      </Grid>

      {/* Network-wide Metrics */}
      <Paper sx={{ p: 3, borderRadius: 3, mb: 4, background: 'var(--surface-base)', border: '1px solid var(--surface-border)' }}>
        <Typography sx={{ fontWeight: 700, mb: 2, color: 'var(--mono-950)' }}>Network-wide Performance</Typography>
        <Grid container spacing={2}>
          <Grid item xs={6} md={3}>
            <MetricGauge
              label="Avg Signal Power"
              value={networkAvg.rsrp}
              unit="dBm"
              status={getSignalStatus(networkAvg.rsrp)}
              icon={SignalIcon}
            />
          </Grid>
          <Grid item xs={6} md={3}>
            <MetricGauge
              label="Avg Signal Quality"
              value={networkAvg.sinr}
              unit="dB"
              status={getSinrStatus(networkAvg.sinr)}
              icon={QualityIcon}
            />
          </Grid>
          <Grid item xs={6} md={3}>
            <MetricGauge
              label="Avg Latency"
              value={networkAvg.latency}
              unit="ms"
              status={getLatencyStatus(networkAvg.latency)}
              icon={LatencyIcon}
            />
          </Grid>
          <Grid item xs={6} md={3}>
            <MetricGauge
              label="Active Cells"
              value={activeCells.length}
              unit={`/ ${cellMetrics.length}`}
              status="healthy"
              icon={CellIcon}
            />
          </Grid>
        </Grid>
      </Paper>

      {/* Performance Trends */}
      <Paper sx={{ p: 3, borderRadius: 3, mb: 4, background: 'var(--surface-base)', border: '1px solid var(--surface-border)' }}>
        <Typography sx={{ fontWeight: 700, mb: 2, color: 'var(--mono-950)' }}>Performance Trends</Typography>
        <MetricsChart />
      </Paper>

      {/* Cell-level Details */}
      <Paper sx={{ p: 3, borderRadius: 3, background: 'var(--surface-base)', border: '1px solid var(--surface-border)' }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
          <Typography sx={{ fontWeight: 700, color: 'var(--mono-950)' }}>Cell-level Metrics</Typography>
          <Tabs
            aria-label="Filter cells by frequency band"
            value={selectedBand}
            onChange={(_, v: BandType) => setSelectedBand(v)}
            sx={{ minHeight: 36, '& .MuiTab-root': { minHeight: 36, py: 0.5 } }}
          >
            <Tab value="all" label="All Bands" />
            <Tab value="n78" label="n78 Only" />
            <Tab value="n28" label="n28 Only" />
          </Tabs>
        </Box>

        {cellMetrics
          .filter(c => c.status === StationStatus.ACTIVE || c.status === StationStatus.MAINTENANCE)
          .filter(c => {
            // Filter by selected band - show cells with meaningful data for that band
            if (selectedBand === 'all') return true
            if (selectedBand === 'n78') return c.n78.dlThroughput > 0 || c.n78.rsrp > -120
            if (selectedBand === 'n28') return c.n28.dlThroughput > 0
            return true
          })
          .map(cell => (
            <CellRow
              key={cell.stationId}
              cell={cell}
              expanded={expandedCell === cell.stationId}
              onToggle={() => setExpandedCell(expandedCell === cell.stationId ? null : cell.stationId)}
            />
          ))}
      </Paper>
    </Box>
  )
}
