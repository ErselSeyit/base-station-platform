-- ============================================================================
-- Flyway V1__baseline.sql for notificationdb (notification-service)
-- Generated from JPA entities using Hibernate default naming strategy
-- (SpringPhysicalNamingStrategy: camelCase -> snake_case)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Table: notifications
-- Entity: io.github.erselseyit.basestation.notification.model.Notification
-- ---------------------------------------------------------------------------
CREATE TABLE notifications (
    id              BIGSERIAL       PRIMARY KEY,
    version         BIGINT,
    station_id      BIGINT          NOT NULL,
    message         VARCHAR(1000)   NOT NULL,
    type            VARCHAR(255)    NOT NULL,
    status          VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMP       NOT NULL,
    updated_at      TIMESTAMP,
    sent_at         TIMESTAMP,
    retry_count     INTEGER         NOT NULL,
    last_error      VARCHAR(500),
    problem_id      VARCHAR(255),
    resolved_at     TIMESTAMP
);

CREATE INDEX idx_station_status ON notifications (station_id, status);
CREATE INDEX idx_created_at ON notifications (created_at);
CREATE INDEX idx_status ON notifications (status);
CREATE INDEX idx_type ON notifications (type);
CREATE INDEX idx_problem_id ON notifications (problem_id);
