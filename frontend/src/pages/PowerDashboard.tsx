import {
  BatteryChargingFull as BatteryIcon,
  Bolt as PowerIcon,
  DeviceThermostat as TempIcon,
  Air as FanIcon,
  Warning as WarningIcon,
  CheckCircle as HealthyIcon,
  Refresh as RefreshIcon,
} from '@mui/icons-material'
import {
  Box,
  Button,
  Grid,
  LinearProgress,
  Paper,
  Typography,
} from '@mui/material'
import { motion } from 'framer-motion'
import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import ErrorDisplay from '../components/ErrorDisplay'
import LoadingSpinner from '../components/LoadingSpinner'
import MetricsChart from '../components/MetricsChart'
import { CARD_STATUS_STYLES, CSS_VARS, POLLING_INTERVALS } from '../constants/designSystem'
import { metricsApi, stationApi } from '../services/api'
import { BaseStation, MetricData, StationStatus } from '../types'
import { ensureArray, avg, sum } from '../utils/arrayUtils'
import {
  getWorstHealthStatus,
  getPowerStatus,
  getBatteryStatus,
  getTempStatus,
  getFanStatus,
  getChargingStatusLabel,
  getCoolingStatusLabel,
  POWER_THRESHOLDS,
  type HealthStatus,
} from '../utils/metricEvaluators'
import { getErrorMessage } from '../utils/statusHelpers'

// ============================================================================
// Types
// ============================================================================

interface PowerMetrics {
  stationId: number
  stationName: string
  location: string
  status: StationStatus
  power: {
    consumption: number // kW
    voltage: number
    current: number
  }
  battery: {
    soc: number // State of charge %
    voltage: number
    temperature: number
  }
  environment: {
    temperature: number
    humidity: number
    fanSpeed: number
  }
  solar?: {
    power: number
    voltage: number
  }
  generator?: {
    fuelLevel: number
    runtime: number
  }
}

// ============================================================================
// Constants
// ============================================================================

// Use CARD_STATUS_STYLES from designSystem.ts for dark mode compatibility

// Local thresholds (summary-specific, not shared with other components)
const LOCAL_THRESHOLDS = {
  TOTAL_POWER_WARNING_KW: 20,
  BATTERY_LOW_WARNING_SOC: 30,
  TEMP_SUMMARY_WARNING: 70,
} as const

// Cost calculation constants
const COST_CONSTANTS = {
  HOURS_PER_MONTH: 720,
  ELECTRICITY_RATE_USD_PER_KWH: 0.12,
} as const

// Default values for metrics when not available
const DEFAULT_METRICS = {
  VOLTAGE_V: 48,
  CURRENT_A: 0,
  BATTERY_SOC_PERCENT: 100,
  BATTERY_TEMP_C: 25,
  AMBIENT_TEMP_C: 35,
  HUMIDITY_PERCENT: 50,
  FAN_SPEED_RPM: 3000,
} as const

// ============================================================================
// Helper Functions
// ============================================================================

function processStationMetrics(
  stations: BaseStation[],
  metrics: MetricData[]
): PowerMetrics[] {
  // Group metrics by station (immutable collection)
  const metricsByStation = metrics.reduce<Map<number, Map<string, number>>>(
    (acc, m) => {
      if (!acc.has(m.stationId)) {
        acc.set(m.stationId, new Map())
      }
      // Use latest value for each metric type
      acc.get(m.stationId)!.set(m.metricType, m.value)
      return acc
    },
    new Map()
  )

  // Create PowerMetrics immutably for each station
  return stations
    .filter((station): station is BaseStation & { id: number } => station.id !== undefined)
    .map((station) => {
      const stationMetrics = metricsByStation.get(station.id)
      const powerConsumption = stationMetrics?.get('POWER_CONSUMPTION')
      const temperature = stationMetrics?.get('TEMPERATURE')
      const fanSpeed = stationMetrics?.get('FAN_SPEED')

      return {
        stationId: station.id,
        stationName: station.stationName,
        location: station.location,
        status: station.status ?? StationStatus.OFFLINE,
        power: {
          consumption: (powerConsumption ?? station.powerConsumption ?? 0) / 1000,
          voltage: DEFAULT_METRICS.VOLTAGE_V,
          current: DEFAULT_METRICS.CURRENT_A,
        },
        battery: {
          soc: DEFAULT_METRICS.BATTERY_SOC_PERCENT,
          voltage: DEFAULT_METRICS.VOLTAGE_V,
          temperature: DEFAULT_METRICS.BATTERY_TEMP_C,
        },
        environment: {
          temperature: temperature ?? DEFAULT_METRICS.AMBIENT_TEMP_C,
          humidity: DEFAULT_METRICS.HUMIDITY_PERCENT,
          fanSpeed: fanSpeed ?? DEFAULT_METRICS.FAN_SPEED_RPM,
        },
      }
    })
}

