package io.github.erselseyit.basestation.tmf.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.erselseyit.basestation.tmf.controller.AlarmManagementController;
import io.github.erselseyit.basestation.tmf.model.Alarm;
import io.github.erselseyit.basestation.tmf.service.AlarmService;

import java.util.List;

/**
 * Security regression test for the production {@link SecurityConfig} (not the
 * permissive test config). Proves that the TMF endpoints, which used to be
 * fully public, now require authentication and enforce read/write role
 * separation. The "test" profile disables the InternalAuthFilter HMAC gate so
 * only the authorization rules under test apply.
 */
@WebMvcTest(AlarmManagementController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class SecurityConfigTest {

    private static final String ALARM_PATH = "/tmf-api/alarmManagement/v4/alarm";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlarmService alarmService;

    @Test
    @WithAnonymousUser
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get(ALARM_PATH))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void prometheusEndpointIsScrapeableWithoutAuth() throws Exception {
        // The scrape endpoint must not be 403: Prometheus scrapes it without
        // auth. It is not mapped in this controller slice, so 404 (passed
        // authorization, no handler) is the expected "permitted" outcome.
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void authenticatedUserCanRead() throws Exception {
        when(alarmService.findAlarms(any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.<Alarm>of()));

        mockMvc.perform(get(ALARM_PATH))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void plainUserCannotWrite() throws Exception {
        // A read-only USER must not be able to create alarms.
        mockMvc.perform(post(ALARM_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCanWrite() throws Exception {
        when(alarmService.create(any(Alarm.class))).thenReturn(new Alarm());

        mockMvc.perform(post(ALARM_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isCreated());
    }
}
