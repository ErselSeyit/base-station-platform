-- ============================================================================
-- Flyway V1__baseline.sql for authdb (auth-service)
-- Generated from JPA entities using Hibernate default naming strategy
-- (SpringPhysicalNamingStrategy: camelCase -> snake_case)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Table: users
-- Entity: com.huawei.auth.model.User
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id              BIGSERIAL       PRIMARY KEY,
    username        VARCHAR(50)     NOT NULL UNIQUE,
    password_hash   VARCHAR(255)    NOT NULL,
    role            VARCHAR(50)     NOT NULL,
    enabled         BOOLEAN         NOT NULL,
    created_at      TIMESTAMP       NOT NULL,
    updated_at      TIMESTAMP
);

CREATE INDEX idx_user_username ON users (username);
CREATE INDEX idx_user_enabled ON users (enabled);
CREATE INDEX idx_user_created_at ON users (created_at);

-- ---------------------------------------------------------------------------
-- Table: refresh_tokens
-- Entity: com.huawei.auth.model.RefreshToken
-- ---------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id              BIGSERIAL       PRIMARY KEY,
    token           VARCHAR(36)     NOT NULL UNIQUE,
    user_id         BIGINT          NOT NULL,
    expires_at      TIMESTAMP       NOT NULL,
    created_at      TIMESTAMP       NOT NULL,
    revoked         BOOLEAN         NOT NULL,
    revoked_at      TIMESTAMP,
    revoke_reason   VARCHAR(255),
    client_ip       VARCHAR(45),
    user_agent      VARCHAR(255),
    device_id       VARCHAR(64),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id)
        REFERENCES users (id)
);

CREATE UNIQUE INDEX idx_refresh_token ON refresh_tokens (token);
CREATE INDEX idx_refresh_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_expiry ON refresh_tokens (expires_at);
CREATE INDEX idx_refresh_revoked ON refresh_tokens (revoked);

-- ---------------------------------------------------------------------------
-- Table: audit_logs
-- Entity: com.huawei.auth.model.AuditLog
-- ---------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id              BIGSERIAL       PRIMARY KEY,
    event_type      VARCHAR(50)     NOT NULL,
    username        VARCHAR(100)    NOT NULL,
    client_ip       VARCHAR(45),
    severity        VARCHAR(20)     NOT NULL,
    message         VARCHAR(500)    NOT NULL,
    details         TEXT,
    timestamp       TIMESTAMP       NOT NULL,
    user_agent      VARCHAR(500),
    request_id      VARCHAR(36),
    session_id      VARCHAR(64),
    resource        VARCHAR(200),
    action_result   VARCHAR(20)
);

CREATE INDEX idx_audit_event_type ON audit_logs (event_type);
CREATE INDEX idx_audit_username ON audit_logs (username);
CREATE INDEX idx_audit_client_ip ON audit_logs (client_ip);
CREATE INDEX idx_audit_timestamp ON audit_logs (timestamp);
CREATE INDEX idx_audit_severity ON audit_logs (severity);
CREATE INDEX idx_audit_username_timestamp ON audit_logs (username, timestamp);
