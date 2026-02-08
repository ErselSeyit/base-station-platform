package com.huawei.tmf.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.tmf.config.TestSecurityConfig;
import com.huawei.tmf.model.Service;
import com.huawei.tmf.service.ServiceInventoryService;

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

import java.util.*;

/**
 * Controller tests for ServiceInventoryController.
 */
@WebMvcTest(ServiceInventoryController.class)
@Import(TestSecurityConfig.class)
class ServiceInventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServiceInventoryService serviceInventoryService;

    @Autowired
    private ObjectMapper objectMapper;

    private Service testService;

    @BeforeEach
    void setUp() {
        testService = createTestService("service-1");
    }

    private Service createTestService(String id) {
        Service service = new Service();
        service.setId(id);
        service.setHref("/tmf-api/serviceInventoryManagement/v4/service/" + id);
        service.setName("Test Service");
        service.setCategory("CFS");
        service.setState("designed");
        service.setServiceEnabled(false);
        return service;
    }

    // ========== GET /service Tests ==========

    @Test
    void listServices_ReturnsPagedResults() throws Exception {
        List<Service> services = List.of(testService);
        PageImpl<Service> page = new PageImpl<>(services, PageRequest.of(0, 10), 1);

        when(serviceInventoryService.findServices(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(page);

        mockMvc.perform(get("/tmf-api/serviceInventoryManagement/v4/service")
                .param("offset", "0")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("service-1"))
            .andExpect(header().string("X-Total-Count", "1"));
    }

    // ========== GET /service/{id} Tests ==========

    @Test
    void getService_WhenExists_ReturnsService() throws Exception {
        when(serviceInventoryService.findById("service-1")).thenReturn(Optional.of(testService));

        mockMvc.perform(get("/tmf-api/serviceInventoryManagement/v4/service/service-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("service-1"))
            .andExpect(jsonPath("$.name").value("Test Service"));
    }

    @Test
    void getService_WhenNotExists_Returns404() throws Exception {
        when(serviceInventoryService.findById("non-existent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/tmf-api/serviceInventoryManagement/v4/service/non-existent"))
            .andExpect(status().isNotFound());
    }

    // ========== POST /service Tests ==========

    @Test
    void createService_ReturnsCreated() throws Exception {
        Service newService = new Service();
        newService.setName("New Service");

        when(serviceInventoryService.create(any(Service.class))).thenReturn(testService);

        mockMvc.perform(post("/tmf-api/serviceInventoryManagement/v4/service")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newService)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("service-1"));
    }

    // ========== DELETE /service/{id} Tests ==========

    @Test
    void deleteService_WhenExists_Returns204() throws Exception {
        when(serviceInventoryService.delete("service-1")).thenReturn(true);

        mockMvc.perform(delete("/tmf-api/serviceInventoryManagement/v4/service/service-1"))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteService_WhenNotExists_Returns404() throws Exception {
        when(serviceInventoryService.delete("non-existent")).thenReturn(false);

        mockMvc.perform(delete("/tmf-api/serviceInventoryManagement/v4/service/non-existent"))
            .andExpect(status().isNotFound());
    }

    // ========== POST /service/{id}/activate Tests ==========

    @Test
    void activateService_WhenExists_ReturnsActivated() throws Exception {
        testService.setState("active");
        testService.setServiceEnabled(true);
        when(serviceInventoryService.activate("service-1")).thenReturn(Optional.of(testService));

        mockMvc.perform(post("/tmf-api/serviceInventoryManagement/v4/service/service-1/activate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("active"))
            .andExpect(jsonPath("$.serviceEnabled").value(true));
    }

    @Test
    void activateService_WhenNotExists_Returns404() throws Exception {
        when(serviceInventoryService.activate("non-existent")).thenReturn(Optional.empty());

        mockMvc.perform(post("/tmf-api/serviceInventoryManagement/v4/service/non-existent/activate"))
            .andExpect(status().isNotFound());
    }

    // ========== POST /service/{id}/deactivate Tests ==========

    @Test
    void deactivateService_WhenExists_ReturnsDeactivated() throws Exception {
        testService.setState("inactive");
        testService.setServiceEnabled(false);
        when(serviceInventoryService.deactivate("service-1")).thenReturn(Optional.of(testService));

        mockMvc.perform(post("/tmf-api/serviceInventoryManagement/v4/service/service-1/deactivate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("inactive"));
    }

    // ========== POST /service/{id}/terminate Tests ==========

    @Test
    void terminateService_WhenExists_ReturnsTerminated() throws Exception {
        testService.setState("terminated");
        when(serviceInventoryService.terminate("service-1")).thenReturn(Optional.of(testService));

        mockMvc.perform(post("/tmf-api/serviceInventoryManagement/v4/service/service-1/terminate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("terminated"));
    }

    // ========== GET /service/stats Tests ==========

    @Test
    void getStatistics_ReturnsStats() throws Exception {
        Map<String, Object> stats = Map.of(
            "total", 50,
            "byState", Map.of("active", 30, "inactive", 15, "terminated", 5)
        );
        when(serviceInventoryService.getStatistics()).thenReturn(stats);

        mockMvc.perform(get("/tmf-api/serviceInventoryManagement/v4/service/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(50));
    }

    // ========== GET /service/external/{externalId} Tests ==========

    @Test
    void getServiceByExternalId_WhenExists_ReturnsService() throws Exception {
        testService.setExternalId("ext-123");
        when(serviceInventoryService.findByExternalId("ext-123")).thenReturn(Optional.of(testService));

        mockMvc.perform(get("/tmf-api/serviceInventoryManagement/v4/service/external/ext-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("service-1"));
    }
}
