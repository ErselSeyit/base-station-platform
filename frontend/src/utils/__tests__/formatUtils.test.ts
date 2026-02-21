import { describe, it, expect } from 'vitest'
import {
  formatPercent,
  formatRatioAsPercent,
  formatMetricValue,
  formatKilowatts,
  formatBytes,
  formatSnakeCase,
} from '../formatUtils'

describe('formatPercent', () => {
  it('formats 0 as "0.0%"', () => {
    expect(formatPercent(0)).toBe('0.0%')
  })

  it('formats 50 as "50.0%"', () => {
    expect(formatPercent(50)).toBe('50.0%')
  })

  it('formats 100 as "100.0%"', () => {
    expect(formatPercent(100)).toBe('100.0%')
  })

  it('formats decimal values with default precision', () => {
    expect(formatPercent(75.56)).toBe('75.6%')
  })

  it('formats with custom decimal places', () => {
    expect(formatPercent(33.333, 2)).toBe('33.33%')
  })

  it('formats negative values', () => {
    expect(formatPercent(-5)).toBe('-5.0%')
  })

  it('formats with zero decimal places', () => {
    expect(formatPercent(42.7, 0)).toBe('43%')
  })
})

describe('formatRatioAsPercent', () => {
  it('formats 0 ratio as "0.0%"', () => {
    expect(formatRatioAsPercent(0)).toBe('0.0%')
  })

  it('formats 0.5 ratio as "50.0%"', () => {
    expect(formatRatioAsPercent(0.5)).toBe('50.0%')
  })

  it('formats 1.0 ratio as "100.0%"', () => {
    expect(formatRatioAsPercent(1.0)).toBe('100.0%')
  })

  it('formats with custom decimal places', () => {
    expect(formatRatioAsPercent(0.3333, 2)).toBe('33.33%')
  })

  it('formats ratio greater than 1', () => {
    expect(formatRatioAsPercent(1.5)).toBe('150.0%')
  })
})

describe('formatMetricValue', () => {
  it('formats normal numbers', () => {
    expect(formatMetricValue(42.567)).toBe('42.6')
  })

  it('formats zero', () => {
    expect(formatMetricValue(0)).toBe('0.0')
  })

  it('returns "0" for NaN', () => {
    expect(formatMetricValue(NaN)).toBe('0')
  })

  it('returns "0" for Infinity', () => {
    expect(formatMetricValue(Infinity)).toBe('0')
  })

  it('returns "0" for negative Infinity', () => {
    expect(formatMetricValue(-Infinity)).toBe('0')
  })

  it('formats with custom decimal places', () => {
    expect(formatMetricValue(3.14159, 3)).toBe('3.142')
  })

  it('formats negative numbers', () => {
    expect(formatMetricValue(-120.5)).toBe('-120.5')
  })
})

describe('formatKilowatts', () => {
  it('formats 0 watts as "0.00"', () => {
    expect(formatKilowatts(0)).toBe('0.00')
  })

  it('formats 1000 watts as "1.00"', () => {
    expect(formatKilowatts(1000)).toBe('1.00')
  })

  it('formats 1500 watts with default precision', () => {
    expect(formatKilowatts(1500)).toBe('1.50')
  })

  it('formats with custom decimal places', () => {
    expect(formatKilowatts(1234, 1)).toBe('1.2')
  })

  it('formats small watt values', () => {
    expect(formatKilowatts(500)).toBe('0.50')
  })
})

describe('formatBytes', () => {
  it('formats 0 bytes as "0 B"', () => {
    expect(formatBytes(0)).toBe('0 B')
  })

  it('formats bytes under 1 KB', () => {
    expect(formatBytes(512)).toBe('512.0 B')
  })

  it('formats 1023 bytes as bytes', () => {
    expect(formatBytes(1023)).toBe('1023.0 B')
  })

  it('formats 1024 bytes as "1.0 KB"', () => {
    expect(formatBytes(1024)).toBe('1.0 KB')
  })

  it('formats megabytes', () => {
    expect(formatBytes(1048576)).toBe('1.0 MB')
  })

  it('formats gigabytes', () => {
    expect(formatBytes(1073741824)).toBe('1.0 GB')
  })

  it('formats terabytes', () => {
    expect(formatBytes(1099511627776)).toBe('1.0 TB')
  })

  it('formats with custom decimal places', () => {
    expect(formatBytes(1536, 2)).toBe('1.50 KB')
  })
})

describe('formatSnakeCase', () => {
  it('converts snake_case to space-separated', () => {
    expect(formatSnakeCase('hello_world')).toBe('hello world')
  })

  it('handles multiple underscores', () => {
    expect(formatSnakeCase('this_is_a_test')).toBe('this is a test')
  })

  it('returns the same string when no underscores', () => {
    expect(formatSnakeCase('hello')).toBe('hello')
  })

  it('handles empty string', () => {
    expect(formatSnakeCase('')).toBe('')
  })

  it('handles string with only underscores', () => {
    expect(formatSnakeCase('___')).toBe('   ')
  })
})
