/**
 * Threshold-aware Metric Evaluators
 *
 * Provides evaluation functions that use dynamic thresholds from the API.
 * Falls back to default thresholds if API values are unavailable.
 *
 * Usage:
 *   const { evalTemperature, evalCpu, evalBattery } = useThresholdEvaluators()
 *   const status = evalTemperature(75) // 'warning' if temp > threshold
 */
import { useMemo } from 'react'
import { useThresholds } from '../contexts/ThresholdContext'
import { HealthStatus, evalLower, evalHigher } from '../utils/metricEvaluators'

// Default thresholds matching metricsConfig.ts (used as fallback)
const DEFAULTS = {
  temperature: { warning: 65, critical: 80 },
  cpu: { warning: 70, critical: 85 },
  memory: { warning: 75, critical: 90 },
  battery: { warning: 50, critical: 20 },
  rsrp: { warning: -85, critical: -100 },
  sinr: { warning: 10, critical: 5 },
  latency: { warning: 15, critical: 30 },
  power: { warning: 5, critical: 8 }, // kW
} as const

interface ThresholdEvaluators {
  // Equipment evaluators (lower is better)
  evalTemperature: (value: number) => HealthStatus
  evalCpu: (value: number) => HealthStatus
  evalMemory: (value: number) => HealthStatus
  evalLatency: (value: number) => HealthStatus
  evalPower: (value: number) => HealthStatus

  // Equipment evaluators (higher is better)
  evalBattery: (value: number) => HealthStatus
  evalRsrp: (value: number) => HealthStatus
  evalSinr: (value: number) => HealthStatus

  // Generic evaluators with custom thresholds
  evalLowerIsBetter: (value: number, warningThreshold: number, criticalThreshold: number) => HealthStatus
  evalHigherIsBetter: (value: number, warningThreshold: number, criticalThreshold: number) => HealthStatus

  // Health score evaluator (0-1 range)
  evalHealthScore: (score: number) => HealthStatus

  // Raw thresholds for display
  thresholds: {
    temperature: { warning: number; critical: number }
    cpu: { warning: number; critical: number }
    memory: { warning: number; critical: number }
    battery: { warning: number; critical: number }
    rsrp: { warning: number; critical: number }
    sinr: { warning: number; critical: number }
    latency: { warning: number; critical: number }
    power: { warning: number; critical: number }
    health: { warning: number; critical: number; degraded: number }
  }
}

export function useThresholdEvaluators(): ThresholdEvaluators {
  const { thresholds: apiThresholds } = useThresholds()

  return useMemo(() => {
    // Extract thresholds with fallbacks
    const temp = apiThresholds.equipment.temperature
    const cpu = apiThresholds.equipment.cpu
    const battery = apiThresholds.equipment.battery_soc
    const rsrp = apiThresholds.equipment.signal_rsrp
    const health = apiThresholds.health

    const thresholds = {
      temperature: {
        warning: temp?.warning ?? DEFAULTS.temperature.warning,
        critical: temp?.critical ?? DEFAULTS.temperature.critical,
      },
      cpu: {
        warning: cpu?.warning ?? DEFAULTS.cpu.warning,
        critical: cpu?.critical ?? DEFAULTS.cpu.critical,
      },
      memory: DEFAULTS.memory, // No API config for memory yet
      battery: {
        warning: battery?.warning ?? DEFAULTS.battery.warning,
        critical: battery?.critical ?? DEFAULTS.battery.critical,
      },
      rsrp: {
        warning: rsrp?.warning ?? DEFAULTS.rsrp.warning,
        critical: rsrp?.critical ?? DEFAULTS.rsrp.critical,
      },
      sinr: DEFAULTS.sinr, // No API config for SINR yet
      latency: DEFAULTS.latency, // No API config for latency yet
      power: DEFAULTS.power, // No API config for power yet
      health: {
        warning: health.warning,
        critical: health.critical,
        degraded: health.degraded,
      },
    }

    return {
      // Lower is better (temperature, CPU, latency, power)
      evalTemperature: (value: number) =>
        evalLower(value, thresholds.temperature.warning, thresholds.temperature.critical),

      evalCpu: (value: number) =>
        evalLower(value, thresholds.cpu.warning, thresholds.cpu.critical),

      evalMemory: (value: number) =>
        evalLower(value, thresholds.memory.warning, thresholds.memory.critical),

      evalLatency: (value: number) =>
        evalLower(value, thresholds.latency.warning, thresholds.latency.critical),

      evalPower: (value: number) =>
        evalLower(value, thresholds.power.warning, thresholds.power.critical),

      // Higher is better (battery, RSRP, SINR)
      evalBattery: (value: number) =>
        evalHigher(value, thresholds.battery.warning, thresholds.battery.critical),

      evalRsrp: (value: number) =>
        evalHigher(value, thresholds.rsrp.warning, thresholds.rsrp.critical),

      evalSinr: (value: number) =>
        evalHigher(value, thresholds.sinr.warning, thresholds.sinr.critical),

      // Generic evaluators
      evalLowerIsBetter: evalLower,
      evalHigherIsBetter: evalHigher,

      // Health score (0-1 where higher is better)
      evalHealthScore: (score: number) =>
        evalHigher(score, thresholds.health.degraded, thresholds.health.warning),

      thresholds,
    }
  }, [apiThresholds])
}
