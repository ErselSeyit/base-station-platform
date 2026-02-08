/**
 * API Module - Re-exports all API modules for backward compatibility.
 *
 * Usage:
 *   import { stationApi, metricsApi } from '../services/api'
 *   // or
 *   import api from '../services/api'
 */

// Re-export the base client
export { api } from './client'
export type { ApiError } from './client'
export { default } from './client'

// Re-export domain APIs
export { stationApi } from './stations'
export { metricsApi } from './metrics'
export type { DailyMetricAggregate } from './metrics'
export { notificationsApi } from './notifications'
export { diagnosticsApi } from './diagnostics'
export { sonApi } from './son'
export { edgeBridgeApi } from './edgeBridge'

// Re-export types
export type {
  DiagnosticSession,
  DiagnosticStatusFilter,
  LearnedPattern,
  LearningStats,
  FeedbackRequest,
} from './diagnostics'

export type {
  SONRecommendation,
  SONFunction,
  SONStatus,
  SONStats,
  CreateRecommendationRequest,
} from './son'

export { SON_FUNCTION_NAMES } from './son'

export type {
  EdgeBridgeInstance,
  BridgeStats,
} from './edgeBridge'
