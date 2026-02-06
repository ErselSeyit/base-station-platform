package com.huawei.basestation.service;

import com.huawei.basestation.model.EdgeBridgeInstance;
import com.huawei.basestation.model.EdgeBridgeInstance.BridgeStatus;
import com.huawei.basestation.repository.EdgeBridgeRepository;
import com.huawei.common.audit.AuditLogger;
import com.huawei.common.audit.AuditLogger.AuditAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.huawei.common.constants.ServiceNames.SYSTEM_ACTOR;

/**
 * Service for managing edge-bridge instances.
 * Handles registration, heartbeat, and lifecycle management.
 */
@Service
@Transactional
@SuppressWarnings("null") // Spring Data repository methods and record accessors guarantee non-null for present values
public class EdgeBridgeService {

    private static final Logger log = LoggerFactory.getLogger(EdgeBridgeService.class);
    private static final String AUDIT_RESOURCE_PREFIX = "edge-bridge:";
    private static final int STALE_THRESHOLD_MINUTES = 2;

    private final EdgeBridgeRepository repository;
    private final AuditLogger auditLogger;

    public EdgeBridgeService(EdgeBridgeRepository repository, AuditLogger auditLogger) {
        this.repository = repository;
        this.auditLogger = auditLogger;
    }

    /**
     * Registers or updates an edge-bridge instance.
     * If bridge with same bridgeId exists, updates it; otherwise creates new.
     */
    public EdgeBridgeInstance register(EdgeBridgeRegistration registration) {
        Objects.requireNonNull(registration, "Registration cannot be null");
        Objects.requireNonNull(registration.bridgeId(), "Bridge ID is required");

        Optional<EdgeBridgeInstance> existing = repository.findByBridgeId(registration.bridgeId());

        EdgeBridgeInstance bridge;
        if (existing.isPresent()) {
            bridge = existing.get();
            updateBridgeFromRegistration(bridge, registration);
            log.info("Updated edge-bridge registration: {}", registration.bridgeId());
        } else {
            bridge = createBridgeFromRegistration(registration);
            log.info("New edge-bridge registered: {}", registration.bridgeId());
            auditLogger.log(AuditAction.EDGE_BRIDGE_REGISTERED, SYSTEM_ACTOR,
                    AUDIT_RESOURCE_PREFIX + registration.bridgeId(), "New edge-bridge registered");
        }

        bridge.recordHeartbeat();
        return repository.save(bridge);
    }

