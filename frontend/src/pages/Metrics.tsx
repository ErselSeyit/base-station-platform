import {
  Box,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Typography,
} from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { format, subDays } from 'date-fns'
import { motion } from 'framer-motion'
import React, { useState } from 'react'
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import LoadingSpinner from '../components/LoadingSpinner'
import { CHART_COLORS } from '../constants/colors'
import { metricsApi, stationApi } from '../services/api'
import { BaseStation, MetricData, MetricType } from '../types'

interface ChartDataPoint {
  date: string
  sortKey: string  // ISO date for proper sorting
  CPU_USAGE?: number
  MEMORY_USAGE?: number
  POWER_CONSUMPTION?: number
  TEMPERATURE?: number
  SIGNAL_STRENGTH?: number
  UPTIME?: number
  CONNECTION_COUNT?: number
  DATA_THROUGHPUT?: number
}

interface MetricAccumulator {
  displayDate: string
  sortKey: string
  sums: Record<string, number>
  counts: Record<string, number>
}

// Shared FormControl styles to avoid duplication
const FILTER_FORM_CONTROL_SX = {
  flex: '1 1 200px',
  '& .MuiOutlinedInput-root': {
    background: 'var(--surface-base)',
    borderRadius: '8px',
    color: 'var(--mono-950)',
    '& fieldset': {
      borderColor: 'var(--surface-border)',
    },
    '&:hover fieldset': {
      borderColor: 'var(--mono-400)',
    },
  },
  '& .MuiInputLabel-root': {
    color: 'var(--mono-600)',
    '&.Mui-focused': {
      color: 'var(--mono-950)',
    },
  },
  '& .MuiSelect-icon': {
    color: 'var(--mono-600)',
  },
} as const

// Shared Select MenuProps styles
const FILTER_MENU_PROPS = {
  PaperProps: {
    sx: {
      background: 'var(--surface-base)',
      border: '1px solid var(--surface-border)',
      '& .MuiMenuItem-root': {
        color: 'var(--mono-950)',
        '&:hover': {
          background: 'var(--mono-100)',
        },
        '&.Mui-selected': {
          background: 'var(--mono-100)',
          '&:hover': {
            background: 'var(--mono-200)',
          },
        },
      },
    },
  },
} as const

const METRIC_CONFIGS = {
  CPU_USAGE: { color: CHART_COLORS.cpuUsage, unit: '%', domain: [0, 100] as [number, number] },
  MEMORY_USAGE: { color: CHART_COLORS.memoryUsage, unit: '%', domain: [0, 100] as [number, number] },
  TEMPERATURE: { color: CHART_COLORS.temperature, unit: '°C', domain: [0, 100] as [number, number] },
  POWER_CONSUMPTION: { color: CHART_COLORS.tertiary, unit: ' kW', domain: [0, 'auto'] as [number, string] },
  SIGNAL_STRENGTH: { color: CHART_COLORS.signalStrength, unit: ' dBm', domain: [-100, -40] as [number, number] },
  UPTIME: { color: CHART_COLORS.secondary, unit: '%', domain: [0, 100] as [number, number] },
  CONNECTION_COUNT: { color: CHART_COLORS.tertiary, unit: '', domain: [0, 'auto'] as [number, string] },
  DATA_THROUGHPUT: { color: CHART_COLORS.dataThroughput, unit: ' Mbps', domain: [0, 'auto'] as [number, string] },
  FAN_SPEED: { color: CHART_COLORS.fanSpeed, unit: ' RPM', domain: [0, 'auto'] as [number, string] },
  // 5G NR Metrics
  DL_THROUGHPUT_NR3500: { color: CHART_COLORS.nr3500Download, unit: ' Mbps', domain: [0, 2000] as [number, number] },
  UL_THROUGHPUT_NR3500: { color: CHART_COLORS.nr3500Upload, unit: ' Mbps', domain: [0, 200] as [number, number] },
  RSRP_NR3500: { color: CHART_COLORS.rsrp, unit: ' dBm', domain: [-120, -60] as [number, number] },
  SINR_NR3500: { color: CHART_COLORS.sinr, unit: ' dB', domain: [-10, 40] as [number, number] },
  DL_THROUGHPUT_NR700: { color: CHART_COLORS.nr700Download, unit: ' Mbps', domain: [0, 150] as [number, number] },
  UL_THROUGHPUT_NR700: { color: CHART_COLORS.nr700Upload, unit: ' Mbps', domain: [0, 50] as [number, number] },
  LATENCY_PING: { color: CHART_COLORS.latency, unit: ' ms', domain: [0, 50] as [number, number] },
  TX_IMBALANCE: { color: CHART_COLORS.txImbalance, unit: ' dB', domain: [0, 10] as [number, number] },
}


