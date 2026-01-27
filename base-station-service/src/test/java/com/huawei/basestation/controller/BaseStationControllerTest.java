package com.huawei.basestation.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.basestation.config.TestConfig;
import com.huawei.basestation.config.TestSecurityConfig;
import com.huawei.basestation.dto.BaseStationDTO;
import com.huawei.basestation.model.StationStatus;
import com.huawei.basestation.model.StationType;
import com.huawei.basestation.service.BaseStationService;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK, classes = { BaseStationController.class, TestConfig.class, TestSecurityConfig.class })
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.data.jpa.repositories.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.cache.type=none"
})
class BaseStationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BaseStationService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @SuppressWarnings("null")
    void createStation_returnsCreatedStation() throws Exception {
        // Verify dependencies are not null
        assertNotNull(objectMapper, "ObjectMapper should be autowired and not null");
        assertNotNull(MediaType.APPLICATION_JSON, "MediaType.APPLICATION_JSON constant should not be null");
        
        BaseStationDTO dto = createTestDTO();
        assertNotNull(dto, "Test DTO should not be null");
        
        BaseStationDTO created = createTestDTO();
        assertNotNull(created, "Created DTO should not be null");
        created.setId(1L);

        // Use any() matcher to match any BaseStationDTO instance (type-safe)
        // The created DTO is verified as non-null above
        when(service.createStation(any(BaseStationDTO.class)))
                .thenReturn(created);

        String jsonContent = serializeToJson(dto);
        assertNotNull(jsonContent, "JSON content should not be null after serialization");
        
        MediaType contentType = getApplicationJsonMediaType();
        assertNotNull(contentType, "Content type should not be null");

        mockMvc.perform(post("/api/v1/stations")
                .contentType(contentType)
                .content(jsonContent))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.stationName").value("BS-001"));
    }

    @Test
    void getStationById_returnsStationWhenFound() throws Exception {
        BaseStationDTO dto = createTestDTO();
        dto.setId(1L);

        when(service.getStationById(1L)).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/api/v1/stations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.stationName").value("BS-001"));
    }

    @Test
    void getStationById_returns404WhenNotFound() throws Exception {
        when(service.getStationById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/stations/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllStations_returnsAllStations() throws Exception {
        BaseStationDTO dto1 = createTestDTO();
        dto1.setId(1L);
        BaseStationDTO dto2 = createTestDTO();
        dto2.setId(2L);
        dto2.setStationName("BS-002");

        when(service.getAllStations()).thenReturn(List.of(dto1, dto2));

        mockMvc.perform(get("/api/v1/stations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @SuppressWarnings("null")
    void updateStation_returnsUpdatedStation() throws Exception {
        // Verify dependencies are not null
        assertNotNull(objectMapper, "ObjectMapper should be autowired and not null");
        assertNotNull(MediaType.APPLICATION_JSON, "MediaType.APPLICATION_JSON constant should not be null");
        
        BaseStationDTO dto = createTestDTO();
        assertNotNull(dto, "Test DTO should not be null");
        dto.setId(1L);
        dto.setStationName("BS-001-Updated");

        // Use any() matcher to match any BaseStationDTO instance (type-safe)
        when(service.updateStation(eq(1L), any(BaseStationDTO.class)))
                .thenReturn(dto);
        
        // Verify the mock return value is not null
        assertNotNull(dto, "Mock service should return a non-null updated DTO");

        String jsonContent = serializeToJson(dto);
        assertNotNull(jsonContent, "JSON content should not be null after serialization");
        
        MediaType contentType = getApplicationJsonMediaType();
        assertNotNull(contentType, "Content type should not be null");

        mockMvc.perform(put("/api/v1/stations/1")
                .contentType(contentType)
                .content(jsonContent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationName").value("BS-001-Updated"));
    }

    @Test
    void deleteStation_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/stations/1"))
                .andExpect(status().isNoContent());
    }

    private BaseStationDTO createTestDTO() {
        BaseStationDTO dto = new BaseStationDTO();
        dto.setStationName("BS-001");
        dto.setLocation("Downtown");
        dto.setLatitude(40.7128);
        dto.setLongitude(-74.0060);
        dto.setStationType(StationType.MACRO_CELL);
        dto.setStatus(StationStatus.ACTIVE);
        return dto;
    }

    /**
     * Safely serializes an object to JSON string with null checks and exception handling.
     * 
     * @param object the object to serialize
     * @return JSON string representation
     * @throws IllegalStateException if objectMapper is null or serialization fails
     */
    private String serializeToJson(Object object) {
        if (objectMapper == null) {
            throw new IllegalStateException("ObjectMapper is null - cannot serialize object to JSON");
        }
        if (object == null) {
            throw new IllegalArgumentException("Cannot serialize null object to JSON");
        }
        try {
            String json = objectMapper.writeValueAsString(object);
            if (json == null) {
                throw new IllegalStateException("ObjectMapper.writeValueAsString returned null");
            }
            return json;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize object to JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Safely retrieves APPLICATION_JSON MediaType with null check.
     * 
     * @return MediaType.APPLICATION_JSON
     * @throws IllegalStateException if MediaType.APPLICATION_JSON is null
     */
    private MediaType getApplicationJsonMediaType() {
        MediaType mediaType = MediaType.APPLICATION_JSON;
        if (mediaType == null) {
            throw new IllegalStateException("MediaType.APPLICATION_JSON constant is null");
        }
        return mediaType;
    }

}
