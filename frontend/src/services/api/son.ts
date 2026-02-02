/**
 * SON (Self-Organizing Network) API - Recommendation management operations.
 */
import { api } from './client'

// Types
export interface SONRecommendation {
  id: string
  stationId: number
  functionType: SONFunction
  actionType: string
  actionValue?: string
  description?: string
  expectedImprovement?: number
  confidence?: number
  status: SONStatus
  autoExecutable: boolean
  approvalRequired: boolean
  rollbackAction?: string
  approvedBy?: string
  approvedAt?: string
  rejectedBy?: string
  rejectedAt?: string
  rejectionReason?: string
  executedAt?: string
  executionResult?: string
  executionSuccess?: boolean
  rolledBackAt?: string
  rolledBackBy?: string
  rollbackReason?: string
  createdAt: string
  updatedAt: string
  expiresAt?: string
}

export type SONFunction = 'MLB' | 'MRO' | 'CCO' | 'ES' | 'ANR' | 'RAO' | 'ICIC'

export type SONStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'EXECUTING'
  | 'EXECUTED'
  | 'FAILED'
  | 'ROLLED_BACK'
  | 'EXPIRED'

export interface SONStats {
  pending: number
  approved: number
  executed: number
  failed: number
  rejected: number
  rolledBack: number
  total: number
  successRate: string
}

export interface CreateRecommendationRequest {
  stationId: number
  functionType: SONFunction
  actionType: string
  actionValue?: string
  description?: string
  expectedImprovement?: number
  confidence?: number
  autoExecutable?: boolean
  approvalRequired?: boolean
  rollbackAction?: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// SON Function display names
export const SON_FUNCTION_NAMES: Record<SONFunction, string> = {
  MLB: 'Mobility Load Balancing',
  MRO: 'Mobility Robustness Optimization',
  CCO: 'Coverage & Capacity Optimization',
  ES: 'Energy Saving',
  ANR: 'Automatic Neighbor Relation',
  RAO: 'Random Access Optimization',
  ICIC: 'Inter-Cell Interference Coordination',
}

// API methods
export const sonApi = {
  // Query endpoints
  getAll: (page = 0, size = 20) =>
    api.get<Page<SONRecommendation>>(`/son?page=${page}&size=${size}`),

  getPending: () => api.get<SONRecommendation[]>('/son/pending'),

  getById: (id: string) => api.get<SONRecommendation>(`/son/${id}`),

  getByStatus: (status: SONStatus, page = 0, size = 20) =>
    api.get<Page<SONRecommendation>>(`/son/status/${status}?page=${page}&size=${size}`),

  getByStation: (stationId: number) =>
    api.get<SONRecommendation[]>(`/son/station/${stationId}`),

  getPendingForStation: (stationId: number) =>
    api.get<SONRecommendation[]>(`/son/station/${stationId}/pending`),

  getByFunctionType: (functionType: SONFunction) =>
    api.get<SONRecommendation[]>(`/son/function/${functionType}`),

  // Approval workflow
  approve: (id: string) => api.post<SONRecommendation>(`/son/${id}/approve`),

  reject: (id: string, reason: string) =>
    api.post<SONRecommendation>(`/son/${id}/reject`, { reason }),

  rollback: (id: string, reason: string) =>
    api.post<SONRecommendation>(`/son/${id}/rollback`, { reason }),

  // Execution callbacks
  startExecution: (id: string) =>
    api.post<SONRecommendation>(`/son/${id}/execute/start`),

  recordResult: (id: string, success: boolean, result?: string) =>
    api.post<SONRecommendation>(`/son/${id}/execute/result`, { success, result }),

  // Statistics
  getStats: () => api.get<SONStats>('/son/stats'),

  getStatsForStation: (stationId: number) =>
    api.get<Record<string, unknown>>(`/son/stats/station/${stationId}`),

  // Create (typically called by AI service)
  create: (request: CreateRecommendationRequest) =>
    api.post<SONRecommendation>('/son', request),
}