interface MetricCardProps {
  label: string
  value: string
  unit: string
  color: string
  delay: number
}

const MetricCard = ({ label, value, unit, color, delay }: MetricCardProps) => {
  return (
    <Box
      component={motion.div}
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
      sx={{
        padding: '20px',
        background: 'var(--surface-base)',
        border: '1px solid var(--surface-border)',
        borderLeft: `3px solid ${color}`,
        borderRadius: '12px',
        transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
        '&:hover': {
          boxShadow: 'var(--shadow-md)',
          borderColor: 'var(--mono-400)',
        },
      }}
    >
      <Typography
        sx={{
          fontSize: '0.75rem',
          fontWeight: 500,
          color: 'var(--mono-500)',
          textTransform: 'uppercase',
          letterSpacing: '0.05em',
          marginBottom: '8px',
        }}
      >
        {label}
      </Typography>
      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: '4px' }}>
        <Typography
          sx={{
            fontSize: '1.75rem',
            fontWeight: 600,
            fontVariantNumeric: 'tabular-nums',
            fontFamily: "'JetBrains Mono', monospace",
            color: 'var(--mono-950)',
          }}
        >
          {value}
        </Typography>
        <Typography
          sx={{
            fontSize: '0.875rem',
            fontWeight: 500,
            color: 'var(--mono-500)',
          }}
        >
          {unit}
        </Typography>
      </Box>
    </Box>
  )
}

interface ChartCardProps {
  title: string
  subtitle: string
  data: ChartDataPoint[]
  metricKey: keyof Omit<ChartDataPoint, 'date'>
  color: string
  unit: string
  domain: [number, number | string]
  delay: number
}

const ChartCard = ({ title, subtitle, data, metricKey, color, unit, domain, delay }: ChartCardProps) => {
  return (
    <Box
      component={motion.div}
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
      sx={{
        background: 'var(--surface-base)',
        border: '1px solid var(--surface-border)',
        borderRadius: '12px',
        padding: '24px',
        transition: 'all 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
        '&:hover': {
          boxShadow: 'var(--shadow-md)',
          borderColor: 'var(--mono-400)',
        },
      }}
    >
      <Typography
        sx={{
          fontSize: '0.875rem',
          fontWeight: 600,
          color: 'var(--mono-950)',
          marginBottom: '4px',
          letterSpacing: '-0.01em',
        }}
      >
        {title}
      </Typography>
      <Typography
        sx={{
          fontSize: '0.75rem',
          color: 'var(--mono-500)',
          marginBottom: '20px',
        }}
      >
        {subtitle}
      </Typography>
      <ResponsiveContainer width="100%" height={250}>
        <LineChart data={data}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--mono-200)" />
          <XAxis
            dataKey="date"
            tick={{ fontSize: 11, fill: 'var(--mono-600)' }}
            stroke="var(--mono-400)"
          />
          <YAxis
            domain={domain}
            tickFormatter={(v) => `${typeof v === 'number' ? v.toFixed(0) : v}${unit}`}
            tick={{ fontSize: 11, fill: 'var(--mono-600)' }}
            stroke="var(--mono-400)"
          />
          <Tooltip
            formatter={(value: number) => `${value.toFixed(2)}${unit}`}
            labelStyle={{ fontWeight: 600, color: 'var(--mono-950)' }}
            contentStyle={{
              background: 'var(--surface-base)',
              border: '1px solid var(--surface-border)',
              borderRadius: '8px',
              fontSize: '0.8125rem',
              color: 'var(--mono-950)',
            }}
            itemStyle={{ color: 'var(--mono-700)' }}
          />
          <Line
            type="monotone"
            dataKey={metricKey}
            stroke={color}
            strokeWidth={2}
            dot={false}
            connectNulls
          />
        </LineChart>
      </ResponsiveContainer>
    </Box>
  )
}