    /**
     * Records a heartbeat from an edge-bridge.
     */
    public Optional<EdgeBridgeInstance> heartbeat(String bridgeId) {
        Objects.requireNonNull(bridgeId, "Bridge ID is required");

        Optional<EdgeBridgeInstance> found = repository.findByBridgeId(bridgeId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        EdgeBridgeInstance bridge = found.get();
        bridge.recordHeartbeat();
        log.debug("Heartbeat received from bridge: {}", bridgeId);
        return Optional.of(repository.save(bridge));
    }

    /**
     * Gets all registered edge-bridges.
     */
    @Transactional(readOnly = true)
    public List<EdgeBridgeInstance> getAllBridges() {
        return repository.findAll();
    }

    /**
     * Gets bridges by status.
     */
    @Transactional(readOnly = true)
    public List<EdgeBridgeInstance> getBridgesByStatus(BridgeStatus status) {
        return repository.findByStatus(status);
    }

    /**
     * Gets online bridges ordered by recent heartbeat.
     */
    @Transactional(readOnly = true)
    public List<EdgeBridgeInstance> getOnlineBridges() {
        return repository.findByStatusOrderByLastHeartbeatAtDesc(BridgeStatus.ONLINE);
    }

    /**
     * Gets bridge by its unique ID.
     */
    @Transactional(readOnly = true)
    public Optional<EdgeBridgeInstance> getBridgeById(String bridgeId) {
        return repository.findByBridgeId(bridgeId);
    }

    /**
     * Gets the bridge managing a specific station.
     */
    @Transactional(readOnly = true)
    public Optional<EdgeBridgeInstance> getBridgeForStation(Long stationId) {
        return repository.findByManagedStation(stationId);
    }

    /**
     * Manually marks a bridge as offline.
     */
    public Optional<EdgeBridgeInstance> markOffline(String bridgeId) {
        Optional<EdgeBridgeInstance> found = repository.findByBridgeId(bridgeId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        EdgeBridgeInstance bridge = found.get();
        bridge.markOffline();
        log.info("Bridge {} manually marked offline", bridgeId);
        auditLogger.log(AuditAction.EDGE_BRIDGE_OFFLINE, SYSTEM_ACTOR,
                AUDIT_RESOURCE_PREFIX + bridgeId, "Manually marked offline");
        return Optional.of(repository.save(bridge));
    }

    /**
     * Gracefully stops a bridge (called when bridge shuts down).
     */
    public Optional<EdgeBridgeInstance> markStopped(String bridgeId) {
        Optional<EdgeBridgeInstance> found = repository.findByBridgeId(bridgeId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        EdgeBridgeInstance bridge = found.get();
        bridge.markStopped();
        log.info("Bridge {} gracefully stopped", bridgeId);
        return Optional.of(repository.save(bridge));
    }

    /**
     * Updates the list of stations managed by a bridge.
     */
    public Optional<EdgeBridgeInstance> updateManagedStations(String bridgeId, List<Long> stationIds) {
        Optional<EdgeBridgeInstance> found = repository.findByBridgeId(bridgeId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        EdgeBridgeInstance bridge = found.get();
        bridge.setManagedStationIds(stationIds);
        log.info("Updated managed stations for bridge {}: {}", bridgeId, stationIds);
        return Optional.of(repository.save(bridge));
    }

    /**
     * Scheduled task to detect stale bridges and mark them offline.
     * Runs every minute.
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupStaleBridges() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);
        List<EdgeBridgeInstance> staleBridges = repository.findStaleOnlineBridges(threshold);

        for (EdgeBridgeInstance bridge : staleBridges) {
            bridge.markOffline();
            repository.save(bridge);
            log.warn("Bridge {} marked offline due to missing heartbeat (last: {})",
                    bridge.getBridgeId(), bridge.getLastHeartbeatAt());
            auditLogger.log(AuditAction.EDGE_BRIDGE_OFFLINE, SYSTEM_ACTOR,
                    AUDIT_RESOURCE_PREFIX + bridge.getBridgeId(),
                    "Auto-marked offline due to stale heartbeat");
        }

        if (!staleBridges.isEmpty()) {
            log.info("Cleanup completed: {} stale bridges marked offline", staleBridges.size());
        }
    }

    /**
     * Gets summary statistics for edge-bridges.
     */
    @Transactional(readOnly = true)
    public BridgeStats getStats() {
        long online = repository.countByStatus(BridgeStatus.ONLINE);
        long offline = repository.countByStatus(BridgeStatus.OFFLINE);
        long degraded = repository.countByStatus(BridgeStatus.DEGRADED);
        long total = repository.count();
        return new BridgeStats(total, online, offline, degraded);
    }

    // Helper methods

    @SuppressWarnings("java:S2589") // Null check intentional - managedStationIds is nullable in record
    private EdgeBridgeInstance createBridgeFromRegistration(EdgeBridgeRegistration reg) {
        EdgeBridgeInstance bridge = new EdgeBridgeInstance(reg.bridgeId());
        bridge.setName(reg.name());
        bridge.setHostname(reg.hostname());
        bridge.setIpAddress(reg.ipAddress());
        bridge.setVersion(reg.version());
        bridge.setCapabilities(reg.capabilities());
        bridge.setLocation(reg.location());
        bridge.setLatitude(reg.latitude());
        bridge.setLongitude(reg.longitude());
        bridge.setCallbackUrl(reg.callbackUrl());
        if (reg.managedStationIds() != null) {
            bridge.setManagedStationIds(reg.managedStationIds());
        }
        bridge.setStatus(BridgeStatus.STARTING);
        return bridge;
    }

    /**
     * Updates bridge fields from registration, preserving null fields (partial update semantics).
     * Null checks are intentional to support partial updates where only provided fields are changed.
     */
    @SuppressWarnings("java:S2589") // Null checks intentional for partial update semantics
    private void updateBridgeFromRegistration(EdgeBridgeInstance bridge, EdgeBridgeRegistration reg) {
        if (reg.name() != null) bridge.setName(reg.name());
        if (reg.hostname() != null) bridge.setHostname(reg.hostname());
        if (reg.ipAddress() != null) bridge.setIpAddress(reg.ipAddress());
        if (reg.version() != null) bridge.setVersion(reg.version());
        if (reg.capabilities() != null) bridge.setCapabilities(reg.capabilities());
        if (reg.location() != null) bridge.setLocation(reg.location());
        if (reg.latitude() != null) bridge.setLatitude(reg.latitude());
        if (reg.longitude() != null) bridge.setLongitude(reg.longitude());
        if (reg.callbackUrl() != null) bridge.setCallbackUrl(reg.callbackUrl());
        if (reg.managedStationIds() != null) bridge.setManagedStationIds(reg.managedStationIds());
    }

    // DTOs

    /**
     * Registration request from edge-bridge.
     */
    public record EdgeBridgeRegistration(
            String bridgeId,
            String name,
            String hostname,
            String ipAddress,
            String version,
            String capabilities,
            String location,
            Double latitude,
            Double longitude,
            String callbackUrl,
            List<Long> managedStationIds
    ) {}

    /**
     * Bridge statistics.
     */
    public record BridgeStats(
            long total,
            long online,
            long offline,
            long degraded
    ) {}
}
