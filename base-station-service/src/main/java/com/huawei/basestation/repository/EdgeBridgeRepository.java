package com.huawei.basestation.repository;

import com.huawei.basestation.model.EdgeBridgeInstance;
import com.huawei.basestation.model.EdgeBridgeInstance.BridgeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EdgeBridgeRepository extends JpaRepository<EdgeBridgeInstance, Long> {

    /**
     * Find edge-bridge by its unique bridge ID.
     */
    Optional<EdgeBridgeInstance> findByBridgeId(String bridgeId);

    /**
     * Check if bridge ID exists.
     */
    boolean existsByBridgeId(String bridgeId);

    /**
     * Find bridges by status.
     */
    List<EdgeBridgeInstance> findByStatus(BridgeStatus status);

    /**
     * Find all online bridges.
     */
    List<EdgeBridgeInstance> findByStatusOrderByLastHeartbeatAtDesc(BridgeStatus status);

    /**
     * Find bridges with stale heartbeat (potential offline).
     */
    @Query("SELECT e FROM EdgeBridgeInstance e WHERE e.lastHeartbeatAt < :threshold AND e.status = 'ONLINE'")
    List<EdgeBridgeInstance> findStaleOnlineBridges(@Param("threshold") LocalDateTime threshold);

    /**
     * Find bridge managing a specific station.
     */
    @Query("SELECT e FROM EdgeBridgeInstance e WHERE :stationId MEMBER OF e.managedStationIds")
    Optional<EdgeBridgeInstance> findByManagedStation(@Param("stationId") Long stationId);

    /**
     * Count bridges by status.
     */
    long countByStatus(BridgeStatus status);

    /**
     * Find bridges by hostname pattern (for search).
     */
    List<EdgeBridgeInstance> findByHostnameContainingIgnoreCase(String hostname);
}
