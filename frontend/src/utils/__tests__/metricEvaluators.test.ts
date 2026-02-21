import { describe, it, expect } from 'vitest'
import {
  evalLower,
  evalHigher,
  healthToMetricStatus,
  metricToHealthStatus,
  getWorstHealthStatus,
  getWorstMetricStatus,
  countHealthStatuses,
  countMetricStatuses,
  getPowerStatus,
  getBatteryStatus,
  getTempStatus,
  getFanStatus,
  getChargingStatusLabel,
  getCoolingStatusLabel,
  getSignalStatus,
  getSinrStatus,
  getLatencyStatus,
  getThroughputStatus,
  getHealthRatioStatus,
  POWER_THRESHOLDS,
  FIVEG_THRESHOLDS,
} from '../metricEvaluators'

// ============================================================================
// Threshold Evaluation Functions
// ============================================================================

describe('evalLower', () => {
  // evalLower: lower values are better (e.g., CPU, temperature, latency)
  const GOOD = 70
  const WARN = 85

  it('returns healthy when value is below good threshold', () => {
    expect(evalLower(50, GOOD, WARN)).toBe('healthy')
  })

  it('returns healthy when value equals good threshold', () => {
    expect(evalLower(70, GOOD, WARN)).toBe('healthy')
  })

  it('returns warning when value is between good and warn thresholds', () => {
    expect(evalLower(75, GOOD, WARN)).toBe('warning')
  })

  it('returns warning when value equals warn threshold', () => {
    expect(evalLower(85, GOOD, WARN)).toBe('warning')
  })

  it('returns critical when value exceeds warn threshold', () => {
    expect(evalLower(90, GOOD, WARN)).toBe('critical')
  })
})

describe('evalHigher', () => {
  // evalHigher: higher values are better (e.g., throughput, signal)
  const GOOD = 1000
  const WARN = 500

  it('returns healthy when value is above good threshold', () => {
    expect(evalHigher(1200, GOOD, WARN)).toBe('healthy')
  })

  it('returns healthy when value equals good threshold', () => {
    expect(evalHigher(1000, GOOD, WARN)).toBe('healthy')
  })

  it('returns warning when value is between warn and good thresholds', () => {
    expect(evalHigher(700, GOOD, WARN)).toBe('warning')
  })

  it('returns warning when value equals warn threshold', () => {
    expect(evalHigher(500, GOOD, WARN)).toBe('warning')
  })

  it('returns critical when value is below warn threshold', () => {
    expect(evalHigher(300, GOOD, WARN)).toBe('critical')
  })
})

// ============================================================================
// Status Conversion
// ============================================================================

describe('healthToMetricStatus', () => {
  it('converts healthy to pass', () => {
    expect(healthToMetricStatus('healthy')).toBe('pass')
  })

  it('converts warning to warning', () => {
    expect(healthToMetricStatus('warning')).toBe('warning')
  })

  it('converts critical to fail', () => {
    expect(healthToMetricStatus('critical')).toBe('fail')
  })
})

describe('metricToHealthStatus', () => {
  it('converts pass to healthy', () => {
    expect(metricToHealthStatus('pass')).toBe('healthy')
  })

  it('converts warning to warning', () => {
    expect(metricToHealthStatus('warning')).toBe('warning')
  })

  it('converts fail to critical', () => {
    expect(metricToHealthStatus('fail')).toBe('critical')
  })
})

describe('bidirectional conversion', () => {
  it('round-trips health -> metric -> health', () => {
    const statuses = ['healthy', 'warning', 'critical'] as const
    for (const status of statuses) {
      expect(metricToHealthStatus(healthToMetricStatus(status))).toBe(status)
    }
  })

  it('round-trips metric -> health -> metric', () => {
    const statuses = ['pass', 'warning', 'fail'] as const
    for (const status of statuses) {
      expect(healthToMetricStatus(metricToHealthStatus(status))).toBe(status)
    }
  })
})

// ============================================================================
// Aggregate Status Utilities
// ============================================================================

