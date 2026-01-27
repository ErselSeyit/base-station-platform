import { Box, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { format } from 'date-fns'
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
import { metricsApi } from '../services/api'
import { MetricData } from '../types'
import LoadingSpinner from './LoadingSpinner'

interface ChartDataPoint {
  date: string
  cpuUsage?: number
  memoryUsage?: number
  temperature?: number
  signalStrength?: number
}

interface AggregatedValues {
  cpuSum: number
  cpuCount: number
  memorySum: number
  memoryCount: number
  tempSum: number
  tempCount: number
  signalSum: number
  signalCount: number
}

export default function MetricsChart() {
  const { data, isLoading } = useQuery({
    queryKey: ['metrics-chart'],
    queryFn: async () => {
      const response = await metricsApi.getAll({
        startTime: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
      })
      return response.data
    },
  })

  if (isLoading) {
    return <LoadingSpinner />
  }

  const metrics = Array.isArray(data) ? data : []

  // Group metrics by date and calculate averages
  const aggregated: Record<string, AggregatedValues> = {}

  metrics.forEach((metric: MetricData) => {
    if (!metric.timestamp) return
    const date = format(new Date(metric.timestamp), 'MMM dd')

    if (!aggregated[date]) {
      aggregated[date] = {
        cpuSum: 0, cpuCount: 0,
        memorySum: 0, memoryCount: 0,
        tempSum: 0, tempCount: 0,
        signalSum: 0, signalCount: 0
      }
    }

    if (metric.metricType === 'CPU_USAGE') {
      aggregated[date].cpuSum += metric.value
      aggregated[date].cpuCount += 1
    } else if (metric.metricType === 'MEMORY_USAGE') {
      aggregated[date].memorySum += metric.value
      aggregated[date].memoryCount += 1
    } else if (metric.metricType === 'TEMPERATURE') {
      aggregated[date].tempSum += metric.value
      aggregated[date].tempCount += 1
    } else if (metric.metricType === 'SIGNAL_STRENGTH') {
      aggregated[date].signalSum += metric.value
      aggregated[date].signalCount += 1
    }
  })

  // Convert to chart data with averages
  const chartData: ChartDataPoint[] = Object.keys(aggregated)
    .map((date) => {
      const values = aggregated[date]
      return {
        date,
        cpuUsage: values.cpuCount > 0 ? values.cpuSum / values.cpuCount : undefined,
        memoryUsage: values.memoryCount > 0 ? values.memorySum / values.memoryCount : undefined,
        temperature: values.tempCount > 0 ? values.tempSum / values.tempCount : undefined,
        signalStrength: values.signalCount > 0 ? values.signalSum / values.signalCount : undefined,
      }
    })
    .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())

  // Check if we have any data
  const hasData = chartData.some(d =>
    d.cpuUsage !== undefined || d.memoryUsage !== undefined ||
    d.temperature !== undefined || d.signalStrength !== undefined
  )

  if (!hasData) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 300 }}>
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
    <ResponsiveContainer width="100%" height={300}>
      <LineChart data={chartData}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--mono-200)" />
        <XAxis
          dataKey="date"
          tick={{ fontSize: 12, fill: 'var(--mono-600)' }}
          stroke="var(--mono-300)"
        />
        <YAxis
          yAxisId="left"
          domain={[0, 100]}
          tickFormatter={(v) => `${v}%`}
          tick={{ fontSize: 12, fill: 'var(--mono-600)' }}
          stroke="var(--mono-300)"
        />
        <YAxis
          yAxisId="right"
          orientation="right"
          domain={[-100, 100]}
          tick={{ fontSize: 12, fill: 'var(--mono-600)' }}
          stroke="var(--mono-300)"
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
          wrapperStyle={{ color: 'var(--mono-700)' }}
        />
        <Line
          yAxisId="left"
          type="monotone"
          dataKey="cpuUsage"
          stroke="var(--status-active)"
          strokeWidth={2}
          dot={false}
          connectNulls
        />
        <Line
          yAxisId="left"
          type="monotone"
          dataKey="memoryUsage"
          stroke="var(--status-maintenance)"
          strokeWidth={2}
          dot={false}
          connectNulls
        />
        <Line
          yAxisId="right"
          type="monotone"
          dataKey="temperature"
          stroke="#f97316"
          strokeWidth={2}
          dot={false}
          connectNulls
        />
        <Line
          yAxisId="right"
          type="monotone"
          dataKey="signalStrength"
          stroke="#8b5cf6"
          strokeWidth={2}
          dot={false}
          connectNulls
        />
      </LineChart>
    </ResponsiveContainer>
  )
}

