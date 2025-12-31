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

interface ChartDataPoint {
  date: string
  cpuUsage?: number
  memoryUsage?: number
}

interface AggregatedValues {
  cpuSum: number
  cpuCount: number
  memorySum: number
  memoryCount: number
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
    return <div>Loading chart data...</div>
  }

  const metrics = Array.isArray(data) ? data : []

  // Group metrics by date and calculate averages (only CPU and Memory - true percentages)
  const aggregated: Record<string, AggregatedValues> = {}
  
  metrics.forEach((metric: MetricData) => {
    if (!metric.timestamp) return
    const date = format(new Date(metric.timestamp), 'MMM dd')
    
    if (!aggregated[date]) {
      aggregated[date] = { cpuSum: 0, cpuCount: 0, memorySum: 0, memoryCount: 0 }
    }

    if (metric.metricType === 'CPU_USAGE') {
      aggregated[date].cpuSum += metric.value
      aggregated[date].cpuCount += 1
    } else if (metric.metricType === 'MEMORY_USAGE') {
      aggregated[date].memorySum += metric.value
      aggregated[date].memoryCount += 1
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
      }
    })
    .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())

  // Check if we have any data
  const hasData = chartData.some(d => d.cpuUsage !== undefined || d.memoryUsage !== undefined)

  if (!hasData) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 300 }}>
        <Typography color="text.secondary">No metrics data available</Typography>
      </Box>
    )
  }

  return (
    <ResponsiveContainer width="100%" height={300}>
      <LineChart data={chartData}>
        <CartesianGrid strokeDasharray="3 3" opacity={0.3} />
        <XAxis dataKey="date" fontSize={12} />
        <YAxis 
          domain={[0, 100]} 
          tickFormatter={(v) => `${v}%`} 
          fontSize={12}
        />
        <Tooltip 
          formatter={(value: number, name: string) => {
            const label = name === 'cpuUsage' ? 'CPU Usage' : 'Memory Usage'
            return [`${value.toFixed(1)}%`, label]
          }}
          labelStyle={{ fontWeight: 600 }}
          contentStyle={{ borderRadius: 8 }}
        />
        <Legend 
          formatter={(value) => value === 'cpuUsage' ? 'CPU Usage' : 'Memory Usage'} 
        />
        <Line 
          type="monotone" 
          dataKey="cpuUsage" 
          stroke="#1976d2" 
          strokeWidth={2} 
          dot={false}
          connectNulls
        />
        <Line 
          type="monotone" 
          dataKey="memoryUsage" 
          stroke="#2e7d32" 
          strokeWidth={2} 
          dot={false}
          connectNulls
        />
      </LineChart>
    </ResponsiveContainer>
  )
}

