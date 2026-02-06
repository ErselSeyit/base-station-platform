package com.huawei.basestation.controller;

import com.huawei.basestation.dto.BulkImportRequest;
import com.huawei.basestation.dto.BulkImportResponse;
import com.huawei.basestation.model.BaseStation;
import com.huawei.common.security.Roles;
import com.huawei.basestation.model.StationStatus;
import com.huawei.basestation.service.BulkProvisioningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for bulk station provisioning operations.
 */
@RestController
@RequestMapping("/api/v1/stations/bulk")
@Tag(name = "Bulk Provisioning", description = "Bulk import and export of base stations")
@SuppressWarnings("null") // Entity getters and helper methods guarantee non-null returns
public class BulkProvisioningController {

    private final BulkProvisioningService bulkService;

    public BulkProvisioningController(BulkProvisioningService bulkService) {
        this.bulkService = bulkService;
    }

    @Operation(summary = "Bulk import stations", description = "Import multiple base stations in a single request. " +
            "Supports dry-run mode for validation without persistence.")
    @ApiResponse(responseCode = "200", description = "Import completed (check results for individual status)")
    @ApiResponse(responseCode = "400", description = "Invalid request format")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @PostMapping("/import")
    @PreAuthorize(Roles.HAS_OPERATOR)
    public ResponseEntity<BulkImportResponse> importStations(
            @Parameter(description = "Stations to import with options") @Valid @RequestBody BulkImportRequest request) {

        BulkImportResponse response = bulkService.importStations(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Export all stations", description = "Export all stations for the current organization as JSON.")
    @ApiResponse(responseCode = "200", description = "Stations exported successfully")
    @GetMapping("/export")
    @PreAuthorize(Roles.HAS_USER)
    public ResponseEntity<List<BaseStation>> exportStations() {
        List<BaseStation> stations = bulkService.exportStations();
        return ResponseEntity.ok(stations);
    }

    @Operation(summary = "Export stations by status", description = "Export stations filtered by status.")
    @ApiResponse(responseCode = "200", description = "Stations exported successfully")
    @GetMapping("/export/status/{status}")
    @PreAuthorize(Roles.HAS_USER)
    public ResponseEntity<List<BaseStation>> exportStationsByStatus(
            @Parameter(description = "Station status to filter by") @PathVariable StationStatus status) {

        List<BaseStation> stations = bulkService.exportStationsByStatus(status);
        return ResponseEntity.ok(stations);
    }

    @Operation(summary = "Export stations as CSV", description = "Export all stations as CSV file download.")
    @ApiResponse(responseCode = "200", description = "CSV file generated")
    @GetMapping(value = "/export/csv", produces = "text/csv")
    @PreAuthorize(Roles.HAS_USER)
    public ResponseEntity<String> exportStationsAsCsv() {
        List<BaseStation> stations = bulkService.exportStations();
        String csv = convertToCsv(stations);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "stations_export.csv");

        return ResponseEntity.ok()
                .headers(headers)
                .body(csv);
    }

    @Operation(summary = "Get import template", description = "Get a sample JSON template for bulk import.")
    @ApiResponse(responseCode = "200", description = "Template returned")
    @GetMapping("/template")
    public ResponseEntity<BulkImportRequest> getImportTemplate() {
        // Return a sample template
        var sample = new BulkImportRequest(
                List.of(
                        new BulkImportRequest.StationImportItem(
                                "STATION-001",
                                "123 Main St, City",
                                40.7128,
                                -74.0060,
                                com.huawei.basestation.model.StationType.MACRO_CELL,
                                2500.0,
                                "Example macro cell station")),
                new BulkImportRequest.ImportOptions(true, false, true, false));
        return ResponseEntity.ok(sample);
    }

    private String convertToCsv(List<BaseStation> stations) {
        StringBuilder csv = new StringBuilder();
        csv.append("id,stationName,location,latitude,longitude,stationType,status,powerConsumption,description\n");

        for (BaseStation station : stations) {
            csv.append(String.format("%d,%s,%s,%.6f,%.6f,%s,%s,%.2f,%s%n",
                    station.getId(),
                    escapeCsv(station.getStationName()),
                    escapeCsv(station.getLocation()),
                    station.getLatitude(),
                    station.getLongitude(),
                    station.getStationType(),
                    station.getStatus(),
                    station.getPowerConsumption() != null ? station.getPowerConsumption() : 0.0,
                    escapeCsv(station.getDescription())));
        }

        return csv.toString();
    }

    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
