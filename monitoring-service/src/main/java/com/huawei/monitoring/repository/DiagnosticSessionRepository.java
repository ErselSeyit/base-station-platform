package com.huawei.monitoring.repository;

import com.huawei.monitoring.model.DiagnosticSession;
import com.huawei.monitoring.model.DiagnosticStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for DiagnosticSession entities.
 */
@Repository
public interface DiagnosticSessionRepository extends MongoRepository<DiagnosticSession, String> {

    /**
     * Find session by external problem ID.
     */
    Optional<DiagnosticSession> findByProblemId(String problemId);

    /**
     * Find all sessions for a station.
     */
    List<DiagnosticSession> findByStationIdOrderByCreatedAtDesc(Long stationId);

    /**
     * Find sessions by status.
     */
    List<DiagnosticSession> findByStatusOrderByCreatedAtDesc(DiagnosticStatus status);

    /**
     * Find sessions pending confirmation.
     */
    @Query("{ 'status': 'PENDING_CONFIRMATION' }")
    List<DiagnosticSession> findPendingConfirmation();

    /**
     * Find sessions for a station with a specific status.
     */
    List<DiagnosticSession> findByStationIdAndStatusOrderByCreatedAtDesc(Long stationId, DiagnosticStatus status);

    /**
     * Find sessions by problem code (for pattern learning).
     */
    List<DiagnosticSession> findByProblemCodeOrderByCreatedAtDesc(String problemCode);

    /**
     * Find resolved sessions for a problem code (for learning from successful solutions).
     */
    @Query("{ 'problemCode': ?0, 'status': 'RESOLVED' }")
    List<DiagnosticSession> findResolvedByProblemCode(String problemCode);

    /**
     * Find failed sessions for a problem code (for learning from failures).
     */
    @Query("{ 'problemCode': ?0, 'status': 'FAILED' }")
    List<DiagnosticSession> findFailedByProblemCode(String problemCode);

    /**
     * Find sessions within a time range.
     */
    List<DiagnosticSession> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    /**
     * Count sessions by status for statistics.
     */
    long countByStatus(DiagnosticStatus status);

    /**
     * Count sessions by problem code.
     */
    long countByProblemCode(String problemCode);

    /**
     * Find sessions with feedback rating for a problem code (for calculating average rating).
     */
    @Query("{ 'problemCode': ?0, 'feedback.rating': { $exists: true } }")
    List<DiagnosticSession> findByProblemCodeWithRating(String problemCode);

    /**
     * Count sessions that were auto-applied (high confidence).
     */
    long countByAutoApplied(boolean autoApplied);

    /**
     * Find recent auto-applied sessions (for monitoring).
     */
    @Query("{ 'autoApplied': true }")
    List<DiagnosticSession> findAutoAppliedOrderByCreatedAtDesc();

    /**
     * Find all sessions ordered by creation date (most recent first).
     */
    List<DiagnosticSession> findAllByOrderByCreatedAtDesc();

    /**
     * Find active sessions for a station and problem code (for deduplication).
     * Active means: DETECTED, DIAGNOSED, APPLIED, or PENDING_CONFIRMATION.
     */
    @Query("{ 'stationId': ?0, 'problemCode': ?1, 'status': { $in: ['DETECTED', 'DIAGNOSED', 'APPLIED', 'PENDING_CONFIRMATION'] } }")
    List<DiagnosticSession> findActiveByStationIdAndProblemCode(Long stationId, String problemCode);
}
