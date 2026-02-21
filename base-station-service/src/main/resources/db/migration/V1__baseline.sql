-- ============================================================================
-- Flyway V1__baseline.sql for basestationdb (base-station-service)
-- Generated from JPA entities using Hibernate default naming strategy
-- (SpringPhysicalNamingStrategy: camelCase -> snake_case)
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Table: organizations
-- Entity: com.huawei.basestation.model.Organization
-- ---------------------------------------------------------------------------
CREATE TABLE organizations (
    id                BIGSERIAL       PRIMARY KEY,
    name              VARCHAR(255)    NOT NULL,
    slug              VARCHAR(255)    NOT NULL UNIQUE,
    description       VARCHAR(255),
    active            BOOLEAN         NOT NULL,
    tier              VARCHAR(255)    NOT NULL,
    max_stations      INTEGER,
    max_users         INTEGER,
    contact_email     VARCHAR(255),
    contact_phone     VARCHAR(255),
    address           VARCHAR(255),
    country           VARCHAR(255),
    timezone          VARCHAR(255),
    logo_url          VARCHAR(255),
    created_at        TIMESTAMP       NOT NULL,
    updated_at        TIMESTAMP       NOT NULL
);

CREATE UNIQUE INDEX idx_org_slug ON organizations (slug);
CREATE INDEX idx_org_active ON organizations (active);
CREATE INDEX idx_org_tier ON organizations (tier);

-- ---------------------------------------------------------------------------
-- Join table: organization_features (@ElementCollection on Organization)
-- ---------------------------------------------------------------------------
CREATE TABLE organization_features (
    organization_id   BIGINT          NOT NULL,
    feature           VARCHAR(255),
    CONSTRAINT fk_org_features_org FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
);

-- ---------------------------------------------------------------------------
-- Table: base_stations
-- Entity: com.huawei.basestation.model.BaseStation
-- ---------------------------------------------------------------------------
CREATE TABLE base_stations (
    id                    BIGSERIAL           PRIMARY KEY,
    organization_id       BIGINT,
    station_name          VARCHAR(255)        NOT NULL UNIQUE,
    location              VARCHAR(255)        NOT NULL,
    latitude              DOUBLE PRECISION    NOT NULL,
    longitude             DOUBLE PRECISION    NOT NULL,
    station_type          VARCHAR(255)        NOT NULL,
    status                VARCHAR(255)        NOT NULL,
    power_consumption     DOUBLE PRECISION,
    description           VARCHAR(1000),
    ip_address            VARCHAR(45),
    port                  INTEGER,
    management_protocol   VARCHAR(255)        NOT NULL,
    created_at            TIMESTAMP           NOT NULL,
    updated_at            TIMESTAMP           NOT NULL,
    CONSTRAINT fk_base_station_org FOREIGN KEY (organization_id)
        REFERENCES organizations (id)
);

CREATE INDEX idx_geo_coordinates ON base_stations (latitude, longitude);
CREATE INDEX idx_station_status ON base_stations (status);
CREATE INDEX idx_station_type ON base_stations (station_type);
CREATE INDEX idx_station_org ON base_stations (organization_id);

-- ---------------------------------------------------------------------------
-- Table: connection_profiles
-- Entity: com.huawei.basestation.model.ConnectionProfile
-- ---------------------------------------------------------------------------
CREATE TABLE connection_profiles (
    id                      BIGSERIAL       PRIMARY KEY,
    name                    VARCHAR(255)    NOT NULL UNIQUE,
    protocol                VARCHAR(255)    NOT NULL,
    host                    VARCHAR(255),
    port                    INTEGER,
    serial_device           VARCHAR(255),
    baud_rate               INTEGER,
    credential_ref          VARCHAR(255),
    tls_config              VARCHAR(2000),
    connection_timeout_ms   INTEGER,
    read_timeout_ms         INTEGER,
    retry_attempts          INTEGER,
    active                  BOOLEAN,
    description             VARCHAR(500),
    created_at              TIMESTAMP       NOT NULL,
    updated_at              TIMESTAMP       NOT NULL
);

CREATE INDEX idx_profile_name ON connection_profiles (name);
CREATE INDEX idx_profile_protocol ON connection_profiles (protocol);

-- ---------------------------------------------------------------------------
-- Table: device_commands
-- Entity: com.huawei.basestation.model.DeviceCommand
-- @GeneratedValue(strategy = GenerationType.UUID) -> VARCHAR(255) PK
-- ---------------------------------------------------------------------------
CREATE TABLE device_commands (
    id                        VARCHAR(255)    PRIMARY KEY,
    station_id                BIGINT          NOT NULL,
    command_type              VARCHAR(255)    NOT NULL,
    status                    VARCHAR(255)    NOT NULL,
    source                    VARCHAR(255)    NOT NULL,
    diagnostic_session_id     VARCHAR(255),
    son_recommendation_id     VARCHAR(255),
    problem_code              VARCHAR(255),
    confidence                DOUBLE PRECISION,
    risk_level                VARCHAR(255),
    success                   BOOLEAN,
    output                    VARCHAR(4000),
    return_code               INTEGER,
    error_message             VARCHAR(2000),
    created_at                TIMESTAMP       NOT NULL,
    picked_up_at              TIMESTAMP,
    completed_at              TIMESTAMP,
    created_by                VARCHAR(255)
);

-- ---------------------------------------------------------------------------
-- Join table: command_params (@ElementCollection on DeviceCommand)
-- ---------------------------------------------------------------------------
CREATE TABLE command_params (
    command_id    VARCHAR(255)    NOT NULL,
    param_value   VARCHAR(255),
    param_key     VARCHAR(255)    NOT NULL,
    CONSTRAINT fk_command_params_cmd FOREIGN KEY (command_id)
        REFERENCES device_commands (id)
);

-- ---------------------------------------------------------------------------
-- Table: edge_bridge_instances
-- Entity: com.huawei.basestation.model.EdgeBridgeInstance
-- ---------------------------------------------------------------------------
CREATE TABLE edge_bridge_instances (
    id                  BIGSERIAL           PRIMARY KEY,
    bridge_id           VARCHAR(255)        NOT NULL UNIQUE,
    name                VARCHAR(255),
    hostname            VARCHAR(255),
    ip_address          VARCHAR(255),
    version             VARCHAR(255),
    status              VARCHAR(255)        NOT NULL,
    last_heartbeat_at   TIMESTAMP,
    capabilities        VARCHAR(2000),
    callback_url        VARCHAR(255),
    location            VARCHAR(255),
    latitude            DOUBLE PRECISION,
    longitude           DOUBLE PRECISION,
    registered_at       TIMESTAMP           NOT NULL,
    updated_at          TIMESTAMP           NOT NULL
);

CREATE UNIQUE INDEX idx_bridge_id ON edge_bridge_instances (bridge_id);
CREATE INDEX idx_bridge_status ON edge_bridge_instances (status);
CREATE INDEX idx_last_heartbeat ON edge_bridge_instances (last_heartbeat_at);

-- ---------------------------------------------------------------------------
-- Join table: bridge_managed_stations (@ElementCollection on EdgeBridgeInstance)
-- ---------------------------------------------------------------------------
CREATE TABLE bridge_managed_stations (
    bridge_id     BIGINT    NOT NULL,
    station_id    BIGINT,
    CONSTRAINT fk_bridge_managed_stations_bridge FOREIGN KEY (bridge_id)
        REFERENCES edge_bridge_instances (id)
);
