import {
  CheckCircle as HealthyIcon,
  Error as CriticalIcon,
  Warning as WarningIcon,
  Speed as PerformanceIcon,
  FiveG as FiveGIcon,
  NetworkCheck as NetworkIcon,
  TrendingUp as QualityIcon,
  InfoOutlined as InfoIcon,
} from '@mui/icons-material'
import {
  Box,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Tooltip,
  Typography,
} from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { format, subDays } from 'date-fns'
import { motion } from 'framer-motion'
import React, { useState } from 'react'
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip as RechartsTooltip,
  XAxis,
  YAxis,
} from 'recharts'
import ErrorDisplay from '../components/ErrorDisplay'
import LoadingSpinner from '../components/LoadingSpinner'
import {
  CARD_STATUS_STYLES,
  CSS_VARS,
  FILTER_FORM_CONTROL_SX,
  SELECT_MENU_PROPS,
  GRID_METRIC_CARDS_SX,
  POLLING_INTERVALS,
} from '../constants/designSystem'
import { CATEGORY_CONFIG as BASE_CATEGORY_CONFIG, HealthStatus, MetricConfig, METRICS_CONFIG } from '../constants/metricsConfig'
import { metricsApi, stationApi } from '../services/api'
import type { DailyMetricAggregate } from '../services/api/metrics'
import { BaseStation, MetricData } from '../types'
import { ensureArray } from '../utils/arrayUtils'

interface ProcessedMetric {
  type: string
  average: number
  min: number
  max: number
  current: number
  count: number
  config: MetricConfig
}

interface ChartDataPoint {
  date: string
  [key: string]: number | string | undefined
}

// Unit conversion constants
const UNIT_CONVERSION = {
  WATTS_TO_KILOWATTS: 1000,
} as const

// ============================================================================
// Constants
// ============================================================================
// Extend CARD_STATUS_STYLES with icons for this page
const STATUS_STYLES = {
  healthy: { ...CARD_STATUS_STYLES.healthy, Icon: HealthyIcon },
  warning: { ...CARD_STATUS_STYLES.warning, Icon: WarningIcon },
  critical: { ...CARD_STATUS_STYLES.critical, Icon: CriticalIcon },
} as const

// Extend base category config with icons
const CATEGORY_CONFIG = {
  system: { ...BASE_CATEGORY_CONFIG.system, Icon: PerformanceIcon },
  '5g-n78': { ...BASE_CATEGORY_CONFIG['5g-n78'], Icon: FiveGIcon },
  '5g-n28': { ...BASE_CATEGORY_CONFIG['5g-n28'], Icon: NetworkIcon },
  quality: { ...BASE_CATEGORY_CONFIG.quality, Icon: QualityIcon },
}

// ============================================================================
// Components
// ============================================================================

interface DetailedMetricCardProps {
  metric: ProcessedMetric
  delay: number
}

