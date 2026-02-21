/**
 * Thresholds API - Fetch threshold configurations from backend.
 *
 * Provides access to centralized threshold configs stored in MongoDB.
 * Thresholds are cached on the server (Redis) and updated via RabbitMQ.
 */
import { api } from './client'

// Types matching backend ThresholdConfig
export interface ThresholdConfig {
  id: string
  configType: string
  thresholds: Record<string, number>
  metadata?: Record<string, unknown>
  description?: string
  enabled: boolean
  version: number
  updatedBy?: string
  updatedAt?: string
}

/**
 * All thresholds as a nested map structure.
 * Matches the backend /api/thresholds endpoint response.
 */
export interface AllThresholds {
  health: HealthThresholds
  confidence: ConfidenceThresholds
  learning: LearningThresholds
  equipment: EquipmentThresholds
}

export interface HealthThresholds {
  critical: number
  warning: number
  degraded: number
}

export interface ConfidenceThresholds {
  auto_apply: number
  suggest_apply: number
  manual_review: number
  low: number
  auto_apply_low_risk?: number
  auto_apply_medium_risk?: number
}

export interface LearningThresholds {
  high_success_rate: number
  low_success_rate: number
  max_confidence_boost: number
  max_confidence_penalty: number
  min_feedback_for_adjustment: number
}

export interface EquipmentThresholds {
  temperature?: MetricThreshold
  cpu?: MetricThreshold
  battery_soc?: MetricThreshold
  signal_rsrp?: MetricThreshold
}

export interface MetricThreshold {
  healthy: number
  warning: number
  critical: number
  unit?: string
  higher_is_worse?: boolean
}

// Default values matching backend defaults (fallback if API unavailable)
export const DEFAULT_THRESHOLDS: AllThresholds = {
  health: {
    critical: 0.4,
    warning: 0.6,
    degraded: 0.8,
  },
  confidence: {
    auto_apply: 0.95,
    suggest_apply: 0.85,
    manual_review: 0.7,
    low: 0.7,
  },
  learning: {
    high_success_rate: 0.8,
    low_success_rate: 0.5,
    max_confidence_boost: 0.1,
    max_confidence_penalty: 0.2,
    min_feedback_for_adjustment: 5,
  },
  equipment: {
    temperature: { healthy: 65, warning: 80, critical: 95, unit: '°C', higher_is_worse: true },
    cpu: { healthy: 70, warning: 85, critical: 95, unit: '%', higher_is_worse: true },
    battery_soc: { healthy: 50, warning: 20, critical: 10, unit: '%', higher_is_worse: false },
    signal_rsrp: { healthy: -85, warning: -100, critical: -110, unit: 'dBm', higher_is_worse: false },
  },
}

// API methods
export const thresholdsApi = {
  /**
   * Get all thresholds as a nested map structure.
   * This is the primary endpoint for loading thresholds on app init.
   */
  getAll: () => api.get<AllThresholds>('/thresholds'),

  /**
   * Get all threshold configs as a list.
   */
  getConfigs: () => api.get<ThresholdConfig[]>('/thresholds/configs'),

  /**
   * Get a specific threshold config by type.
   */
  getConfig: (configType: string) => api.get<ThresholdConfig>(`/thresholds/${configType}`),

  /**
   * Get health status thresholds.
   */
  getHealth: () => api.get<Record<string, number>>('/thresholds/health'),

  /**
   * Get confidence automation thresholds.
   */
  getConfidence: () => api.get<Record<string, number>>('/thresholds/confidence'),

  /**
   * Get equipment thresholds by type.
   */
  getEquipment: (equipmentType: string) =>
    api.get<Record<string, number>>(`/thresholds/equipment/${equipmentType}`),
}
