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
import { useState } from 'react'
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

interface MetricAccumulator {
  date: string
  sums: Record<string, number>
  counts: Record<string, number>
}

const METRIC_CONFIGS = {
  CPU_USAGE: { color: 'var(--status-active)', unit: '%', domain: [0, 100] as [number, number] },
  MEMORY_USAGE: { color: 'var(--status-maintenance)', unit: '%', domain: [0, 100] as [number, number] },
  TEMPERATURE: { color: 'var(--status-offline)', unit: '°C', domain: [0, 100] as [number, number] },
  POWER_CONSUMPTION: { color: 'var(--status-info)', unit: ' kW', domain: [0, 'auto'] as [number, string] },
  SIGNAL_STRENGTH: { color: 'var(--status-active)', unit: ' dBm', domain: [-100, -40] as [number, number] },
  UPTIME: { color: 'var(--status-maintenance)', unit: '%', domain: [0, 100] as [number, number] },
  CONNECTION_COUNT: { color: 'var(--status-info)', unit: '', domain: [0, 'auto'] as [number, string] },
  DATA_THROUGHPUT: { color: 'var(--status-active)', unit: ' Mbps', domain: [0, 'auto'] as [number, string] },
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
          borderColor: 'var(--mono-300)',
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
          borderColor: 'var(--mono-300)',
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
            stroke="var(--mono-300)"
          />
          <YAxis
            domain={domain}
            tickFormatter={(v) => `${typeof v === 'number' ? v.toFixed(0) : v}${unit}`}
            tick={{ fontSize: 11, fill: 'var(--mono-600)' }}
            stroke="var(--mono-300)"
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

  // Convert to averages
  const accumulatorValues: MetricAccumulator[] = Object.values(chartDataAccumulator)
  const chartData: ChartDataPoint[] = accumulatorValues.map((item) => {
    const point: ChartDataPoint = { date: item.date }
    Object.keys(item.sums).forEach((metricType) => {
      const key = metricType as keyof Omit<ChartDataPoint, 'date'>
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
    <Box sx={{ maxWidth: '1400px', margin: '0 auto', padding: '32px 24px' }}>
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
            fontSize: '2.25rem',
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
        <FormControl
          size="small"
          sx={{
            flex: '1 1 200px',
            '& .MuiOutlinedInput-root': {
              background: 'var(--surface-base)',
              borderRadius: '8px',
              color: 'var(--mono-950)',
              '& fieldset': {
                borderColor: 'var(--surface-border)',
              },
              '&:hover fieldset': {
                borderColor: 'var(--mono-300)',
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
          }}
        >
          <InputLabel>Station</InputLabel>
          <Select
            value={selectedStation}
            onChange={(e) => setSelectedStation(e.target.value as number | 'all')}
            label="Station"
            MenuProps={{
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
            }}
          >
            <MenuItem value="all">All Stations</MenuItem>
            {stationsList.map((station: BaseStation) => (
              <MenuItem key={station.id} value={station.id}>
                {station.stationName}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <FormControl
          size="small"
          sx={{
            flex: '1 1 200px',
            '& .MuiOutlinedInput-root': {
              background: 'var(--surface-base)',
              borderRadius: '8px',
              color: 'var(--mono-950)',
              '& fieldset': {
                borderColor: 'var(--surface-border)',
              },
              '&:hover fieldset': {
                borderColor: 'var(--mono-300)',
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
          }}
        >
          <InputLabel>Metric Type</InputLabel>
          <Select
            value={metricType}
            onChange={(e) => setMetricType(e.target.value as MetricType | 'all')}
            label="Metric Type"
            MenuProps={{
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
            }}
          >
            <MenuItem value="all">All Metrics</MenuItem>
            {Object.values(MetricType).map((type) => (
              <MenuItem key={type} value={type}>
                {type.replace('_', ' ')}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <FormControl
          size="small"
          sx={{
            flex: '1 1 200px',
            '& .MuiOutlinedInput-root': {
              background: 'var(--surface-base)',
              borderRadius: '8px',
              color: 'var(--mono-950)',
              '& fieldset': {
                borderColor: 'var(--surface-border)',
              },
              '&:hover fieldset': {
                borderColor: 'var(--mono-300)',
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
          }}
        >
          <InputLabel>Time Range</InputLabel>
          <Select
            value={days}
            onChange={(e) => setDays(Number(e.target.value))}
            label="Time Range"
            MenuProps={{
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
            }}
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
          gridTemplateColumns: 'repeat(auto-fit, minmax(450px, 1fr))',
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
