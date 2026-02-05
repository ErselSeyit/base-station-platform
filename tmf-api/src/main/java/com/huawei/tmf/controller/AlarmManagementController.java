package com.huawei.tmf.controller;

import static com.huawei.common.constants.HttpHeaders.HEADER_RESULT_COUNT;
import static com.huawei.common.constants.HttpHeaders.HEADER_TOTAL_COUNT;

import com.huawei.tmf.model.Alarm;
import com.huawei.tmf.service.AlarmService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TMF642 Alarm Management API Controller.
 * Implements TM Forum TMF642 v4.0 specification.
 *
 * Base path: /tmf-api/alarmManagement/v4
 */
@RestController
@RequestMapping("/tmf-api/alarmManagement/v4")
@CrossOrigin(origins = "*")
public class AlarmManagementController {

    private static final String ALARM_RAISED_TIME_FIELD = "alarmRaisedTime";

    private final AlarmService alarmService;

    public AlarmManagementController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    /**
     * List or find Alarms.
     * GET /alarm
     */
    @GetMapping("/alarm")
    public ResponseEntity<List<Alarm>> listAlarms(
            @RequestParam(required = false) String fields,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String perceivedSeverity,
            @RequestParam(required = false) String alarmType,
            @RequestParam(required = false) String sourceSystemId,
            @RequestParam(required = false) String affectedResourceId) {

        PageRequest pageRequest = PageRequest.of(offset / limit, limit, Sort.by(ALARM_RAISED_TIME_FIELD).descending());

        Page<Alarm> alarms = alarmService.findAlarms(
                state, perceivedSeverity, alarmType, sourceSystemId, affectedResourceId, pageRequest);

        return ResponseEntity.ok()
                .header(HEADER_TOTAL_COUNT, String.valueOf(alarms.getTotalElements()))
                .header(HEADER_RESULT_COUNT, String.valueOf(alarms.getNumberOfElements()))
                .body(alarms.getContent());
    }

