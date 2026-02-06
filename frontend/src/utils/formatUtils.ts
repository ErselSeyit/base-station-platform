/**
 * Number Formatting Utilities
 *
 * Consistent formatting for metric values and percentages across the application.
 */

/**
 * Format a number as a percentage string.
 * @param value - The value to format (0-100 range)
 * @param decimals - Number of decimal places (default: 1)
 * @returns Formatted percentage string (e.g., "75.5%")
 */
export function formatPercent(value: number, decimals: number = 1): string {
  return `${value.toFixed(decimals)}%`
}

/**
 * Format a 0-1 ratio as a percentage string.
 * @param ratio - The ratio to format (0-1 range)
 * @param decimals - Number of decimal places (default: 1)
 * @returns Formatted percentage string (e.g., "75.5%")
 */
export function formatRatioAsPercent(ratio: number, decimals: number = 1): string {
  return `${(ratio * 100).toFixed(decimals)}%`
}

/**
 * Format a metric value with specified decimal places.
 * Handles edge cases like NaN and Infinity.
 * @param value - The value to format
 * @param decimals - Number of decimal places (default: 1)
 * @returns Formatted number string
 */
export function formatMetricValue(value: number, decimals: number = 1): string {
  if (!Number.isFinite(value)) return '0'
  return value.toFixed(decimals)
}

/**
 * Format a value in kilowatts from watts.
 * @param watts - Value in watts
 * @param decimals - Number of decimal places (default: 2)
 * @returns Formatted kW string
 */
export function formatKilowatts(watts: number, decimals: number = 2): string {
  return (watts / 1000).toFixed(decimals)
}

/**
 * Format bytes to human-readable string.
 * @param bytes - Value in bytes
 * @param decimals - Number of decimal places (default: 1)
 * @returns Formatted string with unit (e.g., "1.5 GB")
 */
export function formatBytes(bytes: number, decimals: number = 1): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${(bytes / Math.pow(k, i)).toFixed(decimals)} ${sizes[i]}`
}