const DetailedMetricCard = ({ metric, delay }: DetailedMetricCardProps) => {
  const { config, average, min, max, current, count } = metric
  const status = config.getStatus(average)
  const styles = STATUS_STYLES[status as keyof typeof STATUS_STYLES]
  const StatusIcon = styles.Icon

  return (
    <Box
      component={motion.div}
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.3 }}
      sx={{
        background: 'var(--surface-base)',
        border: '1px solid var(--surface-border)',
        borderRadius: '14px',
        overflow: 'hidden',
        transition: 'all 0.2s ease',
        '&:hover': {
          borderColor: styles.border,
          boxShadow: `0 8px 24px ${styles.shadow}`,
        },
      }}
    >
      {/* Status bar */}
      <Box sx={{ height: '4px', background: styles.color }} />

      <Box sx={{ padding: '20px' }}>
        {/* Header */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: '16px' }}>
          <Box sx={{ flex: 1 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: '8px', mb: '4px' }}>
              <Typography sx={{ fontSize: '1rem', fontWeight: 700, color: 'var(--mono-950)' }}>
                {config.label}
              </Typography>
              <Tooltip title={config.description} arrow placement="top">
                <InfoIcon sx={{ fontSize: 16, color: 'var(--mono-400)', cursor: 'help' }} />
              </Tooltip>
            </Box>
            <Typography sx={{ fontSize: '0.75rem', color: 'var(--mono-500)' }}>
              {config.fullLabel}
            </Typography>
          </Box>
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              padding: '4px 10px',
              borderRadius: '6px',
              background: styles.bgSubtle,
            }}
          >
            <StatusIcon sx={{ fontSize: 14, color: styles.color }} />
            <Typography sx={{ fontSize: '0.6875rem', fontWeight: 700, color: styles.color, textTransform: 'uppercase' }}>
              {styles.label}
            </Typography>
          </Box>
        </Box>

        {/* Current Value */}
        <Box sx={{ display: 'flex', alignItems: 'baseline', gap: '6px', mb: '16px' }}>
          <Typography
            sx={{
              fontSize: '2.25rem',
              fontWeight: 800,
              fontFamily: "'JetBrains Mono', monospace",
              color: 'var(--mono-950)',
              lineHeight: 1,
            }}
          >
            {config.format(current)}
          </Typography>
          <Typography sx={{ fontSize: '1rem', fontWeight: 600, color: 'var(--mono-400)' }}>
            {config.unit}
          </Typography>
        </Box>

        {/* Stats Grid */}
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: 'repeat(3, 1fr)',
            gap: '12px',
            padding: '12px',
            background: 'var(--surface-elevated)',
            borderRadius: '8px',
            mb: '16px',
          }}
        >
          <Box sx={{ textAlign: 'center' }}>
            <Typography sx={{ fontSize: '0.625rem', fontWeight: 600, color: 'var(--mono-400)', textTransform: 'uppercase', mb: '2px' }}>
              Min
            </Typography>
            <Typography sx={{ fontSize: '0.875rem', fontWeight: 600, fontFamily: "'JetBrains Mono', monospace", color: 'var(--mono-700)' }}>
              {config.format(min)}
            </Typography>
          </Box>
          <Box sx={{ textAlign: 'center', borderLeft: '1px solid var(--mono-200)', borderRight: '1px solid var(--mono-200)' }}>
            <Typography sx={{ fontSize: '0.625rem', fontWeight: 600, color: 'var(--mono-400)', textTransform: 'uppercase', mb: '2px' }}>
              Avg
            </Typography>
            <Typography sx={{ fontSize: '0.875rem', fontWeight: 600, fontFamily: "'JetBrains Mono', monospace", color: 'var(--mono-950)' }}>
              {config.format(average)}
            </Typography>
          </Box>
          <Box sx={{ textAlign: 'center' }}>
            <Typography sx={{ fontSize: '0.625rem', fontWeight: 600, color: 'var(--mono-400)', textTransform: 'uppercase', mb: '2px' }}>
              Max
            </Typography>
            <Typography sx={{ fontSize: '0.875rem', fontWeight: 600, fontFamily: "'JetBrains Mono', monospace", color: 'var(--mono-700)' }}>
              {config.format(max)}
            </Typography>
          </Box>
        </Box>

        {/* Thresholds */}
        <Box sx={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <Box sx={{ width: 8, height: 8, borderRadius: '50%', background: CSS_VARS.statusActive }} />
            <Typography sx={{ fontSize: '0.6875rem', color: 'var(--mono-500)' }}>{config.thresholds.good}</Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <Box sx={{ width: 8, height: 8, borderRadius: '50%', background: CSS_VARS.statusMaintenance }} />
            <Typography sx={{ fontSize: '0.6875rem', color: 'var(--mono-500)' }}>{config.thresholds.warn}</Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <Box sx={{ width: 8, height: 8, borderRadius: '50%', background: CSS_VARS.statusOffline }} />
            <Typography sx={{ fontSize: '0.6875rem', color: 'var(--mono-500)' }}>{config.thresholds.critical}</Typography>
          </Box>
        </Box>

        {/* Sample count */}
        <Typography sx={{ fontSize: '0.6875rem', color: 'var(--mono-400)', mt: '12px' }}>
          Based on {count.toLocaleString()} samples
        </Typography>
      </Box>
    </Box>
  )
}

interface CategorySectionProps {
  category: keyof typeof CATEGORY_CONFIG
  metrics: ProcessedMetric[]
  chartData: ChartDataPoint[]
  baseDelay: number
  days: number
}

