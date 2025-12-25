package com.huawei.basestation.controller;

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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.basestation.dto.BaseStationDTO;
import com.huawei.basestation.model.StationStatus;
import com.huawei.basestation.model.StationType;
import com.huawei.basestation.service.BaseStationService;

@WebMvcTest(BaseStationController.class)
@SuppressWarnings("null")
class BaseStationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BaseStationService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createStation_returnsCreatedStation() throws Exception {
        BaseStationDTO dto = createTestDTO();
        BaseStationDTO created = createTestDTO();
        created.setId(1L);

        when(service.createStation(any(BaseStationDTO.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/stations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
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
    void updateStation_returnsUpdatedStation() throws Exception {
        BaseStationDTO dto = createTestDTO();
        dto.setId(1L);
        dto.setStationName("BS-001-Updated");

        when(service.updateStation(eq(1L), any(BaseStationDTO.class))).thenReturn(dto);

        mockMvc.perform(put("/api/v1/stations/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
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
}