// ============================================================================
// Components
// ============================================================================

interface MetricCardProps {
  label: string
  value: number | string
  unit: string
  status: HealthStatus
  icon: React.ElementType
  subtitle?: string
  progress?: { value: number; max: number }
}

function MetricCard({ label, value, unit, status, icon: Icon, subtitle, progress }: Readonly<MetricCardProps>) {
  const styles = CARD_STATUS_STYLES[status]

  return (
    <Paper
      component={motion.div}
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      sx={{
        p: 2.5,
        borderRadius: 3,
        background: 'var(--surface-base)',
        border: '1px solid var(--surface-border)',
        position: 'relative',
        overflow: 'hidden',
        height: '100%',
        '&:hover': { borderColor: styles.border },
      }}
    >
      {/* Status indicator bar */}
      <Box
        sx={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          height: 4,
          background: styles.color,
          opacity: 0.8,
        }}
      />

      <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 1.5 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Box
            sx={{
              width: 36,
              height: 36,
              borderRadius: 2,
              background: styles.bg,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Icon sx={{ fontSize: 20, color: styles.color }} />
          </Box>
          <Box>
            <Typography sx={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--mono-600)' }}>
              {label}
            </Typography>
            {subtitle && (
              <Typography sx={{ fontSize: '0.75rem', color: 'var(--mono-400)' }}>
                {subtitle}
              </Typography>
            )}
          </Box>
        </Box>
        <Box
          sx={{
            px: 1,
            py: 0.5,
            borderRadius: 1,
            background: styles.bg,
            border: `1px solid ${styles.border}`,
          }}
        >
          <Typography sx={{ fontSize: '0.625rem', fontWeight: 700, color: styles.color, textTransform: 'uppercase' }}>
            {styles.label}
          </Typography>
        </Box>
      </Box>

      <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.5, mb: progress ? 1.5 : 0 }}>
        <Typography
          sx={{
            fontFamily: "'JetBrains Mono'",
            fontWeight: 800,
            fontSize: '2rem',
            color: 'var(--mono-950)',
            lineHeight: 1,
          }}
        >
          {typeof value === 'number' ? value.toFixed(1) : value}
        </Typography>
        <Typography sx={{ fontSize: '1rem', fontWeight: 600, color: 'var(--mono-400)' }}>
          {unit}
        </Typography>
      </Box>

      {progress && (
        <LinearProgress
          variant="determinate"
          value={Math.min(100, (progress.value / progress.max) * 100)}
          sx={{
            height: 6,
            borderRadius: 3,
            backgroundColor: 'var(--surface-subtle)',
            '& .MuiLinearProgress-bar': { backgroundColor: styles.color, borderRadius: 3 },
          }}
        />
      )}
    </Paper>
  )
}

interface SummaryCardProps {
  title: string
  icon: React.ElementType
  color: string
  stats: { label: string; value: string | number }[]
  status: HealthStatus
}

function SummaryCard({ title, icon: Icon, color, stats, status }: Readonly<SummaryCardProps>) {
  const styles = CARD_STATUS_STYLES[status]

  return (
    <Paper
      component={motion.div}
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      sx={{
        p: 3,
        borderRadius: 3,
        background: 'var(--surface-base)',
        border: '1px solid var(--surface-border)',
        height: '100%',
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2.5 }}>
        <Box
          sx={{
            width: 48,
            height: 48,
            borderRadius: 2,
            // Use status bg from global styles based on card status
            background: styles.bg,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Icon sx={{ fontSize: 28, color }} />
        </Box>
        <Box sx={{ flex: 1 }}>
          <Typography sx={{ fontWeight: 700, fontSize: '1.125rem', color: 'var(--mono-950)' }}>{title}</Typography>
        </Box>
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.5,
            px: 1.5,
            py: 0.5,
            borderRadius: 2,
            background: styles.bg,
            border: `1px solid ${styles.border}`,
          }}
        >
          {status === 'healthy' ? (
            <HealthyIcon sx={{ fontSize: 14, color: styles.color }} />
          ) : (
            <WarningIcon sx={{ fontSize: 14, color: styles.color }} />
          )}
          <Typography sx={{ fontSize: '0.75rem', fontWeight: 600, color: styles.color }}>
            {styles.label}
          </Typography>
        </Box>
      </Box>

      <Grid container spacing={2}>
        {stats.map((stat) => (
          <Grid item xs={6} key={stat.label}>
            <Typography sx={{ fontSize: '0.75rem', color: 'var(--mono-500)', mb: 0.5 }}>
              {stat.label}
            </Typography>
            <Typography sx={{ fontFamily: "'JetBrains Mono'", fontWeight: 700, fontSize: '1.25rem', color: 'var(--mono-950)' }}>
              {stat.value}
            </Typography>
          </Grid>
        ))}
      </Grid>
    </Paper>
  )
}

