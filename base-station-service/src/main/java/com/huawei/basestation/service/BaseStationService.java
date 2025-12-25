package com.huawei.basestation.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.huawei.basestation.dto.BaseStationDTO;
import com.huawei.basestation.model.BaseStation;
import com.huawei.basestation.model.StationStatus;
import com.huawei.basestation.model.StationType;
import com.huawei.basestation.repository.BaseStationRepository;

@Service
@Transactional
public class BaseStationService {

    private final BaseStationRepository repository;

    public BaseStationService(BaseStationRepository repository) {
        this.repository = repository;
    }

    public BaseStationDTO createStation(BaseStationDTO dto) {
        String stationName = Objects.requireNonNull(dto.getStationName(), "Station name cannot be null");
        if (repository.findByStationName(stationName).isPresent()) {
            throw new IllegalArgumentException("Station with name " + stationName + " already exists");
        }
        BaseStation station = convertToEntity(dto);
        return convertToDTO(repository.save(station));
    }

    @Transactional(readOnly = true)
    public Optional<BaseStationDTO> getStationById(Long id) {
        return Objects.requireNonNull(
                repository.findById(id).map(this::convertToDTO),
                "Repository returned null Optional");
    }

    @Transactional(readOnly = true)
    public List<BaseStationDTO> getAllStations() {
        return Objects.requireNonNull(
                repository.findAll().stream()
                        .map(this::convertToDTO)
                        .toList(),
                "Repository returned null list");
    }

    @Transactional(readOnly = true)
    public List<BaseStationDTO> getStationsByStatus(StationStatus status) {
        return Objects.requireNonNull(
                repository.findByStatus(status).stream()
                        .map(this::convertToDTO)
                        .toList(),
                "Repository returned null list");
    }

    @Transactional(readOnly = true)
    public List<BaseStationDTO> getStationsByType(StationType type) {
        return Objects.requireNonNull(
                repository.findByStationType(type).stream()
                        .map(this::convertToDTO)
                        .toList(),
                "Repository returned null list");
    }

    public BaseStationDTO updateStation(Long id, BaseStationDTO dto) {
        BaseStation station = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Station not found with id: " + id));

        String newStationName = Objects.requireNonNull(dto.getStationName(), "Station name cannot be null");
        if (!station.getStationName().equals(newStationName)
                && repository.findByStationName(newStationName).isPresent()) {
            throw new IllegalArgumentException("Station with name " + newStationName + " already exists");
        }

        station.setStationName(dto.getStationName());
        station.setLocation(dto.getLocation());
        station.setLatitude(dto.getLatitude());
        station.setLongitude(dto.getLongitude());
        station.setStationType(dto.getStationType());
        station.setStatus(dto.getStatus() != null ? dto.getStatus() : station.getStatus());
        station.setPowerConsumption(dto.getPowerConsumption());
        station.setDescription(dto.getDescription());

        return convertToDTO(repository.save(station));
    }

    public void deleteStation(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Station not found with id: " + id);
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<BaseStationDTO> findStationsInArea(Double minLat, Double maxLat, Double minLon, Double maxLon) {
        return Objects.requireNonNull(
                repository.findStationsInArea(minLat, maxLat, minLon, maxLon).stream()
                        .map(this::convertToDTO)
                        .toList(),
                "Repository returned null list");
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
        return Objects.requireNonNull(
                repository.findStationsNearPoint(lat, lon, radiusKm).stream()
                        .map(this::convertToDTO)
                        .toList(),
                "Repository returned null list");
    }

    private BaseStation convertToEntity(BaseStationDTO dto) {
        BaseStation station = new BaseStation();
        station.setStationName(dto.getStationName());
        station.setLocation(dto.getLocation());
        station.setLatitude(dto.getLatitude());
        station.setLongitude(dto.getLongitude());
        station.setStationType(dto.getStationType());
        station.setStatus(dto.getStatus() != null ? dto.getStatus() : StationStatus.ACTIVE);
        station.setPowerConsumption(dto.getPowerConsumption());
        station.setDescription(dto.getDescription());
        return station;
    }

    private BaseStationDTO convertToDTO(BaseStation station) {
        BaseStationDTO dto = new BaseStationDTO();
        dto.setId(station.getId());
        dto.setStationName(station.getStationName());
        dto.setLocation(station.getLocation());
        dto.setLatitude(station.getLatitude());
        dto.setLongitude(station.getLongitude());
        dto.setStationType(station.getStationType());
        dto.setStatus(station.getStatus());
        dto.setPowerConsumption(station.getPowerConsumption());
        dto.setDescription(station.getDescription());
        dto.setCreatedAt(station.getCreatedAt());
        dto.setUpdatedAt(station.getUpdatedAt());
        return dto;
    }
}