describe('getWorstHealthStatus', () => {
  it('returns healthy for empty array', () => {
    expect(getWorstHealthStatus([])).toBe('healthy')
  })

  it('returns healthy when all are healthy', () => {
    expect(getWorstHealthStatus(['healthy', 'healthy', 'healthy'])).toBe('healthy')
  })

  it('returns warning when worst is warning', () => {
    expect(getWorstHealthStatus(['healthy', 'warning', 'healthy'])).toBe('warning')
  })

  it('returns critical when any is critical', () => {
    expect(getWorstHealthStatus(['healthy', 'warning', 'critical'])).toBe('critical')
  })

  it('returns critical even if only one critical', () => {
    expect(getWorstHealthStatus(['healthy', 'healthy', 'critical'])).toBe('critical')
  })
})

describe('getWorstMetricStatus', () => {
  it('returns pass for empty array', () => {
    expect(getWorstMetricStatus([])).toBe('pass')
  })

  it('returns fail when any is fail', () => {
    expect(getWorstMetricStatus(['pass', 'warning', 'fail'])).toBe('fail')
  })

  it('returns warning when worst is warning', () => {
    expect(getWorstMetricStatus(['pass', 'warning'])).toBe('warning')
  })
})

describe('countHealthStatuses', () => {
  it('counts all zeros for empty array', () => {
    expect(countHealthStatuses([])).toEqual({ healthy: 0, warning: 0, critical: 0 })
  })

  it('counts each status correctly', () => {
    const statuses = ['healthy', 'healthy', 'warning', 'critical', 'critical', 'critical'] as const
    expect(countHealthStatuses([...statuses])).toEqual({
      healthy: 2,
      warning: 1,
      critical: 3,
    })
  })

  it('counts single status', () => {
    expect(countHealthStatuses(['warning'])).toEqual({
      healthy: 0,
      warning: 1,
      critical: 0,
    })
  })
})

describe('countMetricStatuses', () => {
  it('counts all zeros for empty array', () => {
    expect(countMetricStatuses([])).toEqual({ pass: 0, warning: 0, fail: 0 })
  })

  it('counts each status correctly', () => {
    const statuses = ['pass', 'pass', 'warning', 'fail'] as const
    expect(countMetricStatuses([...statuses])).toEqual({
      pass: 2,
      warning: 1,
      fail: 1,
    })
  })
})

// ============================================================================
// Power Dashboard Status Evaluation
// ============================================================================

describe('getPowerStatus', () => {
  it('returns healthy when consumption is well within limits', () => {
    // 0.7 * 5 = 3.5 kW healthy threshold (default max 5 kW)
    expect(getPowerStatus(2)).toBe('healthy')
  })

  it('returns healthy at exactly the healthy ratio boundary', () => {
    const boundary = POWER_THRESHOLDS.DEFAULT_MAX_POWER_KW * POWER_THRESHOLDS.POWER_HEALTHY_RATIO
    expect(getPowerStatus(boundary)).toBe('healthy')
  })

  it('returns warning when consumption exceeds healthy ratio', () => {
    // Between 3.5 and 4.5 kW (0.7-0.9 of 5 kW)
    expect(getPowerStatus(4)).toBe('warning')
  })

  it('returns warning at exactly the warning ratio boundary', () => {
    const boundary = POWER_THRESHOLDS.DEFAULT_MAX_POWER_KW * POWER_THRESHOLDS.POWER_WARNING_RATIO
    expect(getPowerStatus(boundary)).toBe('warning')
  })

  it('returns critical when consumption exceeds warning ratio', () => {
    expect(getPowerStatus(4.6)).toBe('critical')
  })

  it('uses custom max power when provided', () => {
    // 0.7 * 10 = 7 kW healthy threshold
    expect(getPowerStatus(5, 10)).toBe('healthy')
    expect(getPowerStatus(8, 10)).toBe('warning')
    expect(getPowerStatus(9.5, 10)).toBe('critical')
  })
})