interface StationPowerRowProps {
  station: PowerMetrics
}

function StationPowerRow({ station }: Readonly<StationPowerRowProps>) {
  const powerStatus = getPowerStatus(station.power.consumption)
  const tempStatus = getTempStatus(station.environment.temperature)
  const fanStatus = getFanStatus(station.environment.fanSpeed)
  const batteryStatus = getBatteryStatus(station.battery.soc)

  const overallStatus = getWorstHealthStatus([powerStatus, tempStatus, batteryStatus])

  const styles = CARD_STATUS_STYLES[overallStatus]

  return (
    <Paper
      component={motion.div}
      initial={{ opacity: 0, x: -10 }}
      animate={{ opacity: 1, x: 0 }}
      sx={{
        p: 2,
        mb: 1.5,
        borderRadius: 2,
        background: 'var(--surface-base)',
        border: `1px solid ${styles.border}`,
        display: 'flex',
        alignItems: 'center',
        gap: 3,
        '&:hover': { boxShadow: `0 4px 12px ${styles.shadow}` },
      }}
    >
      {/* Station Info */}
      <Box sx={{ minWidth: 180 }}>
        <Typography sx={{ fontWeight: 600, fontSize: '0.9375rem', color: 'var(--mono-950)' }}>{station.stationName}</Typography>
        <Typography sx={{ fontSize: '0.8125rem', color: 'var(--mono-500)' }}>
          {station.location}
        </Typography>
      </Box>

      {/* Power */}
      <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
        <PowerIcon sx={{ fontSize: 18, color: CARD_STATUS_STYLES[powerStatus].color }} />
        <Box>
          <Typography sx={{ fontSize: '0.6875rem', color: 'var(--mono-400)' }}>Power</Typography>
          <Typography sx={{ fontFamily: "'JetBrains Mono'", fontWeight: 600, color: 'var(--mono-950)' }}>
            {station.power.consumption.toFixed(2)} kW
          </Typography>
        </Box>
      </Box>

      {/* Temperature */}
      <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
        <TempIcon sx={{ fontSize: 18, color: CARD_STATUS_STYLES[tempStatus].color }} />
        <Box>
          <Typography sx={{ fontSize: '0.6875rem', color: 'var(--mono-400)' }}>Temp</Typography>
          <Typography sx={{ fontFamily: "'JetBrains Mono'", fontWeight: 600, color: 'var(--mono-950)' }}>
            {station.environment.temperature.toFixed(0)}°C
          </Typography>
        </Box>
      </Box>

      {/* Fan Speed */}
      <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
        <FanIcon sx={{ fontSize: 18, color: CARD_STATUS_STYLES[fanStatus].color }} />
        <Box>
          <Typography sx={{ fontSize: '0.6875rem', color: 'var(--mono-400)' }}>Fan</Typography>
          <Typography sx={{ fontFamily: "'JetBrains Mono'", fontWeight: 600, color: 'var(--mono-950)' }}>
            {station.environment.fanSpeed.toFixed(0)} RPM
          </Typography>
        </Box>
      </Box>

      {/* Battery */}
      <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
        <BatteryIcon sx={{ fontSize: 18, color: CARD_STATUS_STYLES[batteryStatus].color }} />
        <Box>
          <Typography sx={{ fontSize: '0.6875rem', color: 'var(--mono-400)' }}>Battery</Typography>
          <Typography sx={{ fontFamily: "'JetBrains Mono'", fontWeight: 600, color: 'var(--mono-950)' }}>
            {station.battery.soc.toFixed(0)}%
          </Typography>
        </Box>
      </Box>

      {/* Status */}
      <Box
        sx={{
          px: 1.5,
          py: 0.5,
          borderRadius: 1.5,
          background: styles.bg,
          border: `1px solid ${styles.border}`,
        }}
      >
        <Typography sx={{ fontSize: '0.6875rem', fontWeight: 700, color: styles.color, textTransform: 'uppercase' }}>
          {styles.label}
        </Typography>
      </Box>
    </Paper>
  )
}

