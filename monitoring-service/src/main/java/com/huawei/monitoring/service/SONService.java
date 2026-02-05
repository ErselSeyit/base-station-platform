package com.huawei.monitoring.service;

import static com.huawei.common.constants.TimeConstants.MILLIS_PER_HOUR;
import static com.huawei.common.constants.TimeConstants.MILLIS_PER_MINUTE;

import com.huawei.monitoring.model.SONRecommendation;
import com.huawei.monitoring.model.SONRecommendation.SONFunction;
import com.huawei.monitoring.model.SONRecommendation.SONStatus;
import com.huawei.monitoring.repository.SONRecommendationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing SON recommendations and their approval workflow.
 */
@Service
@SuppressWarnings("null") // Spring Data repository operations guarantee non-null
public class SONService {

    private static final Logger log = LoggerFactory.getLogger(SONService.class);

    private final SONRecommendationRepository repository;

    public SONService(SONRecommendationRepository repository) {
        this.repository = repository;
    }

    // ========================================================================
    // CRUD Operations
    // ========================================================================

    /**
     * Creates a new SON recommendation.
     */
    public SONRecommendation create(SONRecommendation recommendation) {
        recommendation.setCreatedAt(LocalDateTime.now());
        recommendation.setUpdatedAt(LocalDateTime.now());

        // Set default expiry if not provided (24 hours)
        if (recommendation.getExpiresAt() == null) {
            recommendation.setExpiresAt(LocalDateTime.now().plusHours(24));
        }

        SONRecommendation saved = repository.save(recommendation);
        log.info("Created SON recommendation {} for station {} ({})",
                saved.getId(), saved.getStationId(), saved.getFunctionType());

        return saved;
    }

    /**
     * Gets a recommendation by ID.
     */
    public Optional<SONRecommendation> getById(String id) {
        return repository.findById(id);
    }

    /**
     * Gets all recommendations.
     */
    public List<SONRecommendation> getAll() {
        return repository.findAll();
    }

    /**
     * Gets all recommendations with pagination.
     */
    public Page<SONRecommendation> getAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    /**
     * Gets recommendations for a station.
     */
    public List<SONRecommendation> getByStation(Long stationId) {
        return repository.findByStationId(stationId);
    }

    /**
     * Gets recommendations by status.
     */
    public List<SONRecommendation> getByStatus(SONStatus status) {
        return repository.findByStatus(status);
    }

    /**
     * Gets recommendations by status with pagination.
     */
    public Page<SONRecommendation> getByStatus(SONStatus status, Pageable pageable) {
        return repository.findByStatus(status, pageable);
    }

    /**
     * Gets pending recommendations.
     */
    public List<SONRecommendation> getPending() {
        return repository.findByStatus(SONStatus.PENDING);
    }

    /**
     * Gets pending recommendations for a station.
     */
    public List<SONRecommendation> getPendingForStation(Long stationId) {
        return repository.findByStationIdAndStatus(stationId, SONStatus.PENDING);
    }

    /**
     * Gets recommendations by function type.
     */
    public List<SONRecommendation> getByFunctionType(SONFunction functionType) {
        return repository.findByFunctionType(functionType);
    }

    // ========================================================================
    // Approval Workflow
    // ========================================================================

    /**
     * Approves a recommendation.
     */
    public Optional<SONRecommendation> approve(String id, String username) {
        return repository.findById(id)
                .filter(SONRecommendation::canBeApproved)
                .map(rec -> {
                    rec.approve(username);
                    SONRecommendation saved = repository.save(rec);
                    log.info("SON recommendation {} approved by {}", id, username);
                    return saved;
                });
    }

    /**
     * Rejects a recommendation.
     */
    public Optional<SONRecommendation> reject(String id, String username, String reason) {
        return repository.findById(id)
                .filter(SONRecommendation::canBeApproved)
                .map(rec -> {
                    rec.reject(username, reason);
                    SONRecommendation saved = repository.save(rec);
                    log.info("SON recommendation {} rejected by {}: {}", id, username, reason);
                    return saved;
                });
    }

