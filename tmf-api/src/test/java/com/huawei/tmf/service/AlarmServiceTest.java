package com.huawei.tmf.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.huawei.tmf.model.Alarm;
import com.huawei.tmf.model.Alarm.AlarmState;
import com.huawei.tmf.model.Alarm.PerceivedSeverity;
import com.huawei.tmf.repository.AlarmRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.*;

/**
 * Unit tests for AlarmService.
 */
@ExtendWith(MockitoExtension.class)
class AlarmServiceTest {

    @Mock
    private AlarmRepository alarmRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private AlarmService alarmService;

    private Alarm testAlarm;

    @BeforeEach
    void setUp() {
        testAlarm = createTestAlarm("alarm-1");
    }

    private Alarm createTestAlarm(String id) {
        Alarm alarm = new Alarm();
        alarm.setId(id);
        alarm.setAlarmType(Alarm.AlarmType.communicationsAlarm);
        alarm.setPerceivedSeverity(PerceivedSeverity.major);
        alarm.setState(AlarmState.raised);
        alarm.setAckState("unacknowledged");
        alarm.setProbableCause("Network failure");
        alarm.setSpecificProblem("Link down on interface eth0");
        alarm.setAlarmRaisedTime(Instant.now());
        return alarm;
    }

    // ========== findById Tests ==========

    @Test
    void findById_WhenExists_ReturnsAlarm() {
        when(alarmRepository.findById("alarm-1")).thenReturn(Optional.of(testAlarm));

        Optional<Alarm> result = alarmService.findById("alarm-1");

        assertTrue(result.isPresent());
        assertEquals("alarm-1", result.get().getId());
        verify(alarmRepository).findById("alarm-1");
    }

    @Test
    void findById_WhenNotExists_ReturnsEmpty() {
        when(alarmRepository.findById("non-existent")).thenReturn(Optional.empty());

        Optional<Alarm> result = alarmService.findById("non-existent");

        assertFalse(result.isPresent());
    }

    // ========== create Tests ==========

