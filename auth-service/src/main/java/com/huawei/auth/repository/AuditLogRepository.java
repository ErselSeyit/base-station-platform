package com.huawei.auth.repository;

import com.huawei.auth.model.AuditLog;
import com.huawei.auth.model.AuditLog.EventType;
import com.huawei.auth.model.AuditLog.Severity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for audit log persistence and queries.
 *
 * Provides methods for:
 * - Storing audit events
 * - Querying audit history by user, time range, event type
 * - Compliance reporting
 * - Cleanup of old audit records
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find all audit events for a specific user.
     */
    Page<AuditLog> findByUsernameOrderByTimestampDesc(String username, Pageable pageable);

    /**
     * Find audit events by event type.
     */
    Page<AuditLog> findByEventTypeOrderByTimestampDesc(EventType eventType, Pageable pageable);

    /**
     * Find audit events by severity level.
     */
    Page<AuditLog> findBySeverityOrderByTimestampDesc(Severity severity, Pageable pageable);

    /**
     * Find audit events within a time range.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp BETWEEN :start AND :end ORDER BY a.timestamp DESC")
    Page<AuditLog> findByTimestampBetween(
            @Param("start") Instant start,
            @Param("end") Instant end,
            Pageable pageable);

    /**
     * Find audit events by username and time range.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.username = :username " +
           "AND a.timestamp BETWEEN :start AND :end ORDER BY a.timestamp DESC")
    List<AuditLog> findByUsernameAndTimestampBetween(
            @Param("username") String username,
            @Param("start") Instant start,
            @Param("end") Instant end);

    /**
     * Find audit events from a specific IP address.
     */
    Page<AuditLog> findByClientIpOrderByTimestampDesc(String clientIp, Pageable pageable);

    /**
     * Find critical severity events (for alerting).
     */
    @Query("SELECT a FROM AuditLog a WHERE a.severity = 'CRITICAL' " +
           "AND a.timestamp > :since ORDER BY a.timestamp DESC")
    List<AuditLog> findCriticalEventsSince(@Param("since") Instant since);

    /**
     * Find failed login attempts for a user (for security analysis).
     */
    @Query("SELECT a FROM AuditLog a WHERE a.username = :username " +
           "AND a.eventType = 'LOGIN_FAILURE' " +
           "AND a.timestamp > :since ORDER BY a.timestamp DESC")
    List<AuditLog> findFailedLoginsSince(
            @Param("username") String username,
            @Param("since") Instant since);

    /**
     * Count events by type within a time range (for reporting).
     */
    @Query("SELECT a.eventType, COUNT(a) FROM AuditLog a " +
           "WHERE a.timestamp BETWEEN :start AND :end " +
           "GROUP BY a.eventType")
    List<Object[]> countEventsByType(
            @Param("start") Instant start,
            @Param("end") Instant end);

    /**
     * Count events by severity within a time range.
     */
    @Query("SELECT a.severity, COUNT(a) FROM AuditLog a " +
           "WHERE a.timestamp BETWEEN :start AND :end " +
           "GROUP BY a.severity")
    List<Object[]> countEventsBySeverity(
            @Param("start") Instant start,
            @Param("end") Instant end);

    /**
     * Find suspicious activity patterns - multiple failed logins from same IP.
     */
    @Query("SELECT a.clientIp, COUNT(a) as cnt FROM AuditLog a " +
           "WHERE a.eventType = 'LOGIN_FAILURE' " +
           "AND a.timestamp > :since " +
           "GROUP BY a.clientIp " +
           "HAVING COUNT(a) >= :threshold " +
           "ORDER BY cnt DESC")
    List<Object[]> findSuspiciousIps(
            @Param("since") Instant since,
            @Param("threshold") long threshold);

    /**
     * Delete audit logs older than a specified date (for retention policy).
     */
    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.timestamp < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    /**
     * Search audit logs by message content.
     */
    @Query("SELECT a FROM AuditLog a WHERE LOWER(a.message) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> searchByMessage(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Find recent activity for a specific user (last N events).
     */
    List<AuditLog> findTop50ByUsernameOrderByTimestampDesc(String username);

    /**
     * Combined filter query for audit dashboard.
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:username IS NULL OR a.username = :username) " +
           "AND (:eventType IS NULL OR a.eventType = :eventType) " +
           "AND (:severity IS NULL OR a.severity = :severity) " +
           "AND (:clientIp IS NULL OR a.clientIp = :clientIp) " +
           "AND (:start IS NULL OR a.timestamp >= :start) " +
           "AND (:end IS NULL OR a.timestamp <= :end) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> findWithFilters(
            @Param("username") String username,
            @Param("eventType") EventType eventType,
            @Param("severity") Severity severity,
            @Param("clientIp") String clientIp,
            @Param("start") Instant start,
            @Param("end") Instant end,
            Pageable pageable);
}