    /**
     * Retrieve an Alarm by ID.
     * GET /alarm/{id}
     */
    @GetMapping("/alarm/{id}")
    public ResponseEntity<Alarm> getAlarm(
            @PathVariable String id,
            @RequestParam(required = false) String fields) {

        Optional<Alarm> alarm = alarmService.findById(id);
        return alarm.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create an Alarm.
     * POST /alarm
     */
    @PostMapping("/alarm")
    public ResponseEntity<Alarm> createAlarm(@RequestBody Alarm alarm) {
        Alarm created = alarmService.create(alarm);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Partial update of an Alarm.
     * PATCH /alarm/{id}
     *
     * Note: TMF642 typically only allows state changes (ack, clear, comment)
     */
    @PatchMapping("/alarm/{id}")
    public ResponseEntity<Alarm> patchAlarm(
            @PathVariable String id,
            @RequestBody Map<String, Object> updates) {

        Optional<Alarm> patched = alarmService.patch(id, updates);
        return patched.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete an Alarm.
     * DELETE /alarm/{id}
     */
    @DeleteMapping("/alarm/{id}")
    public ResponseEntity<Void> deleteAlarm(@PathVariable String id) {
        boolean deleted = alarmService.delete(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // TMF642 Acknowledgement operations

    /**
     * Acknowledge an Alarm.
     * POST /alarm/{id}/ack
     */
    @PostMapping("/alarm/{id}/ack")
    public ResponseEntity<Alarm> acknowledgeAlarm(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {

        String ackUserId = body != null ? (String) body.get("ackUserId") : null;
        String ackSystemId = body != null ? (String) body.get("ackSystemId") : null;

        Optional<Alarm> acknowledged = alarmService.acknowledge(id, ackUserId, ackSystemId);
        return acknowledged.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Unacknowledge an Alarm.
     * POST /alarm/{id}/unack
     */
    @PostMapping("/alarm/{id}/unack")
    public ResponseEntity<Alarm> unacknowledgeAlarm(@PathVariable String id) {
        Optional<Alarm> unacknowledged = alarmService.unacknowledge(id);
        return unacknowledged.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Clear an Alarm.
     * POST /alarm/{id}/clear
     */
    @PostMapping("/alarm/{id}/clear")
    public ResponseEntity<Alarm> clearAlarm(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {

        String clearUserId = body != null ? (String) body.get("clearUserId") : null;
        String clearSystemId = body != null ? (String) body.get("clearSystemId") : null;

        Optional<Alarm> cleared = alarmService.clear(id, clearUserId, clearSystemId);
        return cleared.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Add a comment to an Alarm.
     * POST /alarm/{id}/comment
     */
    @PostMapping("/alarm/{id}/comment")
    public ResponseEntity<Alarm> addComment(
            @PathVariable String id,
            @RequestBody Map<String, Object> comment) {

        Optional<Alarm> commented = alarmService.addComment(id, comment);
        return commented.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // TMF642 Group operations

    /**
     * Group acknowledge alarms.
     * POST /alarm/groupAck
     */
    @PostMapping("/alarm/groupAck")
    public ResponseEntity<Map<String, Object>> groupAcknowledge(
            @RequestBody Map<String, Object> request) {

        @SuppressWarnings("unchecked")
        List<String> alarmIds = (List<String>) request.get("alarmIds");
        String ackUserId = (String) request.get("ackUserId");
        String ackSystemId = (String) request.get("ackSystemId");

        Map<String, Object> result = alarmService.groupAcknowledge(alarmIds, ackUserId, ackSystemId);
        return ResponseEntity.ok(result);
    }

    /**
     * Group clear alarms.
     * POST /alarm/groupClear
     */
    @PostMapping("/alarm/groupClear")
    public ResponseEntity<Map<String, Object>> groupClear(
            @RequestBody Map<String, Object> request) {

        @SuppressWarnings("unchecked")
        List<String> alarmIds = (List<String>) request.get("alarmIds");
        String clearUserId = (String) request.get("clearUserId");
        String clearSystemId = (String) request.get("clearSystemId");

        Map<String, Object> result = alarmService.groupClear(alarmIds, clearUserId, clearSystemId);
        return ResponseEntity.ok(result);
    }

    // TMF642 Hub (notification subscription) endpoints

    /**
     * Register a listener for alarm events.
     * POST /hub
     */
    @PostMapping("/hub")
    public ResponseEntity<Map<String, Object>> registerListener(
            @RequestBody Map<String, Object> subscription) {

        Map<String, Object> registered = alarmService.registerListener(subscription);
        return ResponseEntity.status(HttpStatus.CREATED).body(registered);
    }

    /**
     * Unregister a listener.
     * DELETE /hub/{id}
     */
    @DeleteMapping("/hub/{id}")
    public ResponseEntity<Void> unregisterListener(@PathVariable String id) {
        alarmService.unregisterListener(id);
        return ResponseEntity.noContent().build();
    }

    // Convenience endpoints

    /**
     * Get alarm statistics.
     */
    @GetMapping("/alarm/stats")
    public ResponseEntity<Map<String, Object>> getAlarmStats() {
        Map<String, Object> stats = alarmService.getStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get alarms by resource ID.
     */
    @GetMapping("/alarm/byResource/{resourceId}")
    public ResponseEntity<List<Alarm>> getAlarmsByResource(@PathVariable String resourceId) {
        List<Alarm> alarms = alarmService.findByAffectedResourceId(resourceId);
        return ResponseEntity.ok(alarms);
    }

    /**
     * Get active (uncleared) alarms.
     */
    @GetMapping("/alarm/active")
    public ResponseEntity<List<Alarm>> getActiveAlarms(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {

        PageRequest pageRequest = PageRequest.of(offset / limit, limit, Sort.by(ALARM_RAISED_TIME_FIELD).descending());
        Page<Alarm> alarms = alarmService.findActiveAlarms(pageRequest);

        return ResponseEntity.ok()
                .header(HEADER_TOTAL_COUNT, String.valueOf(alarms.getTotalElements()))
                .body(alarms.getContent());
    }

    /**
     * Get alarm history for a resource.
     */
    @GetMapping("/alarm/history/{resourceId}")
    public ResponseEntity<List<Alarm>> getAlarmHistory(
            @PathVariable String resourceId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "100") int limit) {

        PageRequest pageRequest = PageRequest.of(offset / limit, limit, Sort.by(ALARM_RAISED_TIME_FIELD).descending());
        Page<Alarm> alarms = alarmService.findAlarmHistory(resourceId, pageRequest);

        return ResponseEntity.ok()
                .header(HEADER_TOTAL_COUNT, String.valueOf(alarms.getTotalElements()))
                .body(alarms.getContent());
    }
}
