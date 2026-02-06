package com.huawei.basestation.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.huawei.basestation.dto.BaseStationDTO;
import com.huawei.basestation.model.BaseStation;
import com.huawei.basestation.model.ManagementProtocol;
import com.huawei.basestation.model.StationStatus;
import com.huawei.basestation.model.StationType;
import com.huawei.basestation.repository.BaseStationRepository;
import com.huawei.common.audit.AuditLogger;
import com.huawei.common.audit.AuditLogger.AuditAction;

import static com.huawei.common.constants.ServiceNames.SYSTEM_ACTOR;

@Service
@Transactional
public class BaseStationService {
    private static final String AUDIT_RESOURCE_PREFIX = "station:";

    private final BaseStationRepository repository;
    private final AuditLogger auditLogger;

    public BaseStationService(BaseStationRepository repository, AuditLogger auditLogger) {
        this.repository = repository;
        this.auditLogger = auditLogger;
    }

    public BaseStationDTO createStation(BaseStationDTO dto) {
        // @NotBlank validation ensures stationName is not null - requireNonNull satisfies null checker
        String stationName = Objects.requireNonNull(dto.stationName(), "Station name is required");
        if (repository.findByStationName(stationName).isPresent()) {
            throw new IllegalArgumentException("Station with name " + stationName + " already exists");
        }
        BaseStation station = convertToEntity(dto);
        BaseStationDTO saved = convertToDTO(repository.save(station));
        auditLogger.log(AuditAction.STATION_CREATED, SYSTEM_ACTOR,
                AUDIT_RESOURCE_PREFIX + saved.id(), "name=" + saved.stationName());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<BaseStationDTO> getStationById(Long id) {
        return Objects.requireNonNull(repository.findById(id).map(this::convertToDTO));
    }

    @Transactional(readOnly = true)
    public List<BaseStationDTO> getAllStations() {
        return Objects.requireNonNull(repository.findAll().stream()
                        .map(this::convertToDTO)
                        .toList());
    }

    @Transactional(readOnly = true)
    public List<BaseStationDTO> getStationsByStatus(StationStatus status) {
        return Objects.requireNonNull(repository.findByStatus(status).stream()
                        .map(this::convertToDTO)
                        .toList());
    }

    @Transactional(readOnly = true)
    public List<BaseStationDTO> getStationsByType(StationType type) {
        return Objects.requireNonNull(repository.findByStationType(type).stream()
                        .map(this::convertToDTO)
                        .toList());
    }

    public BaseStationDTO updateStation(Long id, BaseStationDTO dto) {
        BaseStation station = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Station not found with id: " + id));

        // @NotBlank validation ensures stationName is not null - requireNonNull satisfies null checker
        String newStationName = Objects.requireNonNull(dto.stationName(), "Station name is required");
        if (!station.getStationName().equals(newStationName)
                && repository.findByStationName(newStationName).isPresent()) {
            throw new IllegalArgumentException("Station with name " + newStationName + " already exists");
        }

        station.setStationName(dto.stationName());
        station.setLocation(dto.location());
        station.setLatitude(dto.latitude());
        station.setLongitude(dto.longitude());
        station.setStationType(dto.stationType());
        station.setIpAddress(dto.ipAddress());
        station.setPort(dto.port());
        if (dto.managementProtocol() != null) {
            station.setManagementProtocol(dto.managementProtocol());
        }
        // Status and powerConsumption are read-only (derived from metrics)
        station.setDescription(dto.description());

        BaseStationDTO updated = convertToDTO(repository.save(station));
        auditLogger.log(AuditAction.STATION_UPDATED, SYSTEM_ACTOR,
                AUDIT_RESOURCE_PREFIX + id, "name=" + updated.stationName());
        return updated;
    }

    public void deleteStation(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Station not found with id: " + id);
        }
        repository.deleteById(id);
        auditLogger.log(AuditAction.STATION_DELETED, SYSTEM_ACTOR, AUDIT_RESOURCE_PREFIX + id, null);
    }

    @Transactional(readOnly = true)
    public List<BaseStationDTO> findStationsInArea(Double minLat, Double maxLat, Double minLon, Double maxLon) {
        return Objects.requireNonNull(repository.findStationsInArea(minLat, maxLat, minLon, maxLon).stream()
                        .map(this::convertToDTO)
                        .toList());
    }

    @Transactional(readOnly = true)
    public Long getStationCountByStatus(StationStatus status) {
        return repository.countByStatus(status);
    }

    /**
     * Finds stations within a radius of a geographic point using Haversine formula.
     * This performs actual great-circle distance calculation on Earth's surface.
     */
    @Transactional(readOnly = true)
    public List<BaseStationDTO> findStationsNearPoint(Double lat, Double lon, Double radiusKm) {
        return Objects.requireNonNull(repository.findStationsNearPoint(lat, lon, radiusKm).stream()
                        .map(this::convertToDTO)
                        .toList());
    }

    private BaseStation convertToEntity(BaseStationDTO dto) {
        BaseStation station = new BaseStation();
        station.setStationName(dto.stationName());
        station.setLocation(dto.location());
        station.setLatitude(dto.latitude());
        station.setLongitude(dto.longitude());
        station.setStationType(dto.stationType());
        station.setIpAddress(dto.ipAddress());
        station.setPort(dto.port());
        station.setManagementProtocol(Objects.requireNonNullElse(
                dto.managementProtocol(), ManagementProtocol.DIRECT));
        // Status defaults to OFFLINE until first metrics/heartbeat received
        station.setStatus(StationStatus.OFFLINE);
        // powerConsumption comes from metrics, not user input
        station.setDescription(dto.description());
        return station;
    }

    private BaseStationDTO convertToDTO(BaseStation station) {
        return BaseStationDTO.builder()
                .id(station.getId())
                .stationName(station.getStationName())
                .location(station.getLocation())
                .latitude(station.getLatitude())
                .longitude(station.getLongitude())
                .stationType(station.getStationType())
                .ipAddress(station.getIpAddress())
                .port(station.getPort())
                .managementProtocol(station.getManagementProtocol())
                .status(station.getStatus())
                .powerConsumption(station.getPowerConsumption())
                .description(station.getDescription())
                .createdAt(station.getCreatedAt())
                .updatedAt(station.getUpdatedAt())
                .build();
    }
}