export default function Metrics() {
  const [selectedStation, setSelectedStation] = useState<number | 'all'>('all')
  const [metricType, setMetricType] = useState<MetricType | 'all'>('all')
  const [days, setDays] = useState(7)

  const { data: stations } = useQuery({
    queryKey: ['stations'],
    queryFn: async () => {
      const response = await stationApi.getAll()
      return response.data
    },
  })

  // Historical data - load once, cache for 5 minutes (won't refetch on every render)
  const { data: historicalMetrics, isLoading: isLoadingHistorical } = useQuery({
    queryKey: ['metrics-historical', days],
    queryFn: async () => {
      const response = await metricsApi.getAll({
        startTime: subDays(new Date(), days).toISOString(),
        endTime: subDays(new Date(), 0).toISOString(), // Up to now
        limit: 10000, // Get more for historical charts
        sort: 'asc', // Oldest first for historical charts
      })
      return response.data
    },
    staleTime: 5 * 60 * 1000, // Consider fresh for 5 minutes
    gcTime: 10 * 60 * 1000, // Keep in cache for 10 minutes
  })

  // Live data - fetch recent metrics only (last 2 minutes), refresh every 30s
  const { data: liveMetrics } = useQuery({
    queryKey: ['metrics-live'],
    queryFn: async () => {
      const twoMinutesAgo = new Date(Date.now() - 2 * 60 * 1000)
      const response = await metricsApi.getAll({
        startTime: twoMinutesAgo.toISOString(),
        limit: 500, // Small limit for recent data
      })
      return response.data
    },
    refetchInterval: 30000, // Only live data refreshes
  })

  // Merge historical + live, dedupe by ID
  const metrics = React.useMemo(() => {
    const historical = Array.isArray(historicalMetrics) ? historicalMetrics : []
    const live = Array.isArray(liveMetrics) ? liveMetrics : []
    const merged = [...historical]
    const existingIds = new Set(historical.map((m: MetricData) => m.id))
    for (const m of live) {
      if (!existingIds.has(m.id)) {
        merged.push(m)
      }
    }
    return merged
  }, [historicalMetrics, liveMetrics])

  const isLoading = isLoadingHistorical

  const stationsList = Array.isArray(stations) ? stations : []
  const metricsList = Array.isArray(metrics) ? metrics : []

  // Filter metrics
  let filteredMetrics = metricsList
  if (selectedStation !== 'all') {
    filteredMetrics = filteredMetrics.filter((m: MetricData) => m.stationId === selectedStation)
  }
  if (metricType !== 'all') {
    filteredMetrics = filteredMetrics.filter((m: MetricData) => m.metricType === metricType)
  }

  // Group by date for chart
  const chartDataAccumulator = filteredMetrics.reduce((acc: Record<string, MetricAccumulator>, metric: MetricData) => {
    if (!metric.timestamp) return acc
    const metricDate = new Date(metric.timestamp)
    const sortKey = format(metricDate, 'yyyy-MM-dd')  // Sortable key
    const displayDate = format(metricDate, 'MMM dd')  // Display format

    if (!acc[sortKey]) {
      acc[sortKey] = { displayDate, sortKey, sums: {}, counts: {} }
    }

    const metricType = metric.metricType
    acc[sortKey].sums[metricType] = (acc[sortKey].sums[metricType] || 0) + metric.value
    acc[sortKey].counts[metricType] = (acc[sortKey].counts[metricType] || 0) + 1

    return acc
  }, {})

  // Convert to averages, sorted by date
  const accumulatorValues: MetricAccumulator[] = Object.values(chartDataAccumulator)
  accumulatorValues.sort((a, b) => a.sortKey.localeCompare(b.sortKey))
  const chartData: ChartDataPoint[] = accumulatorValues
    .map((item) => {
      const point: ChartDataPoint = { date: item.displayDate, sortKey: item.sortKey }
      Object.keys(item.sums).forEach((metricType) => {
        const key = metricType as keyof Omit<ChartDataPoint, 'date' | 'sortKey'>
        let avgValue = item.sums[metricType] / item.counts[metricType]
        if (metricType === 'POWER_CONSUMPTION') {
          avgValue = avgValue / 1000
        }
        ;(point as unknown as Record<string, number>)[key] = avgValue
      })
      return point
    })

  // Calculate overall averages
  const averages = filteredMetrics.reduce((acc: Record<string, { sum: number; count: number }>, metric: MetricData) => {
    if (!acc[metric.metricType]) {
      acc[metric.metricType] = { sum: 0, count: 0 }
    }
    acc[metric.metricType].sum += metric.value
    acc[metric.metricType].count += 1
    return acc
  }, {})

  const averageData = Object.entries(averages).map(([type, data]) => {
    const avgData = data as { sum: number; count: number }
    let avgValue = avgData.sum / avgData.count
    if (type === 'POWER_CONSUMPTION') {
      avgValue = avgValue / 1000
    }
    const config = METRIC_CONFIGS[type as keyof typeof METRIC_CONFIGS]
    return {
      type,
      average: avgValue.toFixed(2),
      config,
    }
  })

  if (isLoading) {
    return <LoadingSpinner />
  }

  return (
    <Box sx={{ maxWidth: '1400px', margin: '0 auto', padding: { xs: '16px 12px', sm: '24px 16px', md: '32px 24px' } }}>
      {/* Header */}
      <Box
        component={motion.div}
        initial={{ opacity: 0, y: -16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
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
        <Typography
          sx={{
            fontSize: '0.875rem',
            color: 'var(--mono-500)',
            letterSpacing: '0.01em',
          }}
        >
          Performance analytics and data visualization · {filteredMetrics.length.toLocaleString()} data points
        </Typography>
      </Box>

      {/* Filters */}
      <Box
        component={motion.div}
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.05, duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
        sx={{
          display: 'flex',
          gap: '16px',
          marginBottom: '24px',
          flexWrap: 'wrap',
        }}
      >
        <FormControl size="small" sx={FILTER_FORM_CONTROL_SX}>
          <InputLabel>Station</InputLabel>
          <Select
            value={selectedStation}
            onChange={(e) => setSelectedStation(e.target.value as number | 'all')}
            label="Station"
            MenuProps={FILTER_MENU_PROPS}
          >
            <MenuItem value="all">All Stations</MenuItem>
            {stationsList.map((station: BaseStation) => (
              <MenuItem key={station.id} value={station.id}>
                {station.stationName}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <FormControl size="small" sx={FILTER_FORM_CONTROL_SX}>
          <InputLabel>Metric Type</InputLabel>
          <Select
            value={metricType}
            onChange={(e) => setMetricType(e.target.value as MetricType | 'all')}
            label="Metric Type"
            MenuProps={FILTER_MENU_PROPS}
          >
            <MenuItem value="all">All Metrics</MenuItem>
            {Object.values(MetricType).map((type) => (
              <MenuItem key={type} value={type}>
                {type.replace('_', ' ')}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <FormControl size="small" sx={FILTER_FORM_CONTROL_SX}>
          <InputLabel>Time Range</InputLabel>
          <Select
            value={days}
            onChange={(e) => setDays(Number(e.target.value))}
            label="Time Range"
            MenuProps={FILTER_MENU_PROPS}
          >
            <MenuItem value={1}>Last 24 Hours</MenuItem>
            <MenuItem value={7}>Last 7 Days</MenuItem>
            <MenuItem value={30}>Last 30 Days</MenuItem>
            <MenuItem value={90}>Last 90 Days</MenuItem>
          </Select>
        </FormControl>
      </Box>

      {/* Average Values Grid */}
      {averageData.length > 0 && (
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
            gap: '16px',
            marginBottom: '24px',
          }}
        >
          {averageData.map((item, idx) => (
            <MetricCard
              key={item.type}
              label={item.type.replace('_', ' ')}
              value={item.average}
              unit={item.config?.unit || ''}
              color={item.config?.color || '#737373'}
              delay={0.1 + idx * 0.03}
            />
          ))}
        </Box>
      )}

      {/* Charts Grid */}
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: '1fr', md: 'repeat(auto-fit, minmax(400px, 1fr))' },
          gap: '16px',
        }}
      >
        {/* Performance Metrics */}
        {(metricType === 'all' || metricType === 'CPU_USAGE' || metricType === 'MEMORY_USAGE' || metricType === 'UPTIME') && (
          <ChartCard
            title="System Performance"
            subtitle="CPU, Memory, and Uptime"
            data={chartData}
            metricKey="CPU_USAGE"
            color={METRIC_CONFIGS.CPU_USAGE.color}
            unit={METRIC_CONFIGS.CPU_USAGE.unit}
            domain={METRIC_CONFIGS.CPU_USAGE.domain}
            delay={0.2}
          />
        )}

        {/* Temperature */}
        {(metricType === 'all' || metricType === 'TEMPERATURE') && (
          <ChartCard
            title="Temperature"
            subtitle="Equipment temperature monitoring"
            data={chartData}
            metricKey="TEMPERATURE"
            color={METRIC_CONFIGS.TEMPERATURE.color}
            unit={METRIC_CONFIGS.TEMPERATURE.unit}
            domain={METRIC_CONFIGS.TEMPERATURE.domain}
            delay={0.25}
          />
        )}

        {/* Power Consumption */}
        {(metricType === 'all' || metricType === 'POWER_CONSUMPTION') && (
          <ChartCard
            title="Power Consumption"
            subtitle="Energy usage over time"
            data={chartData}
            metricKey="POWER_CONSUMPTION"
            color={METRIC_CONFIGS.POWER_CONSUMPTION.color}
            unit={METRIC_CONFIGS.POWER_CONSUMPTION.unit}
            domain={METRIC_CONFIGS.POWER_CONSUMPTION.domain}
            delay={0.3}
          />
        )}

        {/* Signal Strength */}
        {(metricType === 'all' || metricType === 'SIGNAL_STRENGTH') && (
          <ChartCard
            title="Signal Strength"
            subtitle="Radio signal quality"
            data={chartData}
            metricKey="SIGNAL_STRENGTH"
            color={METRIC_CONFIGS.SIGNAL_STRENGTH.color}
            unit={METRIC_CONFIGS.SIGNAL_STRENGTH.unit}
            domain={METRIC_CONFIGS.SIGNAL_STRENGTH.domain}
            delay={0.35}
          />
        )}

        {/* Connection Count */}
        {(metricType === 'all' || metricType === 'CONNECTION_COUNT') && (
          <ChartCard
            title="Active Connections"
            subtitle="Connected devices"
            data={chartData}
            metricKey="CONNECTION_COUNT"
            color={METRIC_CONFIGS.CONNECTION_COUNT.color}
            unit={METRIC_CONFIGS.CONNECTION_COUNT.unit}
            domain={METRIC_CONFIGS.CONNECTION_COUNT.domain}
            delay={0.4}
          />
        )}

        {/* Data Throughput */}
        {(metricType === 'all' || metricType === 'DATA_THROUGHPUT') && (
          <ChartCard
            title="Data Throughput"
            subtitle="Network bandwidth usage"
            data={chartData}
            metricKey="DATA_THROUGHPUT"
            color={METRIC_CONFIGS.DATA_THROUGHPUT.color}
            unit={METRIC_CONFIGS.DATA_THROUGHPUT.unit}
            domain={METRIC_CONFIGS.DATA_THROUGHPUT.domain}
            delay={0.45}
          />
        )}
      </Box>
    </Box>
  )
}
