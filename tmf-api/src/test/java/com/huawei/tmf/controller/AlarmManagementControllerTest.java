package com.huawei.tmf.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.tmf.config.TestSecurityConfig;
import com.huawei.tmf.model.Alarm;
import com.huawei.tmf.model.Alarm.AlarmState;
import com.huawei.tmf.model.Alarm.PerceivedSeverity;
import com.huawei.tmf.service.AlarmService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.*;

/**
 * Controller tests for AlarmManagementController.
 */
@WebMvcTest(AlarmManagementController.class)
@Import(TestSecurityConfig.class)
class AlarmManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlarmService alarmService;

    @Autowired
    private ObjectMapper objectMapper;

    private Alarm testAlarm;

    @BeforeEach
    void setUp() {
        testAlarm = createTestAlarm("alarm-1");
    }

    private Alarm createTestAlarm(String id) {
        Alarm alarm = new Alarm();
        alarm.setId(id);
        alarm.setHref("/tmf-api/alarmManagement/v4/alarm/" + id);
        alarm.setAlarmType(Alarm.AlarmType.communicationsAlarm);
        alarm.setPerceivedSeverity(PerceivedSeverity.major);
        alarm.setState(AlarmState.raised);
        alarm.setAckState("unacknowledged");
        alarm.setProbableCause("Network failure");
        alarm.setSpecificProblem("Link down on interface eth0");
        alarm.setAlarmRaisedTime(Instant.now());
        return alarm;
    }

    // ========== GET /alarm Tests ==========

    @Test
    void listAlarms_ReturnsPagedResults() throws Exception {
        List<Alarm> alarms = List.of(testAlarm);
        PageImpl<Alarm> page = new PageImpl<>(alarms, PageRequest.of(0, 10), 1);

        when(alarmService.findAlarms(any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(page);

        mockMvc.perform(get("/tmf-api/alarmManagement/v4/alarm")
                .param("offset", "0")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("alarm-1"))
            .andExpect(jsonPath("$[0].perceivedSeverity").value("major"))
            .andExpect(header().string("X-Total-Count", "1"));
    }

    @Test
    void listAlarms_WithFilters_PassesFiltersToService() throws Exception {
        List<Alarm> alarms = List.of(testAlarm);
        PageImpl<Alarm> page = new PageImpl<>(alarms, PageRequest.of(0, 10), 1);

        when(alarmService.findAlarms(
            eq("raised"), eq("major"), eq("communicationsAlarm"), any(), any(), any(Pageable.class)))
            .thenReturn(page);

        mockMvc.perform(get("/tmf-api/alarmManagement/v4/alarm")
                .param("state", "raised")
                .param("perceivedSeverity", "major")
                .param("alarmType", "communicationsAlarm"))
            .andExpect(status().isOk());
    }

    // ========== GET /alarm/{id} Tests ==========

    @Test
    void getAlarm_WhenExists_ReturnsAlarm() throws Exception {
        when(alarmService.findById("alarm-1")).thenReturn(Optional.of(testAlarm));

        mockMvc.perform(get("/tmf-api/alarmManagement/v4/alarm/alarm-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("alarm-1"))
            .andExpect(jsonPath("$.alarmType").value("communicationsAlarm"));
    }

    @Test
    void getAlarm_WhenNotExists_Returns404() throws Exception {
        when(alarmService.findById("non-existent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/tmf-api/alarmManagement/v4/alarm/non-existent"))
            .andExpect(status().isNotFound());
    }

    // ========== POST /alarm Tests ==========

    @Test
    void createAlarm_ReturnsCreated() throws Exception {
        Alarm newAlarm = new Alarm();
        newAlarm.setProbableCause("Test cause");
        newAlarm.setPerceivedSeverity(PerceivedSeverity.minor);

        when(alarmService.create(any(Alarm.class))).thenReturn(testAlarm);

        mockMvc.perform(post("/tmf-api/alarmManagement/v4/alarm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newAlarm)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("alarm-1"));
    }

    // ========== PATCH /alarm/{id} Tests ==========

    @Test
    void patchAlarm_WhenExists_ReturnsUpdated() throws Exception {
        Map<String, Object> updates = Map.of("perceivedSeverity", "critical");
        testAlarm.setPerceivedSeverity(PerceivedSeverity.critical);

        when(alarmService.patch(eq("alarm-1"), any())).thenReturn(Optional.of(testAlarm));

        mockMvc.perform(patch("/tmf-api/alarmManagement/v4/alarm/alarm-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updates)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.perceivedSeverity").value("critical"));
    }

    @Test
    void patchAlarm_WhenNotExists_Returns404() throws Exception {
        Map<String, Object> updates = Map.of("perceivedSeverity", "critical");
        when(alarmService.patch(eq("non-existent"), any())).thenReturn(Optional.empty());

        mockMvc.perform(patch("/tmf-api/alarmManagement/v4/alarm/non-existent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updates)))
            .andExpect(status().isNotFound());
    }

    // ========== DELETE /alarm/{id} Tests ==========

    @Test
    void deleteAlarm_WhenExists_Returns204() throws Exception {
        when(alarmService.delete("alarm-1")).thenReturn(true);

        mockMvc.perform(delete("/tmf-api/alarmManagement/v4/alarm/alarm-1"))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteAlarm_WhenNotExists_Returns404() throws Exception {
        when(alarmService.delete("non-existent")).thenReturn(false);

        mockMvc.perform(delete("/tmf-api/alarmManagement/v4/alarm/non-existent"))
            .andExpect(status().isNotFound());
    }

    // ========== POST /alarm/{id}/ack Tests ==========

    @Test
    void acknowledgeAlarm_WhenExists_ReturnsUpdated() throws Exception {
        testAlarm.setAckState("acknowledged");
        when(alarmService.acknowledge(eq("alarm-1"), any(), any())).thenReturn(Optional.of(testAlarm));

        mockMvc.perform(post("/tmf-api/alarmManagement/v4/alarm/alarm-1/ack")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ackUserId\": \"user-123\", \"ackSystemId\": \"system-abc\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ackState").value("acknowledged"));
    }

    @Test
    void acknowledgeAlarm_WhenNotExists_Returns404() throws Exception {
        when(alarmService.acknowledge(eq("non-existent"), any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/tmf-api/alarmManagement/v4/alarm/non-existent/ack")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound());
    }

    // ========== POST /alarm/{id}/clear Tests ==========

    @Test
    void clearAlarm_WhenExists_ReturnsUpdated() throws Exception {
        testAlarm.setState(AlarmState.cleared);
        when(alarmService.clear(eq("alarm-1"), any(), any())).thenReturn(Optional.of(testAlarm));

        mockMvc.perform(post("/tmf-api/alarmManagement/v4/alarm/alarm-1/clear")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clearUserId\": \"user-456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("cleared"));
    }

    // ========== POST /alarm/{id}/comment Tests ==========

    @Test
    void addComment_WhenExists_ReturnsUpdated() throws Exception {
        when(alarmService.addComment(eq("alarm-1"), any())).thenReturn(Optional.of(testAlarm));

        mockMvc.perform(post("/tmf-api/alarmManagement/v4/alarm/alarm-1/comment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\": \"Test comment\", \"userId\": \"user-123\"}"))
            .andExpect(status().isOk());
    }

    // ========== GET /alarm/stats Tests ==========

    @Test
    void getStatistics_ReturnsStats() throws Exception {
        Map<String, Object> stats = Map.of(
            "total", 100,
            "activeCount", 75,
            "byState", Map.of("raised", 50, "updated", 25, "cleared", 25)
        );
        when(alarmService.getStatistics()).thenReturn(stats);

        mockMvc.perform(get("/tmf-api/alarmManagement/v4/alarm/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(100))
            .andExpect(jsonPath("$.activeCount").value(75));
    }

    // ========== GET /alarm/active Tests ==========

    @Test
    void getActiveAlarms_ReturnsActiveOnly() throws Exception {
        PageImpl<Alarm> page = new PageImpl<>(List.of(testAlarm), PageRequest.of(0, 10), 1);
        when(alarmService.findActiveAlarms(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/tmf-api/alarmManagement/v4/alarm/active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("alarm-1"));
    }

    // ========== POST /alarm/groupAck Tests ==========

    @Test
    void groupAcknowledge_ReturnsResults() throws Exception {
        Map<String, Object> result = Map.of(
            "successCount", 2,
            "failedCount", 1,
            "failedIds", List.of("non-existent")
        );
        when(alarmService.groupAcknowledge(any(), any(), any())).thenReturn(result);

        mockMvc.perform(post("/tmf-api/alarmManagement/v4/alarm/groupAck")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alarmIds\": [\"alarm-1\", \"alarm-2\", \"non-existent\"], \"ackUserId\": \"user\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.successCount").value(2))
            .andExpect(jsonPath("$.failedCount").value(1));
    }
}
