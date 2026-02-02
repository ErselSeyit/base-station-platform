package com.huawei.basestation.repository;

import com.huawei.basestation.model.DeviceCommand;
import com.huawei.basestation.model.DeviceCommand.CommandStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, String> {

    /**
     * Find pending commands for a station, ordered by creation time.
     */
    List<DeviceCommand> findByStationIdAndStatusOrderByCreatedAtAsc(Long stationId, CommandStatus status);

    /**
     * Find all commands for a station.
     */
    List<DeviceCommand> findByStationIdOrderByCreatedAtDesc(Long stationId);

    /**
     * Find commands by diagnostic session.
     */
    List<DeviceCommand> findByDiagnosticSessionId(String diagnosticSessionId);

    /**
     * Find commands by status.
     */
    List<DeviceCommand> findByStatusOrderByCreatedAtDesc(CommandStatus status);

    /**
     * Find recent commands (for dashboard).
     */
    @Query("SELECT c FROM DeviceCommand c WHERE c.createdAt > :since ORDER BY c.createdAt DESC")
    List<DeviceCommand> findRecentCommands(@Param("since") Instant since);

    /**
     * Count pending commands for a station.
     */
    long countByStationIdAndStatus(Long stationId, CommandStatus status);

    /**
     * Find stale in-progress commands (stuck for too long).
     */
    @Query("SELECT c FROM DeviceCommand c WHERE c.status = 'IN_PROGRESS' AND c.pickedUpAt < :timeout")
    List<DeviceCommand> findStaleCommands(@Param("timeout") Instant timeout);
}
