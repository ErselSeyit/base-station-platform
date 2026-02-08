import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

/**
 * Route prefetch configuration.
 * Maps current routes to likely next routes for prefetching.
 */
const PREFETCH_MAP: Record<string, string[]> = {
  '/': ['/stations', '/alerts', '/5g', '/power'],      // Dashboard -> common next pages
  '/stations': ['/map', '/alerts'],                     // Stations -> related views
  '/5g': ['/power', '/metrics'],                        // 5G -> related network pages
  '/power': ['/5g', '/metrics'],                        // Power -> related pages
  '/alerts': ['/stations', '/ai-diagnostics'],          // Alerts -> investigation paths
  '/ai-diagnostics': ['/stations', '/son'],             // AI -> action pages
  '/map': ['/stations'],                                // Map -> station details
  '/metrics': ['/stations', '/power'],                  // Metrics -> data sources
  '/son': ['/stations', '/ai-diagnostics'],             // SON -> related pages
  '/reports': ['/metrics'],                             // Reports -> data pages
}

/**
 * Route to chunk name mapping for dynamic imports.
 * These match the lazy() imports in App.tsx.
 */
const ROUTE_CHUNKS: Record<string, () => Promise<unknown>> = {
  '/': () => import('../pages/Dashboard'),
  '/stations': () => import('../pages/Stations'),
  '/map': () => import('../pages/MapView'),
  '/alerts': () => import('../pages/Alerts'),
  '/metrics': () => import('../pages/Metrics'),
  '/ai-diagnostics': () => import('../pages/AIDiagnostics'),
  '/reports': () => import('../pages/Reports'),
  '/5g': () => import('../pages/FiveGDashboard'),
  '/power': () => import('../pages/PowerDashboard'),
  '/son': () => import('../pages/SONRecommendations'),
}

/**
 * Prefetches route chunks that are likely to be navigated to next.
 * Uses requestIdleCallback for non-blocking prefetch.
 */
export function useRoutePrefetch() {
  const location = useLocation()

  useEffect(() => {
    const currentPath = location.pathname

    // Get routes to prefetch based on current location
    const routesToPrefetch = PREFETCH_MAP[currentPath] ?? []

    if (routesToPrefetch.length === 0) return

    // Use requestIdleCallback for non-blocking prefetch
    const prefetch = () => {
      routesToPrefetch.forEach((route) => {
        const chunkLoader = ROUTE_CHUNKS[route]
        if (chunkLoader) {
          // Trigger the dynamic import to cache the chunk
          chunkLoader().catch(() => {
            // Silently fail - prefetch is best-effort
          })
        }
      })
    }

    // Prefetch when browser is idle
    if ('requestIdleCallback' in window) {
      const idleId = requestIdleCallback(prefetch, { timeout: 2000 })
      return () => cancelIdleCallback(idleId)
    } else {
      // Fallback for Safari
      const timeoutId = setTimeout(prefetch, 200)
      return () => clearTimeout(timeoutId)
    }
  }, [location.pathname])
}
