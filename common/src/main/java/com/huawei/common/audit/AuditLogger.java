package com.huawei.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Audit logger for tracking security-relevant events.
 *
 * Logs are written to a dedicated audit log channel using structured JSON format.
 * In production, these should be forwarded to a SIEM or audit log system.
 */
@Component
public class AuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    public enum AuditAction {
        // Authentication events
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        LOGOUT,
        TOKEN_VALIDATED,
        TOKEN_EXPIRED,
        ACCOUNT_LOCKED,

        // User management
        USER_CREATED,
        USER_UPDATED,
        USER_DISABLED,
        PASSWORD_CHANGED,

        // Resource operations
        STATION_CREATED,
        STATION_UPDATED,
        STATION_DELETED,
        ALERT_RULE_CREATED,
        ALERT_RULE_UPDATED,
        ALERT_RULE_DELETED,

        // System events
        CONFIG_CHANGED,
        UNAUTHORIZED_ACCESS
    }

    /**
     * Log an audit event.
     *
     * @param action the audit action
     * @param actor the user/system performing the action
     * @param resource the resource being acted upon
     * @param details additional details
     */
    public void log(AuditAction action, String actor, String resource, String details) {
        try {
            MDC.put("audit.action", action.name());
            MDC.put("audit.actor", actor != null ? actor : "SYSTEM");
            MDC.put("audit.resource", resource != null ? resource : "N/A");
            MDC.put("audit.timestamp", Instant.now().toString());

            auditLog.info("AUDIT: action={}, actor={}, resource={}, details={}",
                    action, actor, resource, details);
        } finally {
            MDC.remove("audit.action");
            MDC.remove("audit.actor");
            MDC.remove("audit.resource");
            MDC.remove("audit.timestamp");
        }
    }

    /**
     * Log an audit event without details.
     */
    public void log(AuditAction action, String actor, String resource) {
        log(action, actor, resource, null);
    }

    /**
     * Log a security-relevant failure.
     */
    public void logSecurityEvent(AuditAction action, String actor, String resource, String reason) {
        try {
            MDC.put("audit.action", action.name());
            MDC.put("audit.actor", actor != null ? actor : "UNKNOWN");
            MDC.put("audit.resource", resource != null ? resource : "N/A");
            MDC.put("audit.timestamp", Instant.now().toString());
            MDC.put("audit.security", "true");

            auditLog.warn("SECURITY: action={}, actor={}, resource={}, reason={}",
                    action, actor, resource, reason);
        } finally {
            MDC.remove("audit.action");
            MDC.remove("audit.actor");
            MDC.remove("audit.resource");
            MDC.remove("audit.timestamp");
            MDC.remove("audit.security");
        }
    }
}
