import { Box, Typography } from '@mui/material'
import { motion } from 'framer-motion'
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip as RechartsTooltip,
  XAxis,
  YAxis,
} from 'recharts'

interface ChartDataPoint {
  date: string
  [key: string]: number | string | undefined
}

interface MetricForChart {
  type: string
  config: {
    color: string
  }
}

interface MetricsCategoryChartProps {
  title: string
  metrics: MetricForChart[]
  chartData: ChartDataPoint[]
  days: number
  delay: number
}

export default function MetricsCategoryChart({
  title,
  metrics,
  chartData,
  days,
  delay,
}: MetricsCategoryChartProps) {
  return (
    <Box
      component={motion.div}
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay, duration: 0.35 }}
      sx={{
        background: 'var(--surface-base)',
        border: '1px solid var(--surface-border)',
        borderRadius: '14px',
        padding: '24px',
      }}
    >
      <Typography sx={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--mono-950)', mb: '4px' }}>
        {title} Trends
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
            interval={days <= 7 ? 0 : 'preserveStartEnd'}
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
  )
}
