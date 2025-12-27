import { useQuery } from '@tanstack/react-query'
import { format } from 'date-fns' // Consider installing 'date-fns' if you haven't already (npm install date-fns)
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
  CPU_USAGE?: number
  MEMORY_USAGE?: number
  POWER_CONSUMPTION?: number
  TEMPERATURE?: number
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

  // Group metrics by timestamp and type
  const chartData = metrics.reduce((acc: ChartDataPoint[], metric: MetricData) => {
    if (!metric.timestamp) return acc
    const date = format(new Date(metric.timestamp), 'MMM dd')
    const existing = acc.find((item) => item.date === date)

    if (existing) {
      const key = metric.metricType as keyof ChartDataPoint
      if (key && key !== 'date') {
        existing[key] = ((existing[key] as number) || 0) + metric.value
      }
    } else {
      const newPoint: ChartDataPoint = { date }
      const key = metric.metricType as keyof ChartDataPoint
      if (key && key !== 'date') {
        newPoint[key] = metric.value
      }
      acc.push(newPoint)
    }

    return acc
  }, [] as ChartDataPoint[])

  return (
    <ResponsiveContainer width="100%" height={300}>
      <LineChart data={chartData}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="date" />
        <YAxis />
        <Tooltip />
        <Legend />
        <Line type="monotone" dataKey="CPU_USAGE" stroke="#1976d2" strokeWidth={2} />
        <Line type="monotone" dataKey="MEMORY_USAGE" stroke="#2e7d32" strokeWidth={2} />
        <Line type="monotone" dataKey="POWER_CONSUMPTION" stroke="#ed6c02" strokeWidth={2} />
        <Line type="monotone" dataKey="TEMPERATURE" stroke="#d32f2f" strokeWidth={2} />
      </LineChart>
    </ResponsiveContainer>
  )
}

