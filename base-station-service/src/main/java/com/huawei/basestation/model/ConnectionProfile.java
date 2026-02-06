package com.huawei.basestation.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Connection profile for establishing communication with base stations.
 * Stores protocol-specific configuration for direct or bridge-mediated connections.
 */
@Entity
@Table(name = "connection_profiles", indexes = {
    @Index(name = "idx_profile_name", columnList = "name"),
    @Index(name = "idx_profile_protocol", columnList = "protocol")
})
@EntityListeners(AuditingEntityListener.class)
public class ConnectionProfile {

    /**
     * Supported connection protocols.
     */
    public enum Protocol {
        TCP,        // Plain TCP socket
        TLS,        // TLS-encrypted TCP
        SERIAL,     // Serial port (RS-232/RS-485)
        SNMP,       // SNMP management
        MQTT,       // MQTT broker connection
        GRPC,       // gRPC bidirectional
        NETCONF     // NETCONF/YANG
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Profile name is required")
    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Protocol protocol = Protocol.TCP;

    /**
     * Host address (IP or hostname) for network protocols.
     */
    private String host;

    /**
     * Port number for network protocols.
     */
    private Integer port;

    /**
     * Serial device path (e.g., /dev/ttyUSB0) for serial connections.
     */
    private String serialDevice;

    /**
     * Baud rate for serial connections.
     */
    private Integer baudRate;

    /**
     * Credential reference (e.g., secret name, not the actual password).
     * Actual credentials are stored in a secrets manager.
     */
    private String credentialRef;

    /**
     * TLS configuration in JSON format (cert paths, verify mode, etc.).
     */
    @Column(length = 2000)
    private String tlsConfig;

    /**
     * Connection timeout in milliseconds.
     */
    private Integer connectionTimeoutMs = 5000;

    /**
     * Read timeout in milliseconds.
     */
    private Integer readTimeoutMs = 30000;

    /**
     * Number of retry attempts on connection failure.
     */
    private Integer retryAttempts = 3;

    /**
     * Whether this profile is currently active/usable.
     */
    private Boolean active = true;

    @Column(length = 500)
    private String description;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public ConnectionProfile() {
    }

    public ConnectionProfile(String name, Protocol protocol) {
        this.name = name;
        this.protocol = protocol;
    }

    // Factory methods for common configurations
    public static ConnectionProfile tcpProfile(String name, String host, int port) {
        ConnectionProfile profile = new ConnectionProfile(name, Protocol.TCP);
        profile.setHost(host);
        profile.setPort(port);
        return profile;
    }

    public static ConnectionProfile tlsProfile(String name, String host, int port, String tlsConfig) {
        ConnectionProfile profile = new ConnectionProfile(name, Protocol.TLS);
        profile.setHost(host);
        profile.setPort(port);
        profile.setTlsConfig(tlsConfig);
        return profile;
    }

    public static ConnectionProfile serialProfile(String name, String device, int baudRate) {
        ConnectionProfile profile = new ConnectionProfile(name, Protocol.SERIAL);
        profile.setSerialDevice(device);
        profile.setBaudRate(baudRate);
        return profile;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getSerialDevice() {
        return serialDevice;
    }

    public void setSerialDevice(String serialDevice) {
        this.serialDevice = serialDevice;
    }

    public Integer getBaudRate() {
        return baudRate;
    }

    public void setBaudRate(Integer baudRate) {
        this.baudRate = baudRate;
    }

    public String getCredentialRef() {
        return credentialRef;
    }

    public void setCredentialRef(String credentialRef) {
        this.credentialRef = credentialRef;
    }

    public String getTlsConfig() {
        return tlsConfig;
    }

    public void setTlsConfig(String tlsConfig) {
        this.tlsConfig = tlsConfig;
    }

    public Integer getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(Integer connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public Integer getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(Integer readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public Integer getRetryAttempts() {
        return retryAttempts;
    }

    public void setRetryAttempts(Integer retryAttempts) {
        this.retryAttempts = retryAttempts;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Returns the connection endpoint as a formatted string.
     */
    public String getEndpoint() {
        if (protocol == Protocol.SERIAL) {
            return serialDevice + "@" + baudRate;
        }
        return host + ":" + port;
    }

    /**
     * Checks if this profile uses network-based protocol.
     */
    public boolean isNetworkProtocol() {
        return protocol != Protocol.SERIAL;
    }
}
