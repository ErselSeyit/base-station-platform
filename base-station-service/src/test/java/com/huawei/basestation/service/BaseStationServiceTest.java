package com.huawei.basestation.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.huawei.basestation.dto.BaseStationDTO;
import com.huawei.basestation.model.BaseStation;
import com.huawei.basestation.model.StationStatus;
import com.huawei.basestation.model.StationType;
import com.huawei.basestation.repository.BaseStationRepository;
import com.huawei.common.audit.AuditLogger;

@ExtendWith(MockitoExtension.class)
class BaseStationServiceTest {

    @Mock
    private BaseStationRepository repository;

    @Mock
    private AuditLogger auditLogger;

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

        testDTO = BaseStationDTO.builder()
                .stationName("BS-001")
                .location("Downtown")
                .latitude(40.7128)
                .longitude(-74.0060)
                .stationType(StationType.MACRO_CELL)
                .status(StationStatus.ACTIVE)
                .build();
    }

    @Test
    @SuppressWarnings("null")
    void createStation_savesAndReturnsStation() {
        when(repository.findByStationName(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(BaseStation.class))).thenReturn(testStation);

        BaseStationDTO result = service.createStation(testDTO);

        assertNotNull(result);
        assertEquals("BS-001", result.stationName());
        verify(repository).save(any(BaseStation.class));
    }

    @Test
    @SuppressWarnings("null")
    void createStation_throwsOnDuplicateName() {
        when(repository.findByStationName(anyString())).thenReturn(Optional.of(testStation));

        assertThrows(IllegalArgumentException.class, () -> service.createStation(testDTO));
        verify(repository, never()).save(any());
    }

    @Test
    void getStationById_returnsStationWhenFound() {
        when(repository.findById(1L)).thenReturn(Optional.of(testStation));

        Optional<BaseStationDTO> result = service.getStationById(1L);

        assertTrue(result.isPresent());
        assertEquals("BS-001", result.get().stationName());
    }

    @Test
    void getStationById_returnsEmptyWhenNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        Optional<BaseStationDTO> result = service.getStationById(1L);

        assertFalse(result.isPresent());
    }

    @Test
    void getAllStations_returnsAllStations() {
        BaseStation station2 = new BaseStation();
        station2.setId(2L);
        station2.setStationName("BS-002");
        station2.setLocation("Uptown");
        station2.setLatitude(40.7580);
        station2.setLongitude(-73.9855);
        station2.setStationType(StationType.MICRO_CELL);
        station2.setStatus(StationStatus.ACTIVE);

        when(repository.findAll()).thenReturn(List.of(testStation, station2));

        List<BaseStationDTO> result = service.getAllStations();

        assertEquals(2, result.size());
    }

    @Test
    @SuppressWarnings("null")
    void updateStation_updatesAndReturnsStation() {
        BaseStationDTO updateDTO = BaseStationDTO.builder()
                .stationName("BS-001-Updated")
                .location("New Location")
                .latitude(40.7128)
                .longitude(-74.0060)
                .stationType(StationType.MACRO_CELL)
                .build();

        when(repository.findById(1L)).thenReturn(Optional.of(testStation));
        when(repository.findByStationName(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(BaseStation.class))).thenReturn(testStation);

        BaseStationDTO result = service.updateStation(1L, updateDTO);

        assertNotNull(result);
        verify(repository).save(any(BaseStation.class));
    }

    @Test
    void updateStation_throwsWhenNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertNotNull(testDTO);
        assertThrows(IllegalArgumentException.class, () -> service.updateStation(1L, testDTO));
    }

    @Test
    void deleteStation_deletesExistingStation() {
        when(repository.existsById(1L)).thenReturn(true);
        doNothing().when(repository).deleteById(1L);

        assertDoesNotThrow(() -> service.deleteStation(1L));
        verify(repository).deleteById(1L);
    }

    @Test
    void deleteStation_throwsWhenNotFound() {
        when(repository.existsById(1L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.deleteStation(1L));
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    void getStationsByStatus_filtersCorrectly() {
        when(repository.findByStatus(StationStatus.ACTIVE)).thenReturn(List.of(testStation));

        List<BaseStationDTO> result = service.getStationsByStatus(StationStatus.ACTIVE);

        assertEquals(1, result.size());
        assertEquals(StationStatus.ACTIVE, result.get(0).status());
    }
}
