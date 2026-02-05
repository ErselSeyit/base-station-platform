import { Box, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { format, subDays } from 'date-fns'
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { CHART_COLORS } from '../constants/colors'
import { CHART_CONFIG } from '../constants/designSystem'
import { metricsApi } from '../services/api'
import type { DailyMetricAggregate } from '../services/api/metrics'
import { ensureArray } from '../utils/arrayUtils'
import LoadingSpinner from './LoadingSpinner'

interface ChartDataPoint {
  date: string
  sortKey: string  // ISO date for sorting
  cpuUsage?: number
  memoryUsage?: number
  temperature?: number
  signalStrength?: number
}

export default function MetricsChart() {
  // Use server-side daily aggregates for efficient chart data
  const { data, isLoading, error } = useQuery({
    queryKey: ['metrics-chart-daily'],
    queryFn: async () => {
      const response = await metricsApi.getDailyAggregates({
        startTime: subDays(new Date(), 7).toISOString(),
        endTime: new Date().toISOString(),
      })
      return response.data
    },
    staleTime: CHART_CONFIG.DEFAULT_STALE_TIME_MS,
  })

  if (isLoading) {
    return <LoadingSpinner />
  }

  if (error) {
    return (
      <Box
        sx={{
          height: CHART_CONFIG.DEFAULT_HEIGHT,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Typography sx={{ color: 'var(--status-offline)', fontSize: '0.875rem' }}>
          Unable to load chart data
        </Typography>
      </Box>
    )
  }

  const dailyAggregates = ensureArray(data as DailyMetricAggregate[])

  // Transform server-aggregated data to chart format
  const chartData: ChartDataPoint[] = dailyAggregates
    .filter((d): d is DailyMetricAggregate => d !== null && d.date !== null)
    .sort((a, b) => a.date.localeCompare(b.date))
    .map((daily) => {
      const dateObj = new Date(daily.date)
      return {
        date: format(dateObj, 'MMM dd'),
        sortKey: daily.date,
        cpuUsage: daily.averages?.CPU_USAGE,
        memoryUsage: daily.averages?.MEMORY_USAGE,
        temperature: daily.averages?.TEMPERATURE,
        signalStrength: daily.averages?.SIGNAL_STRENGTH,
      }
    })

  // Check if we have any data
  const hasData = chartData.some(d =>
    d.cpuUsage !== undefined || d.memoryUsage !== undefined ||
    d.temperature !== undefined || d.signalStrength !== undefined
  )

  if (!hasData) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: CHART_CONFIG.DEFAULT_HEIGHT }}>
        <Typography color="text.secondary">No metrics data available</Typography>
      </Box>
    )
  }

  const formatValue = (value: number, name: string): [string, string] => {
    switch (name) {
      case 'cpuUsage':
        return [`${value.toFixed(1)}%`, 'CPU Usage']
      case 'memoryUsage':
        return [`${value.toFixed(1)}%`, 'Memory Usage']
      case 'temperature':
        return [`${value.toFixed(1)}°C`, 'Temperature']
      case 'signalStrength':
        return [`${value.toFixed(1)} dBm`, 'Signal Strength']
      default:
        return [`${value.toFixed(1)}`, name]
    }
  }

  const legendFormatter = (value: string) => {
    switch (value) {
      case 'cpuUsage': return 'CPU Usage'
      case 'memoryUsage': return 'Memory Usage'
      case 'temperature': return 'Temperature'
      case 'signalStrength': return 'Signal Strength'
      default: return value
    }
  }

  return (
    <Box
      role="img"
      aria-label="7-day metrics trend chart showing CPU usage, memory usage, temperature, and signal strength"
    >
      <ResponsiveContainer width="100%" height={CHART_CONFIG.DEFAULT_HEIGHT}>
        <LineChart data={chartData}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--mono-400)" />
        <XAxis
          dataKey="date"
          tick={{ fontSize: 12, fill: 'var(--mono-700)' }}
          stroke="var(--mono-500)"
        />
        <YAxis
          yAxisId="left"
          domain={[0, 100]}
          tickFormatter={(v) => `${v}%`}
          tick={{ fontSize: 12, fill: 'var(--mono-700)' }}
          stroke="var(--mono-500)"
        />
        <YAxis
          yAxisId="right"
          orientation="right"
          domain={[-100, 100]}
          tick={{ fontSize: 12, fill: 'var(--mono-700)' }}
          stroke="var(--mono-500)"
        />
        <Tooltip
          formatter={formatValue}
          labelStyle={{ fontWeight: 600, color: 'var(--mono-950)' }}
          contentStyle={{
            borderRadius: 8,
            background: 'var(--surface-base)',
            border: '1px solid var(--surface-border)',
            color: 'var(--mono-950)',
          }}
          itemStyle={{ color: 'var(--mono-700)' }}
        />
        <Legend
          formatter={legendFormatter}
          wrapperStyle={{ color: 'var(--mono-800)' }}
        />
        <Line
          yAxisId="left"
          type="monotone"
          dataKey="cpuUsage"
          stroke={CHART_COLORS.cpuUsage}
          strokeWidth={2}
          dot={false}
          connectNulls
        />
        <Line
          yAxisId="left"
          type="monotone"
          dataKey="memoryUsage"
          stroke={CHART_COLORS.memoryUsage}
          strokeWidth={2}
          dot={false}
          connectNulls
        />
        <Line
          yAxisId="right"
          type="monotone"
          dataKey="temperature"
          stroke={CHART_COLORS.temperature}
          strokeWidth={2}
          dot={false}
          connectNulls
        />
        <Line
          yAxisId="right"
          type="monotone"
          dataKey="signalStrength"
          stroke={CHART_COLORS.signalStrength}
          strokeWidth={2}
          dot={false}
          connectNulls
        />
        </LineChart>
      </ResponsiveContainer>
    </Box>
  )
}