const CategorySection = ({ category, metrics, chartData, baseDelay, days }: CategorySectionProps) => {
  const config = CATEGORY_CONFIG[category]
  const Icon = config.Icon

  if (metrics.length === 0) return null

  // Get worst status in category
  const statusSet = new Set(metrics.map(m => m.config.getStatus(m.average)))
  const getWorstStatus = (): HealthStatus => {
    if (statusSet.has('critical')) return 'critical'
    if (statusSet.has('warning')) return 'warning'
    return 'healthy'
  }
  const worstStatus: HealthStatus = getWorstStatus()
  const statusStyles = STATUS_STYLES[worstStatus]

  return (
    <Box
      component={motion.div}
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: baseDelay, duration: 0.4 }}
      sx={{ marginBottom: '40px' }}
    >
      {/* Section Header */}
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <Box
            sx={{
              width: 44,
              height: 44,
              borderRadius: '12px',
              background: 'var(--surface-subtle)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Icon sx={{ fontSize: 24, color: 'var(--mono-600)' }} />
          </Box>
          <Box>
            <Typography sx={{ fontSize: '1.125rem', fontWeight: 700, color: 'var(--mono-950)', letterSpacing: '-0.01em' }}>
              {config.title}
            </Typography>
            <Typography sx={{ fontSize: '0.8125rem', color: 'var(--mono-500)' }}>
              {config.subtitle}
            </Typography>
          </Box>
        </Box>
        <Box
          sx={{
            padding: '6px 14px',
            borderRadius: '100px',
            background: statusStyles.bg,
            border: `1px solid ${statusStyles.border}`,
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
          }}
        >
          <Box sx={{ width: 8, height: 8, borderRadius: '50%', background: statusStyles.color }} />
          <Typography sx={{ fontSize: '0.8125rem', fontWeight: 600, color: statusStyles.color }}>
            {statusStyles.label}
          </Typography>
        </Box>
      </Box>

      {/* Metrics Grid */}
      <Box
        sx={{ ...GRID_METRIC_CARDS_SX, marginBottom: '24px' }}
      >
        {metrics.map((metric, idx) => (
          <DetailedMetricCard key={metric.type} metric={metric} delay={baseDelay + 0.05 + idx * 0.03} />
        ))}
      </Box>

      {/* Chart */}
      <Box
        component={motion.div}
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: baseDelay + 0.2, duration: 0.35 }}
        sx={{
          background: 'var(--surface-base)',
          border: '1px solid var(--surface-border)',
          borderRadius: '14px',
          padding: '24px',
        }}
      >
        <Typography sx={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--mono-950)', mb: '4px' }}>
          {config.title} Trends
        </Typography>
        <Typography sx={{ fontSize: '0.75rem', color: 'var(--mono-500)', mb: '20px' }}>
          Historical data visualization
        </Typography>
        <ResponsiveContainer width="100%" height={220}>
          <AreaChart data={chartData} margin={{ top: 5, right: 30, left: 0, bottom: 5 }}>
            <defs>
              {metrics.map(m => (
                <linearGradient key={m.type} id={`gradient-${m.type}`} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor={m.config.color} stopOpacity={0.3} />
                  <stop offset="95%" stopColor={m.config.color} stopOpacity={0} />
                </linearGradient>
              ))}
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--mono-200)" />
            <XAxis
              dataKey="date"
              tick={{ fontSize: 10, fill: 'var(--mono-600)' }}
              stroke="var(--mono-300)"
              interval={days <= 7 ? 0 : days <= 30 ? 'preserveStartEnd' : 'preserveStartEnd'}
              tickMargin={5}
            />
            <YAxis tick={{ fontSize: 11, fill: 'var(--mono-600)' }} stroke="var(--mono-300)" />
            <RechartsTooltip
              contentStyle={{
                background: 'var(--surface-base)',
                border: '1px solid var(--surface-border)',
                borderRadius: '8px',
                fontSize: '0.8125rem',
              }}
            />
            {metrics.map(m => (
              <Area
                key={m.type}
                type="monotone"
                dataKey={m.type}
                stroke={m.config.color}
                strokeWidth={2}
                fill={`url(#gradient-${m.type})`}
                connectNulls
              />
            ))}
          </AreaChart>
        </ResponsiveContainer>
      </Box>
    </Box>
  )
}

