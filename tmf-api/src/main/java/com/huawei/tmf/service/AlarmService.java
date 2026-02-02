package com.huawei.tmf.service;

import com.huawei.tmf.model.Alarm;
import com.huawei.tmf.model.Alarm.AlarmState;
import com.huawei.tmf.model.Alarm.PerceivedSeverity;
import com.huawei.tmf.repository.AlarmRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for TMF642 Alarm Management.
 */
@Service
public class AlarmService {

    private static final Logger log = LoggerFactory.getLogger(AlarmService.class);
    private static final String ALARM_STATE_CHANGE_EVENT = "AlarmStateChangeEvent";

    private final AlarmRepository alarmRepository;
    private final MongoTemplate mongoTemplate;

    // In-memory listener registry
    private final Map<String, Map<String, Object>> listeners = new ConcurrentHashMap<>();

    public AlarmService(AlarmRepository alarmRepository, MongoTemplate mongoTemplate) {
        this.alarmRepository = alarmRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Find alarms with optional filters.
     */
    public Page<Alarm> findAlarms(String state, String perceivedSeverity, String alarmType,
                                   String sourceSystemId, String affectedResourceId,
                                   Pageable pageable) {
        Query query = new Query();

        if (state != null && !state.isEmpty()) {
            query.addCriteria(Criteria.where("state").is(AlarmState.valueOf(state)));
        }
        if (perceivedSeverity != null && !perceivedSeverity.isEmpty()) {
            query.addCriteria(Criteria.where("perceivedSeverity").is(PerceivedSeverity.valueOf(perceivedSeverity)));
        }
        if (alarmType != null && !alarmType.isEmpty()) {
            query.addCriteria(Criteria.where("alarmType").is(Alarm.AlarmType.valueOf(alarmType)));
        }
        if (sourceSystemId != null && !sourceSystemId.isEmpty()) {
            query.addCriteria(Criteria.where("sourceSystemId").is(sourceSystemId));
        }
        if (affectedResourceId != null && !affectedResourceId.isEmpty()) {
            query.addCriteria(Criteria.where("affectedResource.id").is(affectedResourceId));
        }

        long total = mongoTemplate.count(query, Alarm.class);
        query.with(pageable);
        List<Alarm> alarms = mongoTemplate.find(query, Alarm.class);

        return new PageImpl<>(alarms, pageable, total);
    }

    /**
     * Find alarm by ID.
     */
    public Optional<Alarm> findById(String id) {
        return alarmRepository.findById(id);
    }

    /**
     * Find alarms by affected resource ID.
     */
    public List<Alarm> findByAffectedResourceId(String resourceId) {
        return alarmRepository.findByAffectedResourceId(resourceId);
    }

    /**
     * Find active (uncleared) alarms.
     */
    public Page<Alarm> findActiveAlarms(Pageable pageable) {
        return alarmRepository.findActiveAlarms(pageable);
    }

    /**
     * Find alarm history for a resource.
     */
    public Page<Alarm> findAlarmHistory(String resourceId, Pageable pageable) {
        return alarmRepository.findAlarmHistoryByResourceId(resourceId, pageable);
    }

    /**
     * Create a new alarm.
     */
    public Alarm create(Alarm alarm) {
        if (alarm.getId() == null) {
            alarm.setId(UUID.randomUUID().toString());
        }

        Instant now = Instant.now();
        alarm.setAlarmRaisedTime(now);
        alarm.setAlarmReportingTime(now);

        if (alarm.getState() == null) {
            alarm.setState(AlarmState.raised);
        }
        if (alarm.getAckState() == null) {
            alarm.setAckState("unacknowledged");
        }

        Alarm saved = alarmRepository.save(alarm);
        notifyListeners("AlarmCreateEvent", saved);
        return saved;
    }

    /**
     * Partial update of an alarm.
     */
    public Optional<Alarm> patch(String id, Map<String, Object> updates) {
        Optional<Alarm> existing = alarmRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Alarm alarm = existing.get();
        applyPatch(alarm, updates);

        Alarm saved = alarmRepository.save(alarm);
        notifyListeners("AlarmAttributeValueChangeEvent", saved);
        return Optional.of(saved);
    }

    /**
     * Delete an alarm.
     */
    public boolean delete(String id) {
        Optional<Alarm> existing = alarmRepository.findById(id);
        if (existing.isEmpty()) {
            return false;
        }

        alarmRepository.deleteById(id);
        notifyListeners("AlarmDeleteEvent", existing.get());
        return true;
    }

    /**
     * Acknowledge an alarm.
     */
    public Optional<Alarm> acknowledge(String id, String ackUserId, String ackSystemId) {
        Optional<Alarm> existing = alarmRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Alarm alarm = existing.get();
        alarm.setAckState("acknowledged");
        alarm.setAckUserId(ackUserId);
        alarm.setAckSystemId(ackSystemId);
        alarm.setState(AlarmState.updated);

        Alarm saved = alarmRepository.save(alarm);
        notifyListeners(ALARM_STATE_CHANGE_EVENT, saved);
        return Optional.of(saved);
    }

    /**
     * Unacknowledge an alarm.
     */
    public Optional<Alarm> unacknowledge(String id) {
        Optional<Alarm> existing = alarmRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Alarm alarm = existing.get();
        alarm.setAckState("unacknowledged");
        alarm.setAckUserId(null);
        alarm.setAckSystemId(null);
        alarm.setState(AlarmState.updated);

        Alarm saved = alarmRepository.save(alarm);
        notifyListeners(ALARM_STATE_CHANGE_EVENT, saved);
        return Optional.of(saved);
    }

    /**
     * Clear an alarm.
     */
    public Optional<Alarm> clear(String id, String clearUserId, String clearSystemId) {
        Optional<Alarm> existing = alarmRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Alarm alarm = existing.get();
        alarm.setState(AlarmState.cleared);
        alarm.setClearUserId(clearUserId);
        alarm.setClearSystemId(clearSystemId);
        alarm.setAlarmClearedTime(Instant.now());

        Alarm saved = alarmRepository.save(alarm);
        notifyListeners(ALARM_STATE_CHANGE_EVENT, saved);
        return Optional.of(saved);
    }

    /**
     * Add a comment to an alarm.
     */
    public Optional<Alarm> addComment(String id, Map<String, Object> commentData) {
        Optional<Alarm> existing = alarmRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Alarm alarm = existing.get();

        Alarm.Comment comment = new Alarm.Comment();
        comment.setId(UUID.randomUUID().toString());
        comment.setComment((String) commentData.get("comment"));
        comment.setUserId((String) commentData.get("userId"));
        comment.setSystemId((String) commentData.get("systemId"));
        comment.setTime(Instant.now());

        if (alarm.getComment() == null) {
            alarm.setComment(new ArrayList<>());
        }
        alarm.getComment().add(comment);

        Alarm saved = alarmRepository.save(alarm);
        notifyListeners("AlarmCommentEvent", saved);
        return Optional.of(saved);
    }

    /**
     * Group acknowledge multiple alarms.
     */
    public Map<String, Object> groupAcknowledge(List<String> alarmIds, String ackUserId, String ackSystemId) {
        int success = 0;
        int failed = 0;
        List<String> failedIds = new ArrayList<>();

        for (String id : alarmIds) {
            Optional<Alarm> result = acknowledge(id, ackUserId, ackSystemId);
            if (result.isPresent()) {
                success++;
            } else {
                failed++;
                failedIds.add(id);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", success);
        result.put("failedCount", failed);
        result.put("failedIds", failedIds);
        return result;
    }

    /**
     * Group clear multiple alarms.
     */
    public Map<String, Object> groupClear(List<String> alarmIds, String clearUserId, String clearSystemId) {
        int success = 0;
        int failed = 0;
        List<String> failedIds = new ArrayList<>();

        for (String id : alarmIds) {
            Optional<Alarm> result = clear(id, clearUserId, clearSystemId);
            if (result.isPresent()) {
                success++;
            } else {
                failed++;
                failedIds.add(id);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", success);
        result.put("failedCount", failed);
        result.put("failedIds", failedIds);
        return result;
    }

    /**
     * Get alarm statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalAlarms", alarmRepository.count());
        stats.put("activeAlarms", alarmRepository.countActiveAlarms());

        // Count by state
        Map<String, Long> byState = new HashMap<>();
        for (AlarmState state : AlarmState.values()) {
            byState.put(state.name(), alarmRepository.countByState(state));
        }
        stats.put("byState", byState);

        // Count by severity
        Map<String, Long> bySeverity = new HashMap<>();
        for (PerceivedSeverity severity : PerceivedSeverity.values()) {
            bySeverity.put(severity.name(), alarmRepository.countByPerceivedSeverity(severity));
        }
        stats.put("bySeverity", bySeverity);

        return stats;
    }

    /**
     * Register a listener for alarm events.
     */
    public Map<String, Object> registerListener(Map<String, Object> subscription) {
        String id = UUID.randomUUID().toString();
        subscription.put("id", id);
        subscription.put("createdAt", Instant.now().toString());
        listeners.put(id, subscription);
        return subscription;
    }

    /**
     * Unregister a listener.
     */
    public void unregisterListener(String id) {
        listeners.remove(id);
    }

    /**
     * Apply partial updates to an alarm.
     */
    private void applyPatch(Alarm alarm, Map<String, Object> updates) {
        updates.forEach((key, value) -> {
            switch (key) {
                case "state" -> alarm.setState(AlarmState.valueOf((String) value));
                case "ackState" -> alarm.setAckState((String) value);
                case "perceivedSeverity" -> alarm.setPerceivedSeverity(PerceivedSeverity.valueOf((String) value));
                case "probableCause" -> alarm.setProbableCause((String) value);
                case "specificProblem" -> alarm.setSpecificProblem((String) value);
                case "proposedRepairedActions" -> alarm.setProposedRepairedActions((String) value);
                default -> {
                    // Handle other fields if needed
                }
            }
        });
    }

    /**
     * Notify registered listeners of alarm events.
     */
    private void notifyListeners(String eventType, Alarm alarm) {
        listeners.values().forEach(listener -> {
            String callback = (String) listener.get("callback");
            if (callback != null) {
                // Callback notification would be sent via HTTP POST
                log.debug("[TMF642] Event {} for alarm {} -> {}", eventType, alarm.getId(), callback);
            }
        });
    }
}
