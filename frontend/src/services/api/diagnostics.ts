/**
 * Diagnostics API - AI Learning System operations.
 */
import { api } from './client'

// Types
export interface DiagnosticSession {
  id: string
  problemId: string
  stationId: number
  stationName: string
  createdAt: string
  resolvedAt?: string
  category: string
  severity: string
  problemCode: string
  message: string
  status: 'DETECTED' | 'DIAGNOSED' | 'APPLIED' | 'PENDING_CONFIRMATION' | 'RESOLVED' | 'FAILED'
  autoApplied?: boolean
  metricsSnapshot?: Record<string, number>  // Contains metric values like {CPU_USAGE: 95, threshold: 75}
  aiSolution?: {
    action: string
    commands: string[]
    expectedOutcome: string
    riskLevel: string
    confidence: number
    reasoning: string
  }
  feedback?: {
    wasEffective: boolean
    rating: number
    operatorNotes: string
    actualOutcome: string
    confirmedAt: string
    confirmedBy: string
  }
}

export interface LearnedPattern {
  problemCode: string
  category: string
  resolvedCount: number
  failedCount: number
  adjustedConfidence: number
  successfulSolutions: { action: string; count: number; avgRating: number }[]
  failedSolutions: { action: string; count: number }[]
}

export interface LearningStats {
  totalFeedback: number
  resolved: number
  failed: number
  pendingConfirmation: number
  autoApplied: number
  successRate: number
  learnedPatterns: number
  topPatterns: {
    problemCode: string
    successRate: number
    totalCases: number
    adjustedConfidence: number
  }[]
}

export interface FeedbackRequest {
  wasEffective: boolean
  rating?: number
  operatorNotes?: string
  actualOutcome?: string
}

export interface DiagnosticSessionPage {
  content: DiagnosticSession[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  last: boolean
}

/** Status values for filtering */
export type DiagnosticStatusFilter = 'DETECTED' | 'DIAGNOSED' | 'APPLIED' | 'PENDING_CONFIRMATION' | 'RESOLVED' | 'FAILED'

// API methods
export const diagnosticsApi = {
  getAll: () => api.get<DiagnosticSession[]>('/diagnostics'),

  /** Get recent sessions with limit for performance (default 50, max 200) */
  getRecent: (limit = 50) => api.get<DiagnosticSession[]>(`/diagnostics/recent?limit=${limit}`),

  /** Get sessions with pagination for infinite scroll, optionally filtered by status */
  getPaged: (page = 0, size = 20, status?: DiagnosticStatusFilter) => {
    const statusParam = status ? `&status=${status}` : ''
    return api.get<DiagnosticSessionPage>(`/diagnostics/page?page=${page}&size=${size}&sort=createdAt,desc${statusParam}`)
  },

  getPending: () => api.get<DiagnosticSession[]>('/diagnostics/pending'),

  getPendingForStation: (stationId: number) =>
    api.get<DiagnosticSession[]>(`/diagnostics/pending/station/${stationId}`),

  getSession: (sessionId: string) => api.get<DiagnosticSession>(`/diagnostics/${sessionId}`),

  getSessionsForStation: (stationId: number) =>
    api.get<DiagnosticSession[]>(`/diagnostics/station/${stationId}`),

  getSessionsByStatus: (status: string) =>
    api.get<DiagnosticSession[]>(`/diagnostics/status/${status}`),

  markApplied: (sessionId: string) =>
    api.post<DiagnosticSession>(`/diagnostics/${sessionId}/apply`),

  submitFeedback: (sessionId: string, feedback: FeedbackRequest) =>
    api.post<DiagnosticSession>(`/diagnostics/${sessionId}/feedback`, feedback),

  getLearningStats: () => api.get<LearningStats>('/diagnostics/learning/stats'),

  getLearnedPatterns: () => api.get<{ total: number; patterns: LearnedPattern[] }>('/diagnostics/learning/patterns'),

  getLearnedPattern: (problemCode: string) =>
    api.get<LearnedPattern>(`/diagnostics/learning/patterns/${problemCode}`),
}
