package com.huawei.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

import org.springframework.lang.Nullable;

/**
 * Audit logger for tracking security-relevant events.
 *
 * Logs are written to a dedicated audit log channel using structured JSON format.
 * In production, these should be forwarded to a SIEM or audit log system.
 */
@Component
public class AuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    // MDC key constants - eliminates magic strings (SonarLint java:S1192)
    private static final String MDC_ACTION = "audit.action";
    private static final String MDC_ACTOR = "audit.actor";
    private static final String MDC_RESOURCE = "audit.resource";
    private static final String MDC_TIMESTAMP = "audit.timestamp";
    private static final String MDC_SECURITY = "audit.security";

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
    public void log(AuditAction action, @Nullable String actor, @Nullable String resource, @Nullable String details) {
        try {
            MDC.put(MDC_ACTION, action.name());
            MDC.put(MDC_ACTOR, Objects.requireNonNullElse(actor, "SYSTEM"));
            MDC.put(MDC_RESOURCE, Objects.requireNonNullElse(resource, "N/A"));
            MDC.put(MDC_TIMESTAMP, Instant.now().toString());

            auditLog.info("AUDIT: action={}, actor={}, resource={}, details={}",
                    action, actor, resource, details);
        } finally {
            MDC.remove(MDC_ACTION);
            MDC.remove(MDC_ACTOR);
            MDC.remove(MDC_RESOURCE);
            MDC.remove(MDC_TIMESTAMP);
        }
    }

    /**
     * Log an audit event without details.
     */
    public void log(AuditAction action, @Nullable String actor, @Nullable String resource) {
        log(action, actor, resource, null);
    }

    /**
     * Log a security-relevant failure.
     */
    public void logSecurityEvent(AuditAction action, @Nullable String actor, @Nullable String resource, @Nullable String reason) {
        try {
            MDC.put(MDC_ACTION, action.name());
            MDC.put(MDC_ACTOR, Objects.requireNonNullElse(actor, "UNKNOWN"));
            MDC.put(MDC_RESOURCE, Objects.requireNonNullElse(resource, "N/A"));
            MDC.put(MDC_TIMESTAMP, Instant.now().toString());
            MDC.put(MDC_SECURITY, "true");

            auditLog.warn("SECURITY: action={}, actor={}, resource={}, reason={}",
                    action, actor, resource, reason);
        } finally {
            MDC.remove(MDC_ACTION);
            MDC.remove(MDC_ACTOR);
            MDC.remove(MDC_RESOURCE);
            MDC.remove(MDC_TIMESTAMP);
            MDC.remove(MDC_SECURITY);
        }
    }
}
