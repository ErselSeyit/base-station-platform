/**
 * Threshold Configuration Context
 *
 * Provides centralized access to threshold configurations fetched from the backend.
 * Thresholds are loaded on app initialization and can be refreshed manually or via polling.
 *
 * Features:
 * - Fetches thresholds from /api/thresholds on mount
 * - Optional polling for auto-refresh (configurable interval)
 * - Falls back to defaults if API unavailable
 * - Provides loading/error states for UI feedback
 * - Exposes refresh function for manual updates
 * - Component-level re-render (only components using thresholds update)
 *
 * Usage:
 *   const { thresholds, isLoading, refresh } = useThresholds()
 *   const cpuWarning = thresholds.equipment.cpu?.warning ?? 85
 */
import { createContext, useContext, useState, useEffect, useCallback, useMemo, useRef, ReactNode } from 'react'
import { thresholdsApi, AllThresholds, DEFAULT_THRESHOLDS } from '../services/api/thresholds'
import { logger } from '../services/logger'

/** Default polling interval: 5 minutes */
const DEFAULT_POLL_INTERVAL_MS = 5 * 60 * 1000

interface ThresholdContextValue {
  /** All threshold configurations */
  thresholds: AllThresholds
  /** Whether thresholds are currently being loaded */
  isLoading: boolean
  /** Whether a background refresh is in progress (doesn't block UI) */
  isRefreshing: boolean
  /** Error message if loading failed */
  error: string | null
  /** Whether thresholds were loaded from API (vs defaults) */
  isFromApi: boolean
  /** Timestamp of last successful fetch */
  lastUpdated: Date | null
  /** Manually refresh thresholds from API */
  refresh: () => Promise<void>
}

const ThresholdContext = createContext<ThresholdContextValue | null>(null)

interface ThresholdProviderProps {
  readonly children: ReactNode
  /** Polling interval in milliseconds. Set to 0 to disable polling. Default: 5 minutes */
  readonly pollInterval?: number
  /** Whether to enable polling. Default: true */
  readonly enablePolling?: boolean
}

export function ThresholdProvider({
  children,
  pollInterval = DEFAULT_POLL_INTERVAL_MS,
  enablePolling = true,
}: ThresholdProviderProps) {
  const [thresholds, setThresholds] = useState<AllThresholds>(DEFAULT_THRESHOLDS)
  const [isLoading, setIsLoading] = useState(true)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isFromApi, setIsFromApi] = useState(false)
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
  const isMounted = useRef(true)

  const fetchThresholds = useCallback(async (isBackground = false) => {
    if (isBackground) {
      setIsRefreshing(true)
    } else {
      setIsLoading(true)
    }
    setError(null)

    try {
      const response = await thresholdsApi.getAll()
      const data = response.data

      // Only update if component is still mounted
      if (!isMounted.current) return

      // Merge with defaults to ensure all fields exist
      const merged: AllThresholds = {
        health: { ...DEFAULT_THRESHOLDS.health, ...data.health },
        confidence: { ...DEFAULT_THRESHOLDS.confidence, ...data.confidence },
        learning: { ...DEFAULT_THRESHOLDS.learning, ...data.learning },
        equipment: {
          ...DEFAULT_THRESHOLDS.equipment,
          ...data.equipment,
        },
      }

      setThresholds(merged)
      setIsFromApi(true)
      setLastUpdated(new Date())
      logger.info('Thresholds loaded from API')
    } catch (err) {
      if (!isMounted.current) return

      const message = err instanceof Error ? err.message : 'Failed to load thresholds'
      setError(message)
      // Don't change isFromApi on refresh failure - keep previous state
      if (!isBackground) {
        setIsFromApi(false)
      }
      logger.warn('Failed to load thresholds', { error: message, isBackground })
    } finally {
      if (isMounted.current) {
        setIsLoading(false)
        setIsRefreshing(false)
      }
    }
  }, [])

  // Load thresholds on mount
  useEffect(() => {
    isMounted.current = true
    fetchThresholds(false)

    return () => {
      isMounted.current = false
    }
  }, [fetchThresholds])

  // Setup polling for auto-refresh
  useEffect(() => {
    if (!enablePolling || pollInterval <= 0) return

    const intervalId = setInterval(() => {
      // Background refresh - doesn't show loading state
      fetchThresholds(true)
    }, pollInterval)

    return () => clearInterval(intervalId)
  }, [enablePolling, pollInterval, fetchThresholds])

  // Manual refresh function (shows refreshing state)
  const refresh = useCallback(async () => {
    await fetchThresholds(true)
  }, [fetchThresholds])

  const value = useMemo<ThresholdContextValue>(
    () => ({
      thresholds,
      isLoading,
      isRefreshing,
      error,
      isFromApi,
      lastUpdated,
      refresh,
    }),
    [thresholds, isLoading, isRefreshing, error, isFromApi, lastUpdated, refresh]
  )

  return <ThresholdContext.Provider value={value}>{children}</ThresholdContext.Provider>
}

/**
 * Hook to access threshold configurations.
 *
 * @throws Error if used outside ThresholdProvider
 *
 * @example
 * const { thresholds, refresh, isRefreshing } = useThresholds()
 * const isCritical = temperature > thresholds.equipment.temperature?.critical
 */
export function useThresholds(): ThresholdContextValue {
  const context = useContext(ThresholdContext)
  if (!context) {
    throw new Error('useThresholds must be used within a ThresholdProvider')
  }
  return context
}

/**
 * Hook to get a specific threshold value with fallback.
 *
 * @example
 * const cpuWarning = useThresholdValue('equipment', 'cpu', 'warning')
 */
export function useThresholdValue<T extends keyof AllThresholds>(
  category: T,
  ...path: string[]
): number | undefined {
  const { thresholds } = useThresholds()

  // Navigate the path to get the value
  let value: unknown = thresholds[category]
  for (const key of path) {
    if (value && typeof value === 'object' && key in value) {
      value = (value as Record<string, unknown>)[key]
    } else {
      return undefined
    }
  }

  return typeof value === 'number' ? value : undefined
}