    @Test
    void create_SetsDefaultValues() {
        Alarm newAlarm = new Alarm();
        newAlarm.setProbableCause("Test cause");

        when(alarmRepository.save(any(Alarm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Alarm result = alarmService.create(newAlarm);

        assertNotNull(result.getId());
        assertNotNull(result.getAlarmRaisedTime());
        assertNotNull(result.getAlarmReportingTime());
        assertEquals(AlarmState.raised, result.getState());
        assertEquals("unacknowledged", result.getAckState());
    }

    @Test
    void create_PreservesExistingId() {
        Alarm alarmWithId = createTestAlarm("custom-id");

        when(alarmRepository.save(any(Alarm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Alarm result = alarmService.create(alarmWithId);

        assertEquals("custom-id", result.getId());
    }

    // ========== acknowledge Tests ==========

    @Test
    void acknowledge_WhenExists_UpdatesAlarm() {
        when(alarmRepository.findById("alarm-1")).thenReturn(Optional.of(testAlarm));
        when(alarmRepository.save(any(Alarm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Alarm> result = alarmService.acknowledge("alarm-1", "user-123", "system-abc");

        assertTrue(result.isPresent());
        assertEquals("acknowledged", result.get().getAckState());
        assertEquals("user-123", result.get().getAckUserId());
        assertEquals("system-abc", result.get().getAckSystemId());
        assertEquals(AlarmState.updated, result.get().getState());
    }

    @Test
    void acknowledge_WhenNotExists_ReturnsEmpty() {
        when(alarmRepository.findById("non-existent")).thenReturn(Optional.empty());

        Optional<Alarm> result = alarmService.acknowledge("non-existent", "user-123", "system-abc");

        assertFalse(result.isPresent());
        verify(alarmRepository, never()).save(any());
    }

    // ========== unacknowledge Tests ==========

    @Test
    void unacknowledge_WhenExists_ResetsAckState() {
        testAlarm.setAckState("acknowledged");
        testAlarm.setAckUserId("user-123");
        when(alarmRepository.findById("alarm-1")).thenReturn(Optional.of(testAlarm));
        when(alarmRepository.save(any(Alarm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Alarm> result = alarmService.unacknowledge("alarm-1");

        assertTrue(result.isPresent());
        assertEquals("unacknowledged", result.get().getAckState());
        assertNull(result.get().getAckUserId());
        assertNull(result.get().getAckSystemId());
    }

    // ========== clear Tests ==========

    @Test
    void clear_WhenExists_SetsCleared() {
        when(alarmRepository.findById("alarm-1")).thenReturn(Optional.of(testAlarm));
        when(alarmRepository.save(any(Alarm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Alarm> result = alarmService.clear("alarm-1", "user-456", "system-xyz");

        assertTrue(result.isPresent());
        assertEquals(AlarmState.cleared, result.get().getState());
        assertEquals("user-456", result.get().getClearUserId());
        assertEquals("system-xyz", result.get().getClearSystemId());
        assertNotNull(result.get().getAlarmClearedTime());
    }

    @Test
    void clear_WhenNotExists_ReturnsEmpty() {
        when(alarmRepository.findById("non-existent")).thenReturn(Optional.empty());

        Optional<Alarm> result = alarmService.clear("non-existent", "user", "system");

        assertFalse(result.isPresent());
    }

    // ========== delete Tests ==========

    @Test
    void delete_WhenExists_ReturnsTrue() {
        when(alarmRepository.findById("alarm-1")).thenReturn(Optional.of(testAlarm));
        doNothing().when(alarmRepository).deleteById("alarm-1");

        boolean result = alarmService.delete("alarm-1");

        assertTrue(result);
        verify(alarmRepository).deleteById("alarm-1");
    }

    @Test
    void delete_WhenNotExists_ReturnsFalse() {
        when(alarmRepository.findById("non-existent")).thenReturn(Optional.empty());

        boolean result = alarmService.delete("non-existent");

        assertFalse(result);
        verify(alarmRepository, never()).deleteById(any());
    }

    // ========== addComment Tests ==========

    @Test
    void addComment_WhenExists_AddsComment() {
        when(alarmRepository.findById("alarm-1")).thenReturn(Optional.of(testAlarm));
        when(alarmRepository.save(any(Alarm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> commentData = new HashMap<>();
        commentData.put("comment", "This is a test comment");
        commentData.put("userId", "user-123");
        commentData.put("systemId", "system-abc");

        Optional<Alarm> result = alarmService.addComment("alarm-1", commentData);

        assertTrue(result.isPresent());
        assertNotNull(result.get().getComment());
        assertEquals(1, result.get().getComment().size());
        assertEquals("This is a test comment", result.get().getComment().get(0).getText());
    }

    // ========== groupAcknowledge Tests ==========

    @Test
    void groupAcknowledge_WithMixedResults_ReturnsCorrectCounts() {
        Alarm alarm2 = createTestAlarm("alarm-2");

        when(alarmRepository.findById("alarm-1")).thenReturn(Optional.of(testAlarm));
        when(alarmRepository.findById("alarm-2")).thenReturn(Optional.of(alarm2));
        when(alarmRepository.findById("non-existent")).thenReturn(Optional.empty());
        when(alarmRepository.save(any(Alarm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<String> alarmIds = Arrays.asList("alarm-1", "alarm-2", "non-existent");
        Map<String, Object> result = alarmService.groupAcknowledge(alarmIds, "user", "system");

        assertEquals(2, result.get("successCount"));
        assertEquals(1, result.get("failedCount"));
        @SuppressWarnings("unchecked")
        List<String> failedIds = (List<String>) result.get("failedIds");
        assertTrue(failedIds.contains("non-existent"));
    }

    // ========== findActiveAlarms Tests ==========

    @Test
    void findActiveAlarms_ReturnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Alarm> expectedPage = new PageImpl<>(List.of(testAlarm), pageable, 1);
        when(alarmRepository.findActiveAlarms(pageable)).thenReturn(expectedPage);

        Page<Alarm> result = alarmService.findActiveAlarms(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("alarm-1", result.getContent().get(0).getId());
    }

    // ========== findByAffectedResourceId Tests ==========

    @Test
    void findByAffectedResourceId_ReturnsAlarms() {
        when(alarmRepository.findByAffectedResourceId("resource-1")).thenReturn(List.of(testAlarm));

        List<Alarm> result = alarmService.findByAffectedResourceId("resource-1");

        assertEquals(1, result.size());
    }
}
