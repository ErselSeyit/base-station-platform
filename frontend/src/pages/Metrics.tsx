import {
  Box,
  Card,
  CardContent,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Typography,
} from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { format, subDays } from 'date-fns'
import { useState } from 'react'
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
import LoadingSpinner from '../components/LoadingSpinner'
import { useTheme } from '../contexts/ThemeContext'
import { metricsApi, stationApi } from '../services/api'
import { BaseStation, MetricData, MetricType } from '../types'

interface ChartDataPoint {
  date: string
  CPU_USAGE?: number
  MEMORY_USAGE?: number
  POWER_CONSUMPTION?: number
  TEMPERATURE?: number
  SIGNAL_STRENGTH?: number
  UPTIME?: number
  CONNECTION_COUNT?: number
  DATA_THROUGHPUT?: number
}

interface AverageData {
  sum: number
  count: number
}

interface MetricAccumulator {
  date: string
  sums: Record<string, number>
  counts: Record<string, number>
}

export default function Metrics() {
  const { mode } = useTheme()
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
  const { data: metrics, isLoading } = useQuery({
    queryKey: ['metrics', days],
    queryFn: async () => {
      const response = await metricsApi.getAll({
        startTime: subDays(new Date(), days).toISOString(),
      })
      return response.data
    },
    refetchInterval: 30000,
  })

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

  // Group by date for chart - properly average values
  const chartDataAccumulator = filteredMetrics.reduce((acc: Record<string, MetricAccumulator>, metric: MetricData) => {
    if (!metric.timestamp) return acc
    const date = format(new Date(metric.timestamp), 'MMM dd')
    
    if (!acc[date]) {
      acc[date] = { date, sums: {}, counts: {} }
    }
    
    const metricType = metric.metricType
    acc[date].sums[metricType] = (acc[date].sums[metricType] || 0) + metric.value
    acc[date].counts[metricType] = (acc[date].counts[metricType] || 0) + 1
    
    return acc
  }, {})

  // Convert accumulated sums to averages
  const accumulatorValues: MetricAccumulator[] = Object.values(chartDataAccumulator)
  const chartData: ChartDataPoint[] = accumulatorValues.map((item) => {
    const point: ChartDataPoint = { date: item.date }
    Object.keys(item.sums).forEach((metricType) => {
      const key = metricType as keyof Omit<ChartDataPoint, 'date'>
      let avgValue = item.sums[metricType] / item.counts[metricType]
      // Convert power consumption from W to kW for display consistency
      if (metricType === 'POWER_CONSUMPTION') {
        avgValue = avgValue / 1000
      }
      ;(point as unknown as Record<string, number>)[key] = avgValue
    })
    return point
  })

  // Calculate averages
  const averages = filteredMetrics.reduce((acc: Record<string, AverageData>, metric: MetricData) => {
    if (!acc[metric.metricType]) {
      acc[metric.metricType] = { sum: 0, count: 0 }
    }
    acc[metric.metricType].sum += metric.value
    acc[metric.metricType].count += 1
    return acc
  }, {})

  const averageData = Object.entries(averages).map(([type, data]) => {
    const avgData = data as AverageData
    let avgValue = avgData.sum / avgData.count
    // Convert power consumption from W to kW for display
    if (type === 'POWER_CONSUMPTION') {
      avgValue = avgValue / 1000
    }
    return {
      type,
      average: avgValue.toFixed(2),
    }
  })

  if (isLoading) {
    return <LoadingSpinner />
  }

  return (
    <Box>
      <Box sx={{ mb: 4 }}>
        <Typography
          variant="h3"
          gutterBottom
          sx={{
            fontWeight: 700,
            mb: 1,
            background: mode === 'dark'
              ? 'linear-gradient(135deg, #64b5f6 0%, #90caf9 50%, #ba68c8 100%)'
              : 'linear-gradient(135deg, #1976d2 0%, #42a5f5 50%, #9c27b0 100%)',
            backgroundClip: 'text',
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent',
          }}
        >
          Metrics & Analytics
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Real-time performance metrics and data visualization
        </Typography>
      </Box>

      {/* Filters Row */}
      <Grid container spacing={2} sx={{ mb: 2 }}>
        <Grid item xs={12} sm={4}>
          <FormControl fullWidth size="small">
            <InputLabel id="metrics-station-filter-label" htmlFor="metrics-station-native">
              Station
            </InputLabel>
            <Select
              id="metrics-station-filter"
              name="stationFilter"
              labelId="metrics-station-filter-label"
              value={selectedStation}
              onChange={(e) => setSelectedStation(e.target.value as number | 'all')}
              label="Station"
              inputProps={{ id: 'metrics-station-native', name: 'stationFilter' }}
            >
              <MenuItem value="all">All Stations</MenuItem>
              {stationsList.map((station: BaseStation) => (
                <MenuItem key={station.id} value={station.id}>
                  {station.stationName}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Grid>
        <Grid item xs={12} sm={4}>
          <FormControl fullWidth size="small">
            <InputLabel id="metrics-type-filter-label" htmlFor="metrics-type-native">
              Metric Type
            </InputLabel>
            <Select
              id="metrics-type-filter"
              name="metricTypeFilter"
              labelId="metrics-type-filter-label"
              value={metricType}
              onChange={(e) => setMetricType(e.target.value as MetricType | 'all')}
              label="Metric Type"
              inputProps={{ id: 'metrics-type-native', name: 'metricTypeFilter' }}
            >
              <MenuItem value="all">All Metrics</MenuItem>
              {Object.values(MetricType).map((type) => (
                <MenuItem key={type} value={type}>
                  {type}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Grid>
        <Grid item xs={12} sm={4}>
          <FormControl fullWidth size="small">
            <InputLabel id="metrics-time-range-label" htmlFor="metrics-time-native">
              Time Range
            </InputLabel>
            <Select
              id="metrics-time-range"
              name="timeRange"
              labelId="metrics-time-range-label"
              value={days}
              onChange={(e) => setDays(Number(e.target.value))}
              label="Time Range"
              inputProps={{ id: 'metrics-time-native', name: 'timeRange' }}
            >
              <MenuItem value={1}>Last 24 Hours</MenuItem>
              <MenuItem value={7}>Last 7 Days</MenuItem>
              <MenuItem value={30}>Last 30 Days</MenuItem>
              <MenuItem value={90}>Last 90 Days</MenuItem>
            </Select>
          </FormControl>
        </Grid>
      </Grid>

      {/* Average Values Row */}
      <Box sx={{ mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          Average Values
        </Typography>
        {averageData.length > 0 ? (
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
            {averageData.map((item: { type: string; average: string }) => {
              const getUnit = (type: string) => {
                switch (type) {
                  case 'CPU_USAGE':
                  case 'MEMORY_USAGE':
                  case 'UPTIME':
                    return '%'
                  case 'TEMPERATURE':
                    return '°C'
                  case 'POWER_CONSUMPTION':
                    return ' kW'
                  case 'SIGNAL_STRENGTH':
                    return ' dBm'
                  case 'CONNECTION_COUNT':
                    return ''
                  case 'DATA_THROUGHPUT':
                    return ' Mbps'
                  default:
                    return ''
                }
              }
              const getColor = (type: string) => {
                switch (type) {
                  case 'CPU_USAGE':
                    return '#1976d2'
                  case 'MEMORY_USAGE':
                    return '#2e7d32'
                  case 'TEMPERATURE':
                    return '#d32f2f'
                  case 'POWER_CONSUMPTION':
                    return '#ed6c02'
                  case 'SIGNAL_STRENGTH':
                    return '#00bcd4'
                  case 'UPTIME':
                    return '#9c27b0'
                  case 'CONNECTION_COUNT':
                    return '#673ab7'
                  case 'DATA_THROUGHPUT':
                    return '#4caf50'
                  default:
                    return '#666'
                }
              }
              return (
                <Paper key={item.type} sx={{ p: 1.5, flex: '1 1 auto', minWidth: 120, borderLeft: `4px solid ${getColor(item.type)}` }}>
                  <Typography variant="caption" color="textSecondary" sx={{ textTransform: 'capitalize' }}>
                    {item.type.split('_').join(' ').toLowerCase()}
                  </Typography>
                  <Typography variant="h6" fontWeight={600}>
                    {item.average}{getUnit(item.type)}
                  </Typography>
                </Paper>
              )
            })}
          </Box>
        ) : (
          <Typography variant="body2" color="text.secondary">
            No data available
          </Typography>
        )}
      </Box>

      <Grid container spacing={3}>
        {/* Main Chart - Percentage Metrics */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Performance Metrics (CPU, Memory, Temperature, Uptime)
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Average CPU and Memory usage per day
              </Typography>
              <ResponsiveContainer width="100%" height={300}>
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" />
                  <YAxis domain={[0, 100]} tickFormatter={(v) => `${v}%`} />
                  <Tooltip formatter={(value: number) => `${value.toFixed(1)}%`} />
                  <Legend />
                  {(metricType === 'all' || metricType === 'CPU_USAGE') && (
                    <Line
                      type="monotone"
                      dataKey="CPU_USAGE"
                      stroke="#1976d2"
                      strokeWidth={2}
                      name="CPU Usage"
                      dot={false}
                      connectNulls
                    />
                  )}
                  {(metricType === 'all' || metricType === 'MEMORY_USAGE') && (
                    <Line
                      type="monotone"
                      dataKey="MEMORY_USAGE"
                      stroke="#2e7d32"
                      strokeWidth={2}
                      name="Memory Usage"
                      dot={false}
                      connectNulls
                    />
                  )}
                  {(metricType === 'all' || metricType === 'UPTIME') && (
                    <Line
                      type="monotone"
                      dataKey="UPTIME"
                      stroke="#9c27b0"
                      strokeWidth={2}
                      name="Uptime"
                      dot={false}
                      connectNulls
                    />
                  )}
                </LineChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>

        {/* Temperature Chart - Separate scale */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Temperature
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Average equipment temperature per day
              </Typography>
              <ResponsiveContainer width="100%" height={300}>
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" />
                  <YAxis domain={[0, 100]} tickFormatter={(v) => `${v}°C`} />
                  <Tooltip formatter={(value: number) => `${value.toFixed(1)}°C`} />
                  <Legend />
                  <Line
                    type="monotone"
                    dataKey="TEMPERATURE"
                    stroke="#d32f2f"
                    strokeWidth={2}
                    name="Temperature"
                    dot={false}
                    connectNulls
                  />
                </LineChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>

        {/* Secondary Chart - Power & Throughput */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Power Consumption
              </Typography>
              <ResponsiveContainer width="100%" height={250}>
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" />
                  <YAxis domain={[0, 'auto']} tickFormatter={(v) => `${v.toFixed(1)} kW`} />
                  <Tooltip formatter={(value: number) => `${value.toFixed(2)} kW`} />
                  <Legend />
                  <Line
                    type="monotone"
                    dataKey="POWER_CONSUMPTION"
                    stroke="#ed6c02"
                    strokeWidth={2}
                    name="Power (W)"
                    dot={false}
                    connectNulls
                  />
                </LineChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>

        {/* Signal Strength Chart */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Signal Strength
              </Typography>
              <ResponsiveContainer width="100%" height={250}>
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" />
                  <YAxis domain={[-100, -40]} tickFormatter={(v) => `${v} dBm`} />
                  <Tooltip formatter={(value: number) => `${value.toFixed(1)} dBm`} />
                  <Legend />
                  <Line
                    type="monotone"
                    dataKey="SIGNAL_STRENGTH"
                    stroke="#00bcd4"
                    strokeWidth={2}
                    name="Signal (dBm)"
                    dot={false}
                    connectNulls
                  />
                </LineChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>

        {/* Connection Count Chart */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Active Connections
              </Typography>
              <ResponsiveContainer width="100%" height={200}>
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" tick={{ fontSize: 10 }} />
                  <YAxis domain={[0, 'auto']} />
                  <Tooltip />
                  <Line
                    type="monotone"
                    dataKey="CONNECTION_COUNT"
                    stroke="#673ab7"
                    strokeWidth={2}
                    name="Connections"
                    dot={false}
                    connectNulls
                  />
                </LineChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>

        {/* Throughput Chart */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Data Throughput
              </Typography>
              <ResponsiveContainer width="100%" height={200}>
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" tick={{ fontSize: 10 }} />
                  <YAxis domain={[0, 'auto']} tickFormatter={(v) => `${v.toFixed(0)} Mbps`} />
                  <Tooltip formatter={(value: number) => `${value.toFixed(0)} Mbps`} />
                  <Line
                    type="monotone"
                    dataKey="DATA_THROUGHPUT"
                    stroke="#4caf50"
                    strokeWidth={2}
                    name="Throughput (Mbps)"
                    dot={false}
                    connectNulls
                  />
                </LineChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>

        {/* Summary Card */}
        <Grid item xs={12}>
          <Card>
            <CardContent sx={{ py: 2 }}>
              <Typography variant="h6" gutterBottom>
                Summary
              </Typography>
              <Box sx={{ display: 'flex', gap: 4 }}>
                <Typography variant="body2" color="textSecondary">
                  Total Metrics: <strong>{filteredMetrics.length.toLocaleString()}</strong>
                </Typography>
                <Typography variant="body2" color="textSecondary">
                  Time Range: <strong>Last {days} day{days > 1 ? 's' : ''}</strong>
                </Typography>
                {selectedStation !== 'all' && (
                  <Typography variant="body2" color="textSecondary">
                    Station: <strong>{stationsList.find((s: BaseStation) => s.id === selectedStation)?.stationName}</strong>
                  </Typography>
                )}
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  )
}

