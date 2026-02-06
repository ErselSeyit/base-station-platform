package com.huawei.auth.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Audit log entity for persisting security events.
 *
 * Stores all security-relevant events including:
 * - Authentication attempts (success/failure)
 * - Account lockouts
 * - Token operations
 * - Access control decisions
 *
 * Supports compliance requirements (SOX, PCI-DSS, HIPAA)
 * with immutable audit trail.
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_event_type", columnList = "event_type"),
    @Index(name = "idx_audit_username", columnList = "username"),
    @Index(name = "idx_audit_client_ip", columnList = "client_ip"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_severity", columnList = "severity"),
    @Index(name = "idx_audit_username_timestamp", columnList = "username, timestamp")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "client_ip", length = 45) // IPv6 max length
    private String clientIp;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "request_id", length = 36)
    private String requestId;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(length = 200)
    private String resource;

    @Column(name = "action_result", length = 20)
    private String actionResult;

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
        SUSPICIOUS_ACTIVITY,
        USER_CREATED,
        USER_MODIFIED,
        USER_DELETED,
        ROLE_CHANGED,
        API_ACCESS
    }

    public enum Severity {
        INFO,
        WARNING,
        CRITICAL
    }

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getActionResult() {
        return actionResult;
    }

    public void setActionResult(String actionResult) {
        this.actionResult = actionResult;
    }

    // Builder pattern for convenient creation

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final AuditLog auditLog = new AuditLog();

        public Builder eventType(EventType eventType) {
            auditLog.setEventType(eventType);
            return this;
        }

        public Builder username(String username) {
            auditLog.setUsername(username);
            return this;
        }

        public Builder clientIp(String clientIp) {
            auditLog.setClientIp(clientIp);
            return this;
        }

        public Builder severity(Severity severity) {
            auditLog.setSeverity(severity);
            return this;
        }

        public Builder message(String message) {
            auditLog.setMessage(message);
            return this;
        }

        public Builder details(String details) {
            auditLog.setDetails(details);
            return this;
        }

        public Builder userAgent(String userAgent) {
            auditLog.setUserAgent(userAgent);
            return this;
        }

        public Builder requestId(String requestId) {
            auditLog.setRequestId(requestId);
            return this;
        }

        public Builder sessionId(String sessionId) {
            auditLog.setSessionId(sessionId);
            return this;
        }

        public Builder resource(String resource) {
            auditLog.setResource(resource);
            return this;
        }

        public Builder actionResult(String actionResult) {
            auditLog.setActionResult(actionResult);
            return this;
        }

        public AuditLog build() {
            return auditLog;
        }
    }
}