describe('getBatteryStatus', () => {
  it('returns healthy for high SOC', () => {
    expect(getBatteryStatus(80)).toBe('healthy')
  })

  it('returns healthy at exactly the healthy threshold', () => {
    expect(getBatteryStatus(POWER_THRESHOLDS.BATTERY_HEALTHY_SOC)).toBe('healthy')
  })

  it('returns warning for mid-range SOC', () => {
    expect(getBatteryStatus(30)).toBe('warning')
  })

  it('returns warning at exactly the warning threshold', () => {
    expect(getBatteryStatus(POWER_THRESHOLDS.BATTERY_WARNING_SOC)).toBe('warning')
  })

  it('returns critical for low SOC', () => {
    expect(getBatteryStatus(10)).toBe('critical')
  })

  it('returns critical for zero SOC', () => {
    expect(getBatteryStatus(0)).toBe('critical')
  })
})

describe('getTempStatus', () => {
  it('returns healthy for normal temperature', () => {
    expect(getTempStatus(40)).toBe('healthy')
  })

  it('returns healthy at exactly the healthy threshold', () => {
    expect(getTempStatus(POWER_THRESHOLDS.TEMP_HEALTHY_MAX)).toBe('healthy')
  })

  it('returns warning for warm temperature', () => {
    expect(getTempStatus(70)).toBe('warning')
  })

  it('returns warning at exactly the warning threshold', () => {
    expect(getTempStatus(POWER_THRESHOLDS.TEMP_WARNING_MAX)).toBe('warning')
  })

  it('returns critical for hot temperature', () => {
    expect(getTempStatus(85)).toBe('critical')
  })
})

describe('getFanStatus', () => {
  it('returns healthy when speed is in healthy range', () => {
    expect(getFanStatus(3000)).toBe('healthy')
  })

  it('returns healthy at minimum healthy speed', () => {
    expect(getFanStatus(POWER_THRESHOLDS.FAN_HEALTHY_MIN)).toBe('healthy')
  })

  it('returns healthy at maximum healthy speed', () => {
    expect(getFanStatus(POWER_THRESHOLDS.FAN_HEALTHY_MAX)).toBe('healthy')
  })

  it('returns warning when speed is above healthy max but above warning min', () => {
    expect(getFanStatus(5500)).toBe('warning')
  })

  it('returns warning when speed is below healthy min but above warning min', () => {
    expect(getFanStatus(1500)).toBe('warning')
  })

  it('returns critical when speed is below warning min', () => {
    expect(getFanStatus(500)).toBe('critical')
  })
})

describe('getChargingStatusLabel', () => {
  it('returns "Full" for high battery', () => {
    expect(getChargingStatusLabel(98)).toBe('Full')
  })

  it('returns "Full" at exactly full threshold', () => {
    expect(getChargingStatusLabel(POWER_THRESHOLDS.BATTERY_FULL_SOC)).toBe('Full')
  })

  it('returns "Charging" for mid-range battery', () => {
    expect(getChargingStatusLabel(60)).toBe('Charging')
  })

  it('returns "Charging" at exactly charging threshold', () => {
    expect(getChargingStatusLabel(POWER_THRESHOLDS.BATTERY_CHARGING_SOC)).toBe('Charging')
  })

  it('returns "Low" for low battery', () => {
    expect(getChargingStatusLabel(30)).toBe('Low')
  })
})

describe('getCoolingStatusLabel', () => {
  it('returns "High" for high fan speed', () => {
    expect(getCoolingStatusLabel(4000)).toBe('High')
  })

  it('returns "High" at exactly high threshold', () => {
    expect(getCoolingStatusLabel(POWER_THRESHOLDS.FAN_HIGH_SPEED)).toBe('High')
  })

  it('returns "Active" for active fan speed', () => {
    expect(getCoolingStatusLabel(2000)).toBe('Active')
  })

  it('returns "Active" at exactly active threshold', () => {
    expect(getCoolingStatusLabel(POWER_THRESHOLDS.FAN_ACTIVE_SPEED)).toBe('Active')
  })

  it('returns "Idle" for low fan speed', () => {
    expect(getCoolingStatusLabel(1000)).toBe('Idle')
  })
})

// ============================================================================
// 5G Dashboard Status Evaluation
// ============================================================================

