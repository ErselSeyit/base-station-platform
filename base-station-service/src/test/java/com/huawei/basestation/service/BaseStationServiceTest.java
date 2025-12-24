package com.huawei.basestation.service;

import com.huawei.basestation.dto.BaseStationDTO;
import com.huawei.basestation.model.BaseStation;
import com.huawei.basestation.model.StationStatus;
import com.huawei.basestation.model.StationType;
import com.huawei.basestation.repository.BaseStationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BaseStationServiceTest {

    @Mock
    private BaseStationRepository repository;

    @InjectMocks
    private BaseStationService service;

    private BaseStation testStation;
    private BaseStationDTO testDTO;

    @BeforeEach
    void setUp() {
        testStation = new BaseStation();
        testStation.setId(1L);
        testStation.setStationName("BS-001");
        testStation.setLocation("Downtown");
        testStation.setLatitude(40.7128);
        testStation.setLongitude(-74.0060);
        testStation.setStationType(StationType.MACRO_CELL);
        testStation.setStatus(StationStatus.ACTIVE);

        testDTO = new BaseStationDTO();
        testDTO.setStationName("BS-001");
        testDTO.setLocation("Downtown");
        testDTO.setLatitude(40.7128);
        testDTO.setLongitude(-74.0060);
        testDTO.setStationType(StationType.MACRO_CELL);
        testDTO.setStatus(StationStatus.ACTIVE);
    }

    @Test
    void testCreateStation_Success() {
        when(repository.findByStationName(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(BaseStation.class))).thenReturn(testStation);

        BaseStationDTO result = service.createStation(testDTO);

        assertNotNull(result);
        assertEquals("BS-001", result.getStationName());
        verify(repository, times(1)).save(any(BaseStation.class));
    }

    @Test
    void testCreateStation_DuplicateName() {
        when(repository.findByStationName(anyString())).thenReturn(Optional.of(testStation));

        assertThrows(IllegalArgumentException.class, () -> service.createStation(testDTO));
        verify(repository, never()).save(any(BaseStation.class));
    }

    @Test
    void testGetStationById_Success() {
        when(repository.findById(1L)).thenReturn(Optional.of(testStation));

        Optional<BaseStationDTO> result = service.getStationById(1L);

        assertTrue(result.isPresent());
        assertEquals("BS-001", result.get().getStationName());
    }

    @Test
    void testGetStationById_NotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        Optional<BaseStationDTO> result = service.getStationById(1L);

        assertFalse(result.isPresent());
    }

    @Test
    void testGetAllStations() {
        BaseStation station2 = new BaseStation();
        station2.setId(2L);
        station2.setStationName("BS-002");
        station2.setLocation("Uptown");
        station2.setLatitude(40.7580);
        station2.setLongitude(-73.9855);
        station2.setStationType(StationType.MICRO_CELL);
        station2.setStatus(StationStatus.ACTIVE);

        when(repository.findAll()).thenReturn(Arrays.asList(testStation, station2));

        List<BaseStationDTO> result = service.getAllStations();

        assertEquals(2, result.size());
        verify(repository, times(1)).findAll();
    }

    @Test
    void testUpdateStation_Success() {
        BaseStationDTO updateDTO = new BaseStationDTO();
        updateDTO.setStationName("BS-001-Updated");
        updateDTO.setLocation("New Location");
        updateDTO.setLatitude(40.7128);
        updateDTO.setLongitude(-74.0060);
        updateDTO.setStationType(StationType.MACRO_CELL);

        when(repository.findById(1L)).thenReturn(Optional.of(testStation));
        when(repository.findByStationName(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(BaseStation.class))).thenReturn(testStation);

        BaseStationDTO result = service.updateStation(1L, updateDTO);

        assertNotNull(result);
        verify(repository, times(1)).save(any(BaseStation.class));
    }

    @Test
    void testUpdateStation_NotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.updateStation(1L, testDTO));
    }

    @Test
    void testDeleteStation_Success() {
        when(repository.existsById(1L)).thenReturn(true);
        doNothing().when(repository).deleteById(1L);

        assertDoesNotThrow(() -> service.deleteStation(1L));
        verify(repository, times(1)).deleteById(1L);
    }

    @Test
    void testDeleteStation_NotFound() {
        when(repository.existsById(1L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.deleteStation(1L));
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    void testGetStationsByStatus() {
        when(repository.findByStatus(StationStatus.ACTIVE))
                .thenReturn(Arrays.asList(testStation));

        List<BaseStationDTO> result = service.getStationsByStatus(StationStatus.ACTIVE);

        assertEquals(1, result.size());
        assertEquals(StationStatus.ACTIVE, result.get(0).getStatus());
    }
}

