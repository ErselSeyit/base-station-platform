package com.huawei.basestation.service;

import com.huawei.basestation.dto.BulkImportRequest;
import com.huawei.basestation.dto.BulkImportRequest.ImportOptions;
import com.huawei.basestation.dto.BulkImportRequest.StationImportItem;
import com.huawei.basestation.dto.BulkImportResponse;
import com.huawei.basestation.dto.BulkImportResponse.ImportResult;
import com.huawei.basestation.model.BaseStation;
import com.huawei.basestation.model.Organization;
import com.huawei.basestation.repository.BaseStationRepository;
import com.huawei.basestation.repository.OrganizationRepository;
import com.huawei.basestation.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for bulk station provisioning operations.
 *
 * Supports:
 * - Bulk import of stations from CSV/JSON
 * - Export of stations to various formats
 * - Validation and dry-run modes
 */
@Service
@SuppressWarnings("null") // Spring Data and record accessors guarantee non-null returns
public class BulkProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(BulkProvisioningService.class);

    private final BaseStationRepository stationRepository;
    private final OrganizationRepository organizationRepository;

    public BulkProvisioningService(BaseStationRepository stationRepository,
                                   OrganizationRepository organizationRepository) {
        this.stationRepository = stationRepository;
        this.organizationRepository = organizationRepository;
    }

    /**
     * Import multiple stations in bulk.
     *
     * @param request the bulk import request
     * @return import results
     */
    @Transactional
    public BulkImportResponse importStations(BulkImportRequest request) {
        List<StationImportItem> stations = request.stations();
        ImportOptions options = request.options();

        if (stations == null || stations.isEmpty()) {
            return BulkImportResponse.empty(options.dryRun());
        }

        // Get organization from tenant context
        Long orgId = TenantContext.getCurrentOrganizationId();
        Organization organization = null;
        if (orgId != null) {
            organization = organizationRepository.findById(orgId).orElse(null);
        }

        List<ImportResult> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        int updatedCount = 0;

        for (StationImportItem item : stations) {
            ImportResult result = processStationImport(item, organization, options);
            results.add(result);

            switch (result.status()) {
                case CREATED -> successCount++;
                case UPDATED -> updatedCount++;
                case SKIPPED -> skippedCount++;
                case FAILED -> failedCount++;
            }
        }

        log.info("Bulk import completed: {} created, {} updated, {} skipped, {} failed (dryRun={})",
                successCount, updatedCount, skippedCount, failedCount, options.dryRun());

        return new BulkImportResponse(
                stations.size(),
                successCount,
                failedCount,
                skippedCount,
                updatedCount,
                options.dryRun(),
                results,
                LocalDateTime.now()
        );
    }

    private ImportResult processStationImport(StationImportItem item,
                                              Organization organization,
                                              ImportOptions options) {
        String stationName = item.stationName();

        try {
            // Validate coordinates if enabled
            if (options.validateCoordinates()) {
                String coordError = validateCoordinates(item.latitude(), item.longitude());
                if (coordError != null) {
                    return new ImportResult(stationName, ImportResult.Status.FAILED, coordError, null);
                }
            }

            // Check for existing station
            Optional<BaseStation> existing = stationRepository.findByStationName(stationName);

            if (existing.isPresent()) {
                if (options.updateExisting()) {
                    // Update existing station
                    if (!options.dryRun()) {
                        BaseStation station = existing.get();
                        updateStationFromItem(station, item);
                        stationRepository.save(station);
                    }
                    return new ImportResult(stationName, ImportResult.Status.UPDATED,
                            "Station updated", existing.get().getId());
                } else if (options.skipDuplicates()) {
                    return new ImportResult(stationName, ImportResult.Status.SKIPPED,
                            "Duplicate station name", existing.get().getId());
                } else {
                    return new ImportResult(stationName, ImportResult.Status.FAILED,
                            "Station with this name already exists", null);
                }
            }

            // Create new station
            if (!options.dryRun()) {
                BaseStation station = createStationFromItem(item, organization);
                station = stationRepository.save(station);
                return new ImportResult(stationName, ImportResult.Status.CREATED,
                        "Station created", station.getId());
            } else {
                return new ImportResult(stationName, ImportResult.Status.CREATED,
                        "Would create station (dry run)", null);
            }

        } catch (Exception e) {
            log.error("Failed to import station '{}': {}", stationName, e.getMessage());
            return new ImportResult(stationName, ImportResult.Status.FAILED,
                    "Import error: " + e.getMessage(), null);
        }
    }

    private String validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return "Latitude and longitude are required";
        }
        if (latitude < -90 || latitude > 90) {
            return "Latitude must be between -90 and 90";
        }
        if (longitude < -180 || longitude > 180) {
            return "Longitude must be between -180 and 180";
        }
        return null;
    }

    private BaseStation createStationFromItem(StationImportItem item, Organization organization) {
        BaseStation station = new BaseStation(
                item.stationName(),
                item.location(),
                item.latitude(),
                item.longitude(),
                item.stationType()
        );
        station.setOrganization(organization);
        if (item.powerConsumption() != null) {
            station.setPowerConsumption(item.powerConsumption());
        }
        if (item.description() != null) {
            station.setDescription(item.description());
        }
        return station;
    }

    private void updateStationFromItem(BaseStation station, StationImportItem item) {
        station.setLocation(item.location());
        station.setLatitude(item.latitude());
        station.setLongitude(item.longitude());
        station.setStationType(item.stationType());
        if (item.powerConsumption() != null) {
            station.setPowerConsumption(item.powerConsumption());
        }
        if (item.description() != null) {
            station.setDescription(item.description());
        }
    }

    /**
     * Export all stations for the current organization.
     *
     * @return list of stations
     */
    @Transactional(readOnly = true)
    public List<BaseStation> exportStations() {
        Long orgId = TenantContext.getCurrentOrganizationId();
        if (orgId != null) {
            return stationRepository.findByOrganizationId(orgId);
        }
        // Admin without org context - return all
        return stationRepository.findAll();
    }

    /**
     * Export stations with specific status.
     *
     * @param status the status to filter by
     * @return list of stations
     */
    @Transactional(readOnly = true)
    public List<BaseStation> exportStationsByStatus(com.huawei.basestation.model.StationStatus status) {
        Long orgId = TenantContext.getCurrentOrganizationId();
        if (orgId != null) {
            return stationRepository.findByOrganizationIdAndStatus(orgId, status);
        }
        return stationRepository.findByStatus(status);
    }
}