describe('getSignalStatus', () => {
  // RSRP values are negative; stronger signal = less negative
  it('returns healthy for strong signal', () => {
    expect(getSignalStatus(-70)).toBe('healthy')
  })

  it('returns healthy at exactly the healthy threshold', () => {
    expect(getSignalStatus(FIVEG_THRESHOLDS.RSRP_HEALTHY)).toBe('healthy')
  })

  it('returns warning for medium signal', () => {
    expect(getSignalStatus(-90)).toBe('warning')
  })

  it('returns warning at exactly the warning threshold', () => {
    expect(getSignalStatus(FIVEG_THRESHOLDS.RSRP_WARNING)).toBe('warning')
  })

  it('returns critical for weak signal', () => {
    expect(getSignalStatus(-110)).toBe('critical')
  })
})

describe('getSinrStatus', () => {
  it('returns healthy for good SINR', () => {
    expect(getSinrStatus(15)).toBe('healthy')
  })

  it('returns healthy at exactly the healthy threshold', () => {
    expect(getSinrStatus(FIVEG_THRESHOLDS.SINR_HEALTHY)).toBe('healthy')
  })

  it('returns warning for fair SINR', () => {
    expect(getSinrStatus(7)).toBe('warning')
  })

  it('returns warning at exactly the warning threshold', () => {
    expect(getSinrStatus(FIVEG_THRESHOLDS.SINR_WARNING)).toBe('warning')
  })

  it('returns critical for poor SINR', () => {
    expect(getSinrStatus(2)).toBe('critical')
  })
})

describe('getLatencyStatus', () => {
  it('returns healthy for low latency', () => {
    expect(getLatencyStatus(5)).toBe('healthy')
  })

  it('returns healthy at exactly the healthy threshold', () => {
    expect(getLatencyStatus(FIVEG_THRESHOLDS.LATENCY_HEALTHY)).toBe('healthy')
  })

  it('returns warning for medium latency', () => {
    expect(getLatencyStatus(20)).toBe('warning')
  })

  it('returns warning at exactly the warning threshold', () => {
    expect(getLatencyStatus(FIVEG_THRESHOLDS.LATENCY_WARNING)).toBe('warning')
  })

  it('returns critical for high latency', () => {
    expect(getLatencyStatus(50)).toBe('critical')
  })
})

describe('getThroughputStatus', () => {
  it('returns healthy for good throughput ratio', () => {
    // 600 / 1000 = 0.6, above 0.5 healthy threshold
    expect(getThroughputStatus(600, 1000)).toBe('healthy')
  })

  it('returns healthy at exactly the healthy ratio', () => {
    expect(getThroughputStatus(500, 1000)).toBe('healthy')
  })

  it('returns warning for medium throughput ratio', () => {
    // 300 / 1000 = 0.3, above 0.25 warning threshold
    expect(getThroughputStatus(300, 1000)).toBe('warning')
  })

  it('returns warning at exactly the warning ratio', () => {
    expect(getThroughputStatus(250, 1000)).toBe('warning')
  })

  it('returns critical for poor throughput ratio', () => {
    // 100 / 1000 = 0.1, below 0.25 warning threshold
    expect(getThroughputStatus(100, 1000)).toBe('critical')
  })
})

describe('getHealthRatioStatus', () => {
  it('returns healthy for high ratio', () => {
    expect(getHealthRatioStatus(0.95)).toBe('healthy')
  })

  it('returns healthy at exactly the healthy threshold', () => {
    expect(getHealthRatioStatus(FIVEG_THRESHOLDS.HEALTH_RATIO_HEALTHY)).toBe('healthy')
  })

  it('returns warning for medium ratio', () => {
    expect(getHealthRatioStatus(0.8)).toBe('warning')
  })

  it('returns warning at exactly the warning threshold', () => {
    expect(getHealthRatioStatus(FIVEG_THRESHOLDS.HEALTH_RATIO_WARNING)).toBe('warning')
  })

  it('returns critical for low ratio', () => {
    expect(getHealthRatioStatus(0.5)).toBe('critical')
  })
})
