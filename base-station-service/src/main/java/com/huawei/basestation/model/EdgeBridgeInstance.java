package com.huawei.basestation.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a deployed edge-bridge instance that manages one or more base stations.
 * Edge-bridges are Go applications that run near physical stations and handle
 * command execution, metrics collection, and device protocol translation.
 */
@Entity
@Table(name = "edge_bridge_instances", indexes = {
    @Index(name = "idx_bridge_id", columnList = "bridgeId", unique = true),
    @Index(name = "idx_bridge_status", columnList = "status"),
    @Index(name = "idx_last_heartbeat", columnList = "lastHeartbeatAt")
})
@EntityListeners(AuditingEntityListener.class)
public class EdgeBridgeInstance {

    /**
     * Edge-bridge operational status.
     */
    public enum BridgeStatus {
        ONLINE,     // Receiving regular heartbeats
        OFFLINE,    // No heartbeat for > threshold
        DEGRADED,   // Partial functionality
        STARTING,   // Recently registered, initializing
        STOPPED     // Gracefully shut down
    }

    private static final int STALE_THRESHOLD_MINUTES = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique bridge identifier from configuration (e.g., "MIPS-BS-001").
     */
    @NotBlank(message = "Bridge ID is required")
    @Column(nullable = false, unique = true)
    private String bridgeId;

    /**
     * Human-readable name for the bridge instance.
     */
    private String name;

    /**
     * Hostname where the bridge is running.
     */
    private String hostname;

    /**
     * IP address of the bridge (for direct communication if needed).
     */
    private String ipAddress;

    /**
     * Bridge software version.
     */
    private String version;

    /**
     * Current operational status.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BridgeStatus status = BridgeStatus.STARTING;

    /**
     * Last heartbeat received from the bridge.
     */
    private LocalDateTime lastHeartbeatAt;

    /**
     * Capabilities supported by this bridge (JSON format).
     * Example: {"protocols": ["TCP", "SERIAL"], "adapters": ["SNMP", "MQTT"]}
     */
    @Column(length = 2000)
    private String capabilities;

    /**
     * List of station IDs managed by this bridge.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "bridge_managed_stations", joinColumns = @JoinColumn(name = "bridge_id"))
    @Column(name = "station_id")
    private List<Long> managedStationIds = new ArrayList<>();

    /**
     * URL for callback/push notifications (if bridge supports it).
     */
    private String callbackUrl;

    /**
     * Geographic location description (for operational awareness).
     */
    private String location;

    /**
     * Latitude of bridge location.
     */
    private Double latitude;

    /**
     * Longitude of bridge location.
     */
    private Double longitude;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime registeredAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Constructors
    public EdgeBridgeInstance() {
    }

    public EdgeBridgeInstance(String bridgeId) {
        this.bridgeId = bridgeId;
        this.lastHeartbeatAt = LocalDateTime.now();
    }

    // Domain methods

    /**
     * Records a heartbeat, updating status to ONLINE.
     */
    public void recordHeartbeat() {
        this.lastHeartbeatAt = LocalDateTime.now();
        if (this.status != BridgeStatus.ONLINE) {
            this.status = BridgeStatus.ONLINE;
        }
    }

    /**
     * Checks if the bridge is considered stale (no recent heartbeat).
     */
    public boolean isStale() {
        if (lastHeartbeatAt == null) {
            return true;
        }
        return lastHeartbeatAt.plusMinutes(STALE_THRESHOLD_MINUTES).isBefore(LocalDateTime.now());
    }

    /**
     * Marks the bridge as offline due to missing heartbeats.
     */
    public void markOffline() {
        this.status = BridgeStatus.OFFLINE;
    }

    /**
     * Marks the bridge as gracefully stopped.
     */
    public void markStopped() {
        this.status = BridgeStatus.STOPPED;
    }

    /**
     * Checks if bridge manages a specific station.
     */
    public boolean managesStation(Long stationId) {
        return managedStationIds.contains(stationId);
    }

    /**
     * Adds a station to the managed list.
     */
    public void addManagedStation(Long stationId) {
        if (!managedStationIds.contains(stationId)) {
            managedStationIds.add(stationId);
        }
    }

    /**
     * Removes a station from the managed list.
     */
    public void removeManagedStation(Long stationId) {
        managedStationIds.remove(stationId);
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBridgeId() {
        return bridgeId;
    }

    public void setBridgeId(String bridgeId) {
        this.bridgeId = bridgeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public BridgeStatus getStatus() {
        return status;
    }

    public void setStatus(BridgeStatus status) {
        this.status = status;
    }

    public LocalDateTime getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(LocalDateTime lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public String getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(String capabilities) {
        this.capabilities = capabilities;
    }

    public List<Long> getManagedStationIds() {
        return managedStationIds;
    }

    public void setManagedStationIds(List<Long> managedStationIds) {
        this.managedStationIds = managedStationIds;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