    /**
     * Marks a recommendation as executing.
     */
    public Optional<SONRecommendation> markExecuting(String id) {
        return repository.findById(id)
                .filter(rec -> rec.getStatus() == SONStatus.APPROVED)
                .map(rec -> {
                    rec.markExecuting();
                    SONRecommendation saved = repository.save(rec);
                    log.info("SON recommendation {} marked as executing", id);
                    return saved;
                });
    }

    /**
     * Records execution result.
     */
    public Optional<SONRecommendation> recordExecutionResult(String id, boolean success, String result) {
        return repository.findById(id)
                .filter(rec -> rec.getStatus() == SONStatus.EXECUTING)
                .map(rec -> {
                    rec.markExecuted(success, result);
                    SONRecommendation saved = repository.save(rec);
                    log.info("SON recommendation {} execution result: success={}", id, success);
                    return saved;
                });
    }

    /**
     * Rolls back an executed recommendation.
     */
    public Optional<SONRecommendation> rollback(String id, String username, String reason) {
        return repository.findById(id)
                .filter(SONRecommendation::canBeRolledBack)
                .map(rec -> {
                    rec.rollback(username, reason);
                    SONRecommendation saved = repository.save(rec);
                    log.info("SON recommendation {} rolled back by {}: {}", id, username, reason);
                    return saved;
                });
    }

    // ========================================================================
    // Statistics
    // ========================================================================

    /**
     * Gets statistics about SON recommendations.
     */
    public Map<String, Object> getStats() {
        long pending = repository.countByStatus(SONStatus.PENDING);
        long approved = repository.countByStatus(SONStatus.APPROVED);
        long executed = repository.countByStatus(SONStatus.EXECUTED);
        long failed = repository.countByStatus(SONStatus.FAILED);
        long rejected = repository.countByStatus(SONStatus.REJECTED);
        long rolledBack = repository.countByStatus(SONStatus.ROLLED_BACK);

        long total = pending + approved + executed + failed + rejected + rolledBack;
        double successRate = total > 0 ? (double) executed / total * 100 : 0;

        return Map.of(
                "pending", pending,
                "approved", approved,
                "executed", executed,
                "failed", failed,
                "rejected", rejected,
                "rolledBack", rolledBack,
                "total", total,
                "successRate", String.format("%.1f%%", successRate)
        );
    }

    /**
     * Gets statistics for a specific station.
     */
    public Map<String, Object> getStatsForStation(Long stationId) {
        long pending = repository.countByStationIdAndStatus(stationId, SONStatus.PENDING);
        long approved = repository.countByStationIdAndStatus(stationId, SONStatus.APPROVED);
        long executed = repository.countByStationIdAndStatus(stationId, SONStatus.EXECUTED);
        long failed = repository.countByStationIdAndStatus(stationId, SONStatus.FAILED);

        return Map.of(
                "stationId", stationId,
                "pending", pending,
                "approved", approved,
                "executed", executed,
                "failed", failed
        );
    }

    // ========================================================================
    // Scheduled Tasks
    // ========================================================================

    /**
     * Expires old pending recommendations.
     * Runs every hour.
     */
    @Scheduled(fixedRate = MILLIS_PER_HOUR)
    public void expireOldRecommendations() {
        List<SONRecommendation> expired = repository.findByStatusAndExpiresAtBefore(
                SONStatus.PENDING, LocalDateTime.now());

        for (SONRecommendation rec : expired) {
            rec.markExpired();
            repository.save(rec);
            log.info("SON recommendation {} expired", rec.getId());
        }

        if (!expired.isEmpty()) {
            log.info("Expired {} SON recommendations", expired.size());
        }
    }

    /**
     * Auto-executes approved recommendations that are auto-executable.
     * Runs every minute.
     */
    @Scheduled(fixedRate = MILLIS_PER_MINUTE)
    public void autoExecuteApproved() {
        List<SONRecommendation> autoExec = repository.findByAutoExecutableTrueAndStatus(SONStatus.APPROVED);

        for (SONRecommendation rec : autoExec) {
            // In a real implementation, this would call the edge bridge to execute
            log.info("Auto-executing SON recommendation {} for station {}",
                    rec.getId(), rec.getStationId());

            rec.markExecuting();
            repository.save(rec);

            // Simulate execution (in real implementation, wait for callback)
            // For now, mark as executed after a brief delay would happen via callback
        }
    }
}
