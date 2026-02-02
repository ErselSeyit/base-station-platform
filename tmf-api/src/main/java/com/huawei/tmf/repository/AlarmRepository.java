package com.huawei.tmf.repository;

import com.huawei.tmf.model.Alarm;
import com.huawei.tmf.model.Alarm.AlarmState;
import com.huawei.tmf.model.Alarm.PerceivedSeverity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * MongoDB repository for TMF642 Alarm entities.
 */
@Repository
public interface AlarmRepository extends MongoRepository<Alarm, String> {

    /**
     * Find alarms by state.
     */
    List<Alarm> findByState(AlarmState state);

    /**
     * Find alarms by state (paginated).
     */
    Page<Alarm> findByState(AlarmState state, Pageable pageable);

    /**
     * Find alarms by perceived severity.
     */
    List<Alarm> findByPerceivedSeverity(PerceivedSeverity severity);

    /**
     * Find alarms by source system.
     */
    List<Alarm> findBySourceSystemId(String sourceSystemId);

    /**
     * Find alarms by external alarm ID.
     */
    List<Alarm> findByExternalAlarmId(String externalAlarmId);

    /**
     * Find active (not cleared) alarms.
     */
    @Query("{ 'state': { $ne: 'cleared' } }")
    Page<Alarm> findActiveAlarms(Pageable pageable);

    /**
     * Find unacknowledged alarms.
     */
    @Query("{ 'ackState': 'unacknowledged' }")
    List<Alarm> findUnacknowledged();

    /**
     * Find alarms affecting a specific resource.
     */
    @Query("{ 'affectedResource.id': ?0 }")
    List<Alarm> findByAffectedResourceId(String resourceId);

    /**
     * Find alarm history for a resource (including cleared).
     */
    @Query("{ 'affectedResource.id': ?0 }")
    Page<Alarm> findAlarmHistoryByResourceId(String resourceId, Pageable pageable);

    /**
     * Find alarms raised within a time range.
     */
    List<Alarm> findByAlarmRaisedTimeBetween(Instant start, Instant end);

    /**
     * Find alarms by type and severity.
     */
    List<Alarm> findByAlarmTypeAndPerceivedSeverity(Alarm.AlarmType type, PerceivedSeverity severity);

    /**
     * Count alarms by state.
     */
    long countByState(AlarmState state);

    /**
     * Count alarms by severity.
     */
    long countByPerceivedSeverity(PerceivedSeverity severity);

    /**
     * Count active alarms (not cleared).
     */
    @Query(value = "{ 'state': { $ne: 'cleared' } }", count = true)
    long countActiveAlarms();

    /**
     * Find correlated alarms.
     */
    @Query("{ 'correlatedAlarm.id': ?0 }")
    List<Alarm> findCorrelatedAlarms(String parentAlarmId);

    /**
     * Find alarms by probable cause.
     */
    List<Alarm> findByProbableCauseContainingIgnoreCase(String probableCause);
}
