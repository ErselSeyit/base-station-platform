import {
  Box,
  Card,
  CardContent,
  CircularProgress,
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
import { useTheme } from '../contexts/ThemeContext'
import { metricsApi, stationApi } from '../services/api'
import { BaseStation, MetricData, MetricType } from '../types'

interface ChartDataPoint {
  date: string
  CPU_USAGE?: number
  MEMORY_USAGE?: number
  POWER_CONSUMPTION?: number
  TEMPERATURE?: number
  count?: number
}

interface AverageData {
  sum: number
  count: number
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

  // Group by date for chart
  const chartData = filteredMetrics.reduce((acc: ChartDataPoint[], metric: MetricData) => {
    if (!metric.timestamp) return acc
    const date = format(new Date(metric.timestamp), 'MMM dd')
    const existing = acc.find((item) => item.date === date)

    if (existing) {
      const key = metric.metricType as keyof ChartDataPoint
      if (key && key !== 'date' && key !== 'count') {
        existing[key] = ((existing[key] as number) || 0) + metric.value
      }
      existing.count = (existing.count || 0) + 1
    } else {
      const newPoint: ChartDataPoint = { date, count: 1 }
      const key = metric.metricType as keyof ChartDataPoint
      if (key && key !== 'date' && key !== 'count') {
        newPoint[key] = metric.value
      }
      acc.push(newPoint)
    }

    return acc
  }, [] as ChartDataPoint[])

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
    return {
      type,
      average: (avgData.sum / avgData.count).toFixed(2),
    }
  })

  if (isLoading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="400px">
        <CircularProgress />
      </Box>
    )
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

      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} md={4}>
          <FormControl fullWidth>
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
        <Grid item xs={12} md={4}>
          <FormControl fullWidth>
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
        <Grid item xs={12} md={4}>
          <FormControl fullWidth>
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

      <Grid container spacing={3}>
        <Grid item xs={12} md={8}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Metrics Trend
              </Typography>
              <ResponsiveContainer width="100%" height={400}>
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" />
                  <YAxis />
                  <Tooltip />
                  <Legend />
                  {metricType === 'all' ? (
                    <>
                      <Line
                        type="monotone"
                        dataKey="CPU_USAGE"
                        stroke="#1976d2"
                        strokeWidth={2}
                        name="CPU Usage (%)"
                      />
                      <Line
                        type="monotone"
                        dataKey="MEMORY_USAGE"
                        stroke="#2e7d32"
                        strokeWidth={2}
                        name="Memory Usage (%)"
                      />
                      <Line
                        type="monotone"
                        dataKey="POWER_CONSUMPTION"
                        stroke="#ed6c02"
                        strokeWidth={2}
                        name="Power (kW)"
                      />
                      <Line
                        type="monotone"
                        dataKey="TEMPERATURE"
                        stroke="#d32f2f"
                        strokeWidth={2}
                        name="Temperature (°C)"
                      />
                    </>
                  ) : (
                    <Line
                      type="monotone"
                      dataKey={metricType}
                      stroke="#1976d2"
                      strokeWidth={2}
                    />
                  )}
                </LineChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Average Values
              </Typography>
              {averageData.length > 0 ? (
                averageData.map((item: { type: string; average: string }) => (
                  <Paper key={item.type} sx={{ p: 2, mb: 2 }}>
                    <Typography variant="body2" color="textSecondary">
                      {item.type}
                    </Typography>
                    <Typography variant="h5" fontWeight={600}>
                      {item.average}
                    </Typography>
                  </Paper>
                ))
              ) : (
                <Typography variant="body2" color="textSecondary">
                  No data available
                </Typography>
              )}
            </CardContent>
          </Card>

          <Card sx={{ mt: 3 }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Summary
              </Typography>
              <Typography variant="body2" color="textSecondary">
                Total Metrics: {filteredMetrics.length}
              </Typography>
              <Typography variant="body2" color="textSecondary">
                Time Range: Last {days} day{days > 1 ? 's' : ''}
              </Typography>
              {selectedStation !== 'all' && (
                <Typography variant="body2" color="textSecondary">
                  Station: {stationsList.find((s: BaseStation) => s.id === selectedStation)?.stationName}
                </Typography>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  )
}

