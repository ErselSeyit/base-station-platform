/**
 * Structured logging service for the frontend application.
 * 
 * Features:
 * - Environment-aware log levels (verbose in dev, minimal in prod)
 * - Structured log format with timestamps and context
 * - Easy to extend for remote logging (e.g., Sentry, LogRocket)
 * 
 * Usage:
 *   import { logger } from './logger'
 *   logger.info('User logged in', { userId: '123' })
 *   logger.error('API request failed', { url: '/api/users', status: 500 })
 */

type LogLevel = 'debug' | 'info' | 'warn' | 'error'

interface LogContext {
  [key: string]: unknown
}

interface LogEntry {
  level: LogLevel
  message: string
  timestamp: string
  context?: LogContext
}

// Check if we're in development mode
const isDevelopment = import.meta.env.DEV || import.meta.env.MODE === 'development'

// Minimum log level based on environment
const minLogLevel: LogLevel = isDevelopment ? 'debug' : 'warn'

const LOG_LEVELS: Record<LogLevel, number> = {
  debug: 0,
  info: 1,
  warn: 2,
  error: 3,
}

function shouldLog(level: LogLevel): boolean {
  return LOG_LEVELS[level] >= LOG_LEVELS[minLogLevel]
}

function formatLogEntry(entry: LogEntry): string {
  const contextStr = entry.context ? ` ${JSON.stringify(entry.context)}` : ''
  return `[${entry.timestamp}] [${entry.level.toUpperCase()}] ${entry.message}${contextStr}`
}

function createLogEntry(level: LogLevel, message: string, context?: LogContext): LogEntry {
  return {
    level,
    message,
    timestamp: new Date().toISOString(),
    context,
  }
}

/**
 * Logs a message at the specified level.
 * In production, only warn and error levels are logged.
 * In development, all levels are logged.
 */
function log(level: LogLevel, message: string, context?: LogContext): void {
  if (!shouldLog(level)) {
    return
  }

  const entry = createLogEntry(level, message, context)
  const formatted = formatLogEntry(entry)

  switch (level) {
    case 'debug':
      console.debug(formatted)
      break
    case 'info':
      console.info(formatted)
      break
    case 'warn':
      console.warn(formatted)
      break
    case 'error':
      console.error(formatted)
      break
  }

  // Extension point: Send to remote logging service in production
  // if (!isDevelopment && (level === 'warn' || level === 'error')) {
  //   sendToRemoteLogging(entry)
  // }
}

export const logger = {
  /**
   * Log debug information (development only).
   */
  debug: (message: string, context?: LogContext) => log('debug', message, context),

  /**
   * Log informational messages (development only).
   */
  info: (message: string, context?: LogContext) => log('info', message, context),

  /**
   * Log warnings (always logged).
   */
  warn: (message: string, context?: LogContext) => log('warn', message, context),

  /**
   * Log errors (always logged).
   */
  error: (message: string, context?: LogContext) => log('error', message, context),

  /**
   * Log API-related messages with consistent prefix.
   */
  api: {
    debug: (message: string, context?: LogContext) => log('debug', `[API] ${message}`, context),
    info: (message: string, context?: LogContext) => log('info', `[API] ${message}`, context),
    warn: (message: string, context?: LogContext) => log('warn', `[API] ${message}`, context),
    error: (message: string, context?: LogContext) => log('error', `[API] ${message}`, context),
  },
}

export default logger
