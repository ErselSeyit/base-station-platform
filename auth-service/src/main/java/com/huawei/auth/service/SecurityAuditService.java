package com.huawei.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.auth.model.AuditLog;
import com.huawei.auth.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Security Audit Logging Service.
 *
 * Provides structured logging for security-relevant events:
 * - Authentication attempts (success/failure)
 * - Authorization decisions
 * - Account lockouts
 * - Password changes
 * - Token operations
 *
 * Persists audit events to database for compliance and supports
 * structured JSON logging for SIEM integration.
 */
@Service
@SuppressWarnings("null") // Spring Data repository operations guarantee non-null returns
public class SecurityAuditService {

    private static final Logger auditLog = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);
    private static final String REASON_FIELD = "reason";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${audit.persistence.enabled:true}")
    private boolean persistenceEnabled;

    @Value("${audit.retention.days:90}")
    private int retentionDays;

    public SecurityAuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public enum EventType {
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        LOGOUT,
        ACCOUNT_LOCKED,
        ACCOUNT_UNLOCKED,
        PASSWORD_CHANGED,
        TOKEN_ISSUED,
        TOKEN_REVOKED,
        REFRESH_TOKEN_CREATED,
        REFRESH_TOKEN_USED,
        REFRESH_TOKEN_REVOKED,
        REFRESH_TOKEN_ROTATED,
        ACCESS_DENIED,
        PRIVILEGE_ESCALATION_ATTEMPT,
        SUSPICIOUS_ACTIVITY
    }

    public enum Severity {
        INFO,
        WARNING,
        CRITICAL
    }

    /**
     * Logs a security audit event to both file and database.
     */
    public void logEvent(EventType eventType, String username, String clientIp,
                         Severity severity, String message, Map<String, Object> details) {
        // Log to file (synchronous for immediate visibility)
        logToFile(eventType, username, clientIp, severity, message, details);

        // Persist to database
        if (persistenceEnabled) {
            persistEvent(eventType, username, clientIp, severity, message, details);
        }
    }

    private void logToFile(EventType eventType, String username, String clientIp,
                           Severity severity, String message, Map<String, Object> details) {
        try {
            // Set MDC context for structured logging
            MDC.put("event_type", eventType.name());
            MDC.put("username", username != null ? username : "anonymous");
            MDC.put("client_ip", clientIp != null ? clientIp : "unknown");
            MDC.put("severity", severity.name());
            MDC.put("timestamp", Instant.now().toString());

            if (details != null) {
                details.forEach((k, v) -> MDC.put("detail_" + k, String.valueOf(v)));
            }

            String logMessage = String.format("[%s] %s - User: %s, IP: %s - %s",
                    severity, eventType, username, clientIp, message);

            switch (severity) {
                case CRITICAL -> auditLog.error(logMessage);
                case WARNING -> auditLog.warn(logMessage);
                default -> auditLog.info(logMessage);
            }
        } finally {
            MDC.clear();
        }
    }

    /**
     * Persists audit event to database.
     */
    private void persistEvent(EventType eventType, String username, String clientIp,
                              Severity severity, String message, Map<String, Object> details) {
        try {
            AuditLog auditLogEntry = AuditLog.builder()
                    .eventType(mapEventType(eventType))
                    .username(username != null ? username : "anonymous")
                    .clientIp(clientIp)
                    .severity(mapSeverity(severity))
                    .message(truncate(message, 500))
                    .details(serializeDetails(details))
                    .build();

            auditLogRepository.save(auditLogEntry);
        } catch (Exception e) {
            // Don't let database failures break the application flow
            log.error("Failed to persist audit event to database: {}", e.getMessage(), e);
        }
    }

    private AuditLog.EventType mapEventType(EventType eventType) {
        return AuditLog.EventType.valueOf(eventType.name());
    }

    private AuditLog.Severity mapSeverity(Severity severity) {
        return AuditLog.Severity.valueOf(severity.name());
    }

    private String serializeDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit details: {}", e.getMessage());
            return details.toString();
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }

    /**
     * Cleanup old audit logs based on retention policy.
     * Runs daily at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldAuditLogs() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = auditLogRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} audit log entries older than {} days", deleted, retentionDays);
        }
    }

    /**
     * Logs a successful login.
     */
    public void logLoginSuccess(String username, String clientIp) {
        logEvent(EventType.LOGIN_SUCCESS, username, clientIp, Severity.INFO,
                "User authenticated successfully", null);
    }

    /**
     * Logs a failed login attempt.
     */
    public void logLoginFailure(String username, String clientIp, String reason, int remainingAttempts) {
        logEvent(EventType.LOGIN_FAILURE, username, clientIp, Severity.WARNING,
                "Authentication failed: " + reason,
                Map.of("remaining_attempts", remainingAttempts, REASON_FIELD, reason));
    }

    /**
     * Logs an account lockout.
     */
    public void logAccountLocked(String username, String clientIp, long lockDurationSeconds) {
        logEvent(EventType.ACCOUNT_LOCKED, username, clientIp, Severity.CRITICAL,
                "Account locked due to excessive failed attempts",
                Map.of("lock_duration_seconds", lockDurationSeconds));
    }

    /**
     * Logs a logout event.
     */
    public void logLogout(String username, String clientIp) {
        logEvent(EventType.LOGOUT, username, clientIp, Severity.INFO,
                "User logged out", null);
    }

    /**
     * Logs an access denied event.
     */
    public void logAccessDenied(String username, String clientIp, String resource, String requiredRole) {
        logEvent(EventType.ACCESS_DENIED, username, clientIp, Severity.WARNING,
                "Access denied to resource: " + resource,
                Map.of("resource", resource, "required_role", requiredRole));
    }

    /**
     * Logs suspicious activity.
     */
    public void logSuspiciousActivity(String username, String clientIp, String activity) {
        logEvent(EventType.SUSPICIOUS_ACTIVITY, username, clientIp, Severity.CRITICAL,
                "Suspicious activity detected: " + activity, null);
    }

    /**
     * Logs a password change.
     */
    public void logPasswordChanged(String username, String clientIp) {
        logEvent(EventType.PASSWORD_CHANGED, username, clientIp, Severity.INFO,
                "Password changed successfully", null);
    }

    /**
     * Logs refresh token creation.
     */
    public void logRefreshTokenCreated(String username, String clientIp) {
        logEvent(EventType.REFRESH_TOKEN_CREATED, username, clientIp, Severity.INFO,
                "Refresh token created", null);
    }

    /**
     * Logs refresh token usage.
     */
    public void logRefreshTokenUsed(String username, String clientIp) {
        logEvent(EventType.REFRESH_TOKEN_USED, username, clientIp, Severity.INFO,
                "Refresh token used to obtain new access token", null);
    }

    /**
     * Logs refresh token revocation.
     */
    public void logRefreshTokenRevoked(String username, String clientIp, String reason) {
        logEvent(EventType.REFRESH_TOKEN_REVOKED, username, clientIp, Severity.WARNING,
                "Refresh token revoked: " + reason,
                Map.of(REASON_FIELD, reason));
    }

    /**
     * Logs all refresh tokens revoked for a user.
     */
    public void logAllRefreshTokensRevoked(String username, String reason, int count) {
        logEvent(EventType.REFRESH_TOKEN_REVOKED, username, "system", Severity.WARNING,
                "All refresh tokens revoked: " + reason,
                Map.of(REASON_FIELD, reason, "tokens_revoked", count));
    }

    /**
     * Logs refresh token rotation.
     */
    public void logRefreshTokenRotated(String username, String clientIp) {
        logEvent(EventType.REFRESH_TOKEN_ROTATED, username, clientIp, Severity.INFO,
                "Refresh token rotated", null);
    }
}
