/**
 * Array Utilities - Shared helper functions for array operations.
 *
 * These functions are used across multiple components to avoid duplication.
 * Following immutability principles - no mutation of input arrays.
 */

/**
 * Ensures the value is an array. Returns empty array if null/undefined or not an array.
 * Use this instead of repeated `Array.isArray(data) ? data : []` patterns.
 */
export function ensureArray<T>(value: T[] | null | undefined): T[] {
  return Array.isArray(value) ? value : []
}

/**
 * Calculates the sum of values extracted from array items.
 * Returns 0 for empty arrays.
 */
export function sum<T>(array: readonly T[], getter: (item: T) => number): number {
  return array.reduce((total, item) => total + getter(item), 0)
}

/**
 * Calculates the average of values extracted from array items.
 * Returns defaultValue for empty arrays (avoids division by zero).
 */
export function avg<T>(
  array: readonly T[],
  getter: (item: T) => number,
  defaultValue: number = 0
): number {
  if (array.length === 0) return defaultValue
  return sum(array, getter) / array.length
}

/**
 * Calculates average of a simple number array.
 * Returns defaultValue for empty or undefined arrays.
 */
export function avgNumbers(values: readonly number[] | undefined, defaultValue: number = 0): number {
  if (!values || values.length === 0) return defaultValue
  return values.reduce((total, v) => total + v, 0) / values.length
}

/**
 * Partitions an array into two groups based on a predicate.
 * Returns { pass, fail } - items that match and don't match the predicate.
 * Single pass through array (more efficient than two separate filters).
 */
export function partition<T>(
  array: readonly T[],
  predicate: (item: T) => boolean
): { pass: T[]; fail: T[] } {
  return array.reduce<{ pass: T[]; fail: T[] }>(
    (acc, item) => {
      if (predicate(item)) {
        acc.pass.push(item)
      } else {
        acc.fail.push(item)
      }
      return acc
    },
    { pass: [], fail: [] }
  )
}

/**
 * Groups array items by a key extracted from each item.
 * Returns a Map for O(1) lookup by key.
 */
export function groupBy<T, K>(
  array: readonly T[],
  keyFn: (item: T) => K
): Map<K, T[]> {
  return array.reduce((map, item) => {
    const key = keyFn(item)
    const group = map.get(key) || []
    group.push(item)
    map.set(key, group)
    return map
  }, new Map<K, T[]>())
}
