package com.huawei.basestation.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public BaseStationService(BaseStationRepository repository) {
        this.repository = repository;
    }

    @SuppressWarnings("null")
    public BaseStationDTO createStation(BaseStationDTO dto) {
        if (repository.findByStationName(dto.getStationName()).isPresent()) {
            throw new IllegalArgumentException("Station with name " + dto.getStationName() + " already exists");
        }

        BaseStation station = convertToEntity(dto);

        BaseStation saved = repository.save(station);
        return convertToDTO(saved);
    }

    @Transactional(readOnly = true)
    public Optional<BaseStationDTO> getStationById(Long id) {
        return repository.findById(Objects.requireNonNull(id)).map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public List<BaseStationDTO> getAllStations() {
        return repository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BaseStationDTO> getStationsByStatus(StationStatus status) {
        return repository.findByStatus(status).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BaseStationDTO> getStationsByType(StationType type) {
        return repository.findByStationType(type).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public BaseStationDTO updateStation(Long id, BaseStationDTO dto) {
        BaseStation station = repository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new IllegalArgumentException("Station not found with id: " + id));

        // Check if name is being changed and if new name already exists
        if (!station.getStationName().equals(dto.getStationName())) {
            if (repository.findByStationName(dto.getStationName()).isPresent()) {
                throw new IllegalArgumentException("Station with name " + dto.getStationName() + " already exists");
            }
        }

        station.setStationName(dto.getStationName());
        station.setLocation(dto.getLocation());
        station.setLatitude(dto.getLatitude());
        station.setLongitude(dto.getLongitude());
        station.setStationType(dto.getStationType());
        station.setStatus(dto.getStatus() != null ? dto.getStatus() : station.getStatus());
        station.setPowerConsumption(dto.getPowerConsumption());
        station.setDescription(dto.getDescription());

        BaseStation updated = repository.save(station);
        return convertToDTO(updated);
    }

    public void deleteStation(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Station id cannot be null");
        }
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Station not found with id: " + id);
        }
        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<BaseStationDTO> findStationsInArea(Double minLat, Double maxLat, Double minLon, Double maxLon) {
        return repository.findStationsInArea(minLat, maxLat, minLon, maxLon).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Long getStationCountByStatus(StationStatus status) {
        return repository.countByStatus(status);
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
