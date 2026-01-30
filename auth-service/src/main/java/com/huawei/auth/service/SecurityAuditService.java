package com.huawei.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
 * Uses structured JSON logging for SIEM integration.
 */
@Service
public class SecurityAuditService {

    private static final Logger auditLog = LoggerFactory.getLogger("SECURITY_AUDIT");

    public enum EventType {
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        LOGOUT,
        ACCOUNT_LOCKED,
        ACCOUNT_UNLOCKED,
        PASSWORD_CHANGED,
        TOKEN_ISSUED,
        TOKEN_REVOKED,
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
     * Logs a security audit event.
     */
    public void logEvent(EventType eventType, String username, String clientIp,
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
                Map.of("remaining_attempts", remainingAttempts, "reason", reason));
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
}
