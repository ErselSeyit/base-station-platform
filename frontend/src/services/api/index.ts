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
export { notificationsApi } from './notifications'
export { diagnosticsApi } from './diagnostics'

// Re-export types
export type {
  DiagnosticSession,
  LearnedPattern,
  LearningStats,
  FeedbackRequest,
} from './diagnostics'
