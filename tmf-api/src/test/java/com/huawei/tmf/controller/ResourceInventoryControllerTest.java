package com.huawei.tmf.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.tmf.config.TestSecurityConfig;
import com.huawei.tmf.model.Resource;
import com.huawei.tmf.service.ResourceService;

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
 * Controller tests for ResourceInventoryController.
 */
@WebMvcTest(ResourceInventoryController.class)
@Import(TestSecurityConfig.class)
class ResourceInventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResourceService resourceService;

    @Autowired
    private ObjectMapper objectMapper;

    private Resource testResource;

    @BeforeEach
    void setUp() {
        testResource = createTestResource("resource-1");
    }

    private Resource createTestResource(String id) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setHref("/tmf-api/resourceInventoryManagement/v4/resource/" + id);
        resource.setName("Test Resource");
        resource.setCategory("Equipment");
        resource.setResourceType("PhysicalResource");
        resource.setOperationalState("enabled");
        resource.setAdministrativeState("unlocked");
        return resource;
    }

    // ========== GET /resource Tests ==========

    @Test
    void listResources_ReturnsPagedResults() throws Exception {
        List<Resource> resources = List.of(testResource);
        PageImpl<Resource> page = new PageImpl<>(resources, PageRequest.of(0, 10), 1);

        when(resourceService.findResources(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(page);

        mockMvc.perform(get("/tmf-api/resourceInventoryManagement/v4/resource")
                .param("offset", "0")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("resource-1"))
            .andExpect(header().string("X-Total-Count", "1"));
    }

    @Test
    void listResources_WithFilters_PassesFiltersToService() throws Exception {
        List<Resource> resources = List.of(testResource);
        PageImpl<Resource> page = new PageImpl<>(resources, PageRequest.of(0, 10), 1);

        when(resourceService.findResources(
            eq("Equipment"), eq("PhysicalResource"), any(), any(), any(), any(), any(Pageable.class)))
            .thenReturn(page);

        mockMvc.perform(get("/tmf-api/resourceInventoryManagement/v4/resource")
                .param("category", "Equipment")
                .param("resourceType", "PhysicalResource"))
            .andExpect(status().isOk());
    }

    // ========== GET /resource/{id} Tests ==========

    @Test
    void getResource_WhenExists_ReturnsResource() throws Exception {
        when(resourceService.findById("resource-1")).thenReturn(Optional.of(testResource));

        mockMvc.perform(get("/tmf-api/resourceInventoryManagement/v4/resource/resource-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("resource-1"))
            .andExpect(jsonPath("$.name").value("Test Resource"));
    }

    @Test
    void getResource_WhenNotExists_Returns404() throws Exception {
        when(resourceService.findById("non-existent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/tmf-api/resourceInventoryManagement/v4/resource/non-existent"))
            .andExpect(status().isNotFound());
    }

    // ========== POST /resource Tests ==========

    @Test
    void createResource_ReturnsCreated() throws Exception {
        Resource newResource = new Resource();
        newResource.setName("New Resource");

        when(resourceService.create(any(Resource.class))).thenReturn(testResource);

        mockMvc.perform(post("/tmf-api/resourceInventoryManagement/v4/resource")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newResource)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("resource-1"));
    }

    // ========== PUT /resource/{id} Tests ==========

    @Test
    void updateResource_WhenExists_ReturnsUpdated() throws Exception {
        testResource.setName("Updated Name");
        when(resourceService.findById("resource-1")).thenReturn(Optional.of(testResource));
        when(resourceService.update(any(Resource.class))).thenReturn(testResource);

        mockMvc.perform(put("/tmf-api/resourceInventoryManagement/v4/resource/resource-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testResource)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    void updateResource_WhenNotExists_Returns404() throws Exception {
        when(resourceService.findById("non-existent")).thenReturn(Optional.empty());

        mockMvc.perform(put("/tmf-api/resourceInventoryManagement/v4/resource/non-existent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testResource)))
            .andExpect(status().isNotFound());
    }

    // ========== PATCH /resource/{id} Tests ==========

    @Test
    void patchResource_WhenExists_ReturnsUpdated() throws Exception {
        Map<String, Object> updates = Map.of("operationalState", "disabled");
        testResource.setOperationalState("disabled");

        when(resourceService.patch(eq("resource-1"), any())).thenReturn(Optional.of(testResource));

        mockMvc.perform(patch("/tmf-api/resourceInventoryManagement/v4/resource/resource-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updates)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.operationalState").value("disabled"));
    }

    @Test
    void patchResource_WhenNotExists_Returns404() throws Exception {
        when(resourceService.patch(eq("non-existent"), any())).thenReturn(Optional.empty());

        mockMvc.perform(patch("/tmf-api/resourceInventoryManagement/v4/resource/non-existent")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"New Name\"}"))
            .andExpect(status().isNotFound());
    }

    // ========== DELETE /resource/{id} Tests ==========

    @Test
    void deleteResource_WhenExists_Returns204() throws Exception {
        when(resourceService.delete("resource-1")).thenReturn(true);

        mockMvc.perform(delete("/tmf-api/resourceInventoryManagement/v4/resource/resource-1"))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteResource_WhenNotExists_Returns404() throws Exception {
        when(resourceService.delete("non-existent")).thenReturn(false);

        mockMvc.perform(delete("/tmf-api/resourceInventoryManagement/v4/resource/non-existent"))
            .andExpect(status().isNotFound());
    }

    // ========== GET /resource/stats Tests ==========

    @Test
    void getStatistics_ReturnsStats() throws Exception {
        Map<String, Object> stats = Map.of(
            "total", 100,
            "byCategory", Map.of("Equipment", 60, "Logical", 40)
        );
        when(resourceService.getStatistics()).thenReturn(stats);

        mockMvc.perform(get("/tmf-api/resourceInventoryManagement/v4/resource/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(100));
    }

    // ========== GET /resource/external/{externalId} Tests ==========

    @Test
    void getResourceByExternalId_WhenExists_ReturnsResource() throws Exception {
        testResource.setExternalId("ext-456");
        when(resourceService.findByExternalId("ext-456")).thenReturn(Optional.of(testResource));

        mockMvc.perform(get("/tmf-api/resourceInventoryManagement/v4/resource/external/ext-456"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("resource-1"));
    }

    // ========== GET /resource/{id}/children Tests ==========

    @Test
    void getChildren_ReturnsChildResources() throws Exception {
        Resource childResource = createTestResource("child-1");
        when(resourceService.findChildren("resource-1")).thenReturn(List.of(childResource));

        mockMvc.perform(get("/tmf-api/resourceInventoryManagement/v4/resource/resource-1/children"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("child-1"));
    }
}
