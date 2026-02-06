/**
 * API Module - Backward compatibility re-export.
 *
 * This file maintains backward compatibility with existing imports.
 * New code should import from 'services/api' (the directory index).
 *
 * @deprecated Import from 'services/api' instead of 'services/api.ts'
 */

// Re-export everything from the modular API
export {
  api,
  stationApi,
  metricsApi,
  notificationsApi,
  diagnosticsApi,
} from './api/index'

// Re-export types separately
export type { ApiError } from './api/index'
export type {
  DiagnosticSession,
  LearnedPattern,
  LearningStats,
  FeedbackRequest,
} from './api/index'

export { default } from './api/index'