// ============================================================================
// Main Component
// ============================================================================

export default function PowerDashboard() {
  // Fetch stations
  const { data: stationsData, isLoading: stationsLoading, error: stationsError } = useQuery({
    queryKey: ['stations'],
    queryFn: async () => {
      const response = await stationApi.getAll()
      return response.data
    },
  })

  // Fetch metrics
  const {
    data: metricsData,
    isLoading: metricsLoading,
    error: metricsError,
    refetch,
  } = useQuery({
    queryKey: ['power-metrics'],
    queryFn: async () => {
      const response = await metricsApi.getAll({
        startTime: new Date(Date.now() - 3600000).toISOString(),
      })
      return response.data
    },
    refetchInterval: POLLING_INTERVALS.NORMAL,
  })

  // Process metrics
  const stationMetrics = useMemo(() => {
    const stations = ensureArray(stationsData as BaseStation[])
    const metrics = ensureArray(metricsData as MetricData[])
    return processStationMetrics(stations, metrics)
  }, [stationsData, metricsData])

  // Memoize active stations filter - used in summary calculation
  const activeStations = useMemo(
    () => stationMetrics.filter(s => s.status === StationStatus.ACTIVE),
    [stationMetrics]
  )

  // Calculate summaries
  const summary = useMemo(() => {
    const totalPower = sum(activeStations, s => s.power.consumption)
    const avgTemp = avg(activeStations, s => s.environment.temperature)
    const avgBattery = avg(activeStations, s => s.battery.soc)
    const criticalCount = activeStations.filter(
      s => getTempStatus(s.environment.temperature) === 'critical' || getPowerStatus(s.power.consumption) === 'critical'
    ).length

    // Additional derived metrics
    const avgFanSpeed = avg(activeStations, s => s.environment.fanSpeed)
    const sitesOnBattery = activeStations.filter(s => s.battery.soc < DEFAULT_METRICS.BATTERY_SOC_PERCENT).length
    const chargingStatus = getChargingStatusLabel(avgBattery)
    const coolingStatus = getCoolingStatusLabel(avgFanSpeed)

    return {
      totalPower,
      avgTemp,
      avgBattery,
      criticalCount,
      activeCount: activeStations.length,
      avgFanSpeed,
      sitesOnBattery,
      chargingStatus,
      coolingStatus,
    }
  }, [activeStations])

  if (stationsLoading || metricsLoading) {
    return <LoadingSpinner />
  }

  const error = stationsError || metricsError
  if (error) {
    return <ErrorDisplay title="Failed to load power data" message={getErrorMessage(error)} />
  }

  return (
    <Box sx={{ maxWidth: 1600, mx: 'auto', p: { xs: 2, sm: 3, md: 4 } }}>
      {/* Header */}
      <Box
        component={motion.div}
        initial={{ opacity: 0, y: -12 }}
        animate={{ opacity: 1, y: 0 }}
        sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3 }}
      >
        <Box>
          <Typography variant="h1" sx={{ fontSize: { xs: '1.5rem', md: '2rem' }, fontWeight: 800, color: 'var(--mono-950)' }}>
            Power & Environmental
          </Typography>
          <Typography sx={{ color: 'var(--mono-500)', mt: 0.5 }}>
            Power consumption, thermal, and environmental monitoring
          </Typography>
        </Box>
        <Button
          aria-label="Refresh power metrics data"
          startIcon={<RefreshIcon />}
          onClick={() => refetch()}
          sx={{
            background: 'var(--surface-subtle)',
            color: 'var(--mono-700)',
            '&:hover': { background: 'var(--surface-hover)' },
          }}
        >
          Refresh
        </Button>
      </Box>

      {/* Summary Cards */}
      <Grid container spacing={3} sx={{ mb: 4 }}>
        <Grid item xs={12} md={4}>
          <SummaryCard
            title="Power Consumption"
            icon={PowerIcon}
            color={CSS_VARS.colorPurple500}
            status={summary.totalPower > LOCAL_THRESHOLDS.TOTAL_POWER_WARNING_KW ? 'warning' : 'healthy'}
            stats={[
              { label: 'Total Power', value: `${summary.totalPower.toFixed(1)} kW` },
              { label: 'Active Sites', value: summary.activeCount },
              { label: 'Avg per Site', value: `${(summary.totalPower / (summary.activeCount || 1)).toFixed(2)} kW` },
              { label: 'Est. Monthly', value: `$${(summary.totalPower * COST_CONSTANTS.HOURS_PER_MONTH * COST_CONSTANTS.ELECTRICITY_RATE_USD_PER_KWH).toFixed(0)}` },
            ]}
          />
        </Grid>
        <Grid item xs={12} md={4}>
          <SummaryCard
            title="Thermal Status"
            icon={TempIcon}
            color={CSS_VARS.colorAmber500}
            status={summary.avgTemp > LOCAL_THRESHOLDS.TEMP_SUMMARY_WARNING ? 'warning' : 'healthy'}
            stats={[
              { label: 'Avg Temperature', value: `${summary.avgTemp.toFixed(1)}°C` },
              { label: 'Critical Sites', value: summary.criticalCount },
              { label: 'Max Threshold', value: `${POWER_THRESHOLDS.TEMP_WARNING_MAX}°C` },
              { label: 'Cooling Status', value: summary.coolingStatus },
            ]}
          />
        </Grid>
        <Grid item xs={12} md={4}>
          <SummaryCard
            title="Battery Backup"
            icon={BatteryIcon}
            color={CSS_VARS.statusActive}
            status={summary.avgBattery < LOCAL_THRESHOLDS.BATTERY_LOW_WARNING_SOC ? 'warning' : 'healthy'}
            stats={[
              { label: 'Avg Charge', value: `${summary.avgBattery.toFixed(0)}%` },
              { label: 'Sites on Battery', value: summary.sitesOnBattery },
              { label: 'Est. Backup Time', value: 'N/A' },
              { label: 'Charging Status', value: summary.chargingStatus },
            ]}
          />
        </Grid>
      </Grid>

      {/* Network-wide Metrics */}
      <Grid container spacing={2} sx={{ mb: 4 }}>
        <Grid item xs={6} md={3}>
          <MetricCard
            label="Total Power Draw"
            value={summary.totalPower}
            unit="kW"
            status={summary.totalPower > LOCAL_THRESHOLDS.TOTAL_POWER_WARNING_KW ? 'warning' : 'healthy'}
            icon={PowerIcon}
            subtitle="All active sites"
          />
        </Grid>
        <Grid item xs={6} md={3}>
          <MetricCard
            label="Average Temperature"
            value={summary.avgTemp}
            unit="°C"
            status={getTempStatus(summary.avgTemp)}
            icon={TempIcon}
            subtitle="Network average"
          />
        </Grid>
        <Grid item xs={6} md={3}>
          <MetricCard
            label="Battery Reserve"
            value={summary.avgBattery}
            unit="%"
            status={getBatteryStatus(summary.avgBattery)}
            icon={BatteryIcon}
            progress={{ value: summary.avgBattery, max: 100 }}
          />
        </Grid>
        <Grid item xs={6} md={3}>
          <MetricCard
            label="Sites Critical"
            value={summary.criticalCount}
            unit={`/ ${summary.activeCount}`}
            status={summary.criticalCount > 0 ? 'critical' : 'healthy'}
            icon={WarningIcon}
            subtitle="Requiring attention"
          />
        </Grid>
      </Grid>

      {/* Power Trends Chart */}
      <Paper sx={{ p: 3, borderRadius: 3, mb: 4, background: 'var(--surface-base)', border: '1px solid var(--surface-border)' }}>
        <Typography sx={{ fontWeight: 700, mb: 2, color: 'var(--mono-950)' }}>Power Consumption Trends</Typography>
        <MetricsChart />
      </Paper>

      {/* Station Details */}
      <Paper sx={{ p: 3, borderRadius: 3, background: 'var(--surface-base)', border: '1px solid var(--surface-border)' }}>
        <Typography sx={{ fontWeight: 700, mb: 2, color: 'var(--mono-950)' }}>Station Power Details</Typography>
        {stationMetrics
          .filter(s => s.status === StationStatus.ACTIVE || s.status === StationStatus.MAINTENANCE)
          .map(station => (
            <StationPowerRow key={station.stationId} station={station} />
          ))}
      </Paper>
    </Box>
  )
}