// ============================================================================
// Main Component
// ============================================================================
export default function Metrics() {
  const [selectedStation, setSelectedStation] = useState<number | 'all'>('all')
  const [selectedCategory, setSelectedCategory] = useState<string>('all')
  const [days, setDays] = useState(7)

  const { data: stations, error: stationsError } = useQuery({
    queryKey: ['stations'],
    queryFn: async () => {
      const response = await stationApi.getAll()
      return response.data
    },
  })

  const { data: historicalMetrics, isLoading: isLoadingHistorical, error: historicalError } = useQuery({
    queryKey: ['metrics-historical', days],
    queryFn: async () => {
      const response = await metricsApi.getAll({
        startTime: subDays(new Date(), days).toISOString(),
        endTime: new Date().toISOString(),
        limit: 10000,
        sort: 'asc',
      })
      return response.data
    },
    staleTime: 5 * 60 * 1000,
    gcTime: 10 * 60 * 1000,
  })

  const { data: liveMetrics, error: liveError } = useQuery({
    queryKey: ['metrics-live'],
    queryFn: async () => {
      const twoMinutesAgo = new Date(Date.now() - 2 * 60 * 1000)
      const response = await metricsApi.getAll({
        startTime: twoMinutesAgo.toISOString(),
        limit: 500,
      })
      return response.data
    },
    refetchInterval: POLLING_INTERVALS.NORMAL,
  })

  // Fetch pre-aggregated daily data for charts (efficient - no client-side aggregation needed)
  const { data: dailyAggregates, error: dailyError } = useQuery({
    queryKey: ['metrics-daily', days],
    queryFn: async () => {
      const response = await metricsApi.getDailyAggregates({
        startTime: subDays(new Date(), days).toISOString(),
        endTime: new Date().toISOString(),
      })
      return response.data
    },
    staleTime: 5 * 60 * 1000,
    gcTime: 10 * 60 * 1000,
  })

  // Merge historical + live (immutable)
  const metrics = React.useMemo(() => {
    const historical = ensureArray(historicalMetrics as MetricData[])
    const live = ensureArray(liveMetrics as MetricData[])
    const existingIds = new Set(historical.map((m) => m.id))
    const newLiveMetrics = live.filter((m) => !existingIds.has(m.id))
    return [...historical, ...newLiveMetrics]
  }, [historicalMetrics, liveMetrics])

  const stationsList = ensureArray(stations as BaseStation[])
  const metricsList = ensureArray(metrics as MetricData[])

  // Filter by station - memoized to prevent recalculation every render
  const filteredMetrics = React.useMemo(
    () => selectedStation === 'all'
      ? metricsList
      : metricsList.filter((m) => m.stationId === selectedStation),
    [metricsList, selectedStation]
  )

  // Process metrics into detailed format
  // Uses Map for O(1) lookups and local mutation for efficiency
  const processedMetrics = React.useMemo(() => {
    const accumulator = new Map<string, { sum: number; min: number; max: number; current: number; count: number; maxTimestamp: number }>()

    for (const m of filteredMetrics) {
      const ts = m.timestamp ? new Date(m.timestamp).getTime() : 0
      const existing = accumulator.get(m.metricType)

      if (!existing) {
        accumulator.set(m.metricType, {
          sum: m.value,
          min: m.value,
          max: m.value,
          current: m.value,
          count: 1,
          maxTimestamp: ts,
        })
      } else {
        // Update in place (local mutation within useMemo is safe)
        existing.sum += m.value
        existing.min = Math.min(existing.min, m.value)
        existing.max = Math.max(existing.max, m.value)
        existing.count += 1
        if (ts > existing.maxTimestamp) {
          existing.current = m.value
          existing.maxTimestamp = ts
        }
      }
    }

    return Array.from(accumulator.entries())
      .filter(([type]) => METRICS_CONFIG[type])
      .map(([type, data]) => ({
        type,
        average: data.sum / data.count,
        min: data.min,
        max: data.max,
        current: data.current,
        count: data.count,
        config: METRICS_CONFIG[type],
      }))
  }, [filteredMetrics])

  // Group by category
  const metricsByCategory = React.useMemo(() => {
    const groups: Record<string, ProcessedMetric[]> = {
      system: [],
      '5g-n78': [],
      '5g-n28': [],
      quality: [],
    }
    for (const m of processedMetrics) {
      if (groups[m.config.category]) {
        groups[m.config.category].push(m)
      }
    }
    return groups
  }, [processedMetrics])

  // Chart data from pre-aggregated daily data (efficient - computed server-side)
  // Date format adapts based on time range for readability
  const dateFormat = days <= 7 ? 'MMM dd' : days <= 30 ? 'MM/dd' : 'M/d'
  const chartData = React.useMemo(() => {
    const aggregates = ensureArray(dailyAggregates as DailyMetricAggregate[])

    return aggregates
      .filter((d): d is DailyMetricAggregate => d !== null && d.date !== null)
      .sort((a, b) => a.date.localeCompare(b.date))
      .map((daily) => {
        const date = new Date(daily.date)
        const point: ChartDataPoint = { date: format(date, dateFormat) }

        if (daily.averages) {
          for (const [type, avg] of Object.entries(daily.averages)) {
            let value = avg
            if (type === 'POWER_CONSUMPTION') {
              value = avg / UNIT_CONVERSION.WATTS_TO_KILOWATTS
            }
            point[type] = value
          }
        }
        return point
      })
  }, [dailyAggregates, days, dateFormat])

  if (isLoadingHistorical) {
    return <LoadingSpinner />
  }

  const error = stationsError || historicalError || liveError || dailyError
  if (error) {
    return <ErrorDisplay title="Failed to load metrics data" message={error.message} />
  }

  const categories = selectedCategory === 'all'
    ? (['system', '5g-n78', '5g-n28', 'quality'] as const)
    : [selectedCategory as keyof typeof CATEGORY_CONFIG]

  return (
    <Box sx={{ maxWidth: '1600px', margin: '0 auto', padding: { xs: '16px 12px', sm: '24px 16px', md: '32px 24px' } }}>
      {/* Header */}
      <Box
        component={motion.div}
        initial={{ opacity: 0, y: -16 }}
        animate={{ opacity: 1, y: 0 }}
        sx={{ marginBottom: '32px' }}
      >
        <Typography
          variant="h1"
          sx={{
            fontSize: { xs: '1.5rem', sm: '1.75rem', md: '2.25rem' },
            fontWeight: 700,
            letterSpacing: '-0.025em',
            color: 'var(--mono-950)',
            marginBottom: '8px',
          }}
        >
          Metrics
        </Typography>
        <Typography sx={{ fontSize: '0.875rem', color: 'var(--mono-500)' }}>
          Detailed performance analytics with thresholds and trends · {filteredMetrics.length.toLocaleString()} data points
        </Typography>
      </Box>

      {/* Filters */}
      <Box
        component={motion.div}
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.05, duration: 0.35 }}
        sx={{ display: 'flex', gap: '16px', marginBottom: '32px', flexWrap: 'wrap' }}
      >
        <FormControl size="small" sx={FILTER_FORM_CONTROL_SX}>
          <InputLabel>Station</InputLabel>
          <Select
            value={selectedStation}
            onChange={(e) => setSelectedStation(e.target.value as number | 'all')}
            label="Station"
            MenuProps={SELECT_MENU_PROPS}
          >
            <MenuItem value="all">All Stations</MenuItem>
            {stationsList.map((station: BaseStation) => (
              <MenuItem key={station.id} value={station.id}>{station.stationName}</MenuItem>
            ))}
          </Select>
        </FormControl>

        <FormControl size="small" sx={FILTER_FORM_CONTROL_SX}>
          <InputLabel>Category</InputLabel>
          <Select
            value={selectedCategory}
            onChange={(e) => setSelectedCategory(e.target.value)}
            label="Category"
            MenuProps={SELECT_MENU_PROPS}
          >
            <MenuItem value="all">All Categories</MenuItem>
            <MenuItem value="system">System Performance</MenuItem>
            <MenuItem value="5g-n78">5G NR n78 (3.5 GHz)</MenuItem>
            <MenuItem value="5g-n28">5G NR n28 (700 MHz)</MenuItem>
            <MenuItem value="quality">Network Quality</MenuItem>
          </Select>
        </FormControl>

        <FormControl size="small" sx={FILTER_FORM_CONTROL_SX}>
          <InputLabel>Time Range</InputLabel>
          <Select
            value={days}
            onChange={(e) => setDays(Number(e.target.value))}
            label="Time Range"
            MenuProps={SELECT_MENU_PROPS}
          >
            <MenuItem value={1}>Last 24 Hours</MenuItem>
            <MenuItem value={7}>Last 7 Days</MenuItem>
            <MenuItem value={30}>Last 30 Days</MenuItem>
            <MenuItem value={90}>Last 90 Days</MenuItem>
          </Select>
        </FormControl>
      </Box>

      {/* Category Sections */}
      {categories.map((category, idx) => (
        <CategorySection
          key={category}
          category={category}
          metrics={metricsByCategory[category] || []}
          chartData={chartData}
          baseDelay={0.1 + idx * 0.1}
          days={days}
        />
      ))}

      {processedMetrics.length === 0 && (
        <Box sx={{ textAlign: 'center', padding: '60px 20px' }}>
          <Typography sx={{ fontSize: '1rem', color: 'var(--mono-500)' }}>
            No metrics data available for the selected filters
          </Typography>
        </Box>
      )}
    </Box>
  )
}
