package com.huawei.tmf.controller;

import com.huawei.tmf.model.Service;
import com.huawei.tmf.service.ServiceInventoryService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TMF638 Service Inventory Management API Controller.
 * Implements TM Forum TMF638 v4.0 specification.
 *
 * Base path: /tmf-api/serviceInventoryManagement/v4
 */
@RestController
@RequestMapping("/tmf-api/serviceInventoryManagement/v4")
@CrossOrigin(origins = "*")
public class ServiceInventoryController {

    private final ServiceInventoryService serviceInventoryService;

    public ServiceInventoryController(ServiceInventoryService serviceInventoryService) {
        this.serviceInventoryService = serviceInventoryService;
    }

    /**
     * List or find Services.
     * GET /service
     */
    @GetMapping("/service")
    public ResponseEntity<List<Service>> listServices(
            @RequestParam(required = false) String fields,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String serviceType,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String externalId,
            @RequestParam(required = false) String relatedPartyId) {

        PageRequest pageRequest = PageRequest.of(offset / limit, limit, Sort.by("createdAt").descending());

        Page<Service> services = serviceInventoryService.findServices(
                state, category, serviceType, name, externalId, relatedPartyId, pageRequest);

        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(services.getTotalElements()))
                .header("X-Result-Count", String.valueOf(services.getNumberOfElements()))
                .body(services.getContent());
    }

    /**
     * Retrieve a Service by ID.
     * GET /service/{id}
     */
    @GetMapping("/service/{id}")
    public ResponseEntity<Service> getService(
            @PathVariable String id,
            @RequestParam(required = false) String fields) {

        Optional<Service> service = serviceInventoryService.findById(id);
        return service.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a Service.
     * POST /service
     */
    @PostMapping("/service")
    public ResponseEntity<Service> createService(@RequestBody Service service) {
        Service created = serviceInventoryService.create(service);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update a Service (full replacement).
     * PUT /service/{id}
     */
    @PutMapping("/service/{id}")
    public ResponseEntity<Service> updateService(
            @PathVariable String id,
            @RequestBody Service service) {

        Optional<Service> existing = serviceInventoryService.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        service.setId(id);
        Service updated = serviceInventoryService.update(service);
        return ResponseEntity.ok(updated);
    }

    /**
     * Partial update of a Service.
     * PATCH /service/{id}
     */
    @PatchMapping("/service/{id}")
    public ResponseEntity<Service> patchService(
            @PathVariable String id,
            @RequestBody Map<String, Object> updates) {

        Optional<Service> patched = serviceInventoryService.patch(id, updates);
        return patched.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a Service.
     * DELETE /service/{id}
     */
    @DeleteMapping("/service/{id}")
    public ResponseEntity<Void> deleteService(@PathVariable String id) {
        boolean deleted = serviceInventoryService.delete(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // Service lifecycle operations

    /**
     * Activate a Service.
     * POST /service/{id}/activate
     */
    @PostMapping("/service/{id}/activate")
    public ResponseEntity<Service> activateService(@PathVariable String id) {
        Optional<Service> activated = serviceInventoryService.activate(id);
        return activated.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Deactivate a Service.
     * POST /service/{id}/deactivate
     */
    @PostMapping("/service/{id}/deactivate")
    public ResponseEntity<Service> deactivateService(@PathVariable String id) {
        Optional<Service> deactivated = serviceInventoryService.deactivate(id);
        return deactivated.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Terminate a Service.
     * POST /service/{id}/terminate
     */
    @PostMapping("/service/{id}/terminate")
    public ResponseEntity<Service> terminateService(@PathVariable String id) {
        Optional<Service> terminated = serviceInventoryService.terminate(id);
        return terminated.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // TMF638 Hub (notification subscription) endpoints

    /**
     * Register a listener for service events.
     * POST /hub
     */
    @PostMapping("/hub")
    public ResponseEntity<Map<String, Object>> registerListener(
            @RequestBody Map<String, Object> subscription) {

        Map<String, Object> registered = serviceInventoryService.registerListener(subscription);
        return ResponseEntity.status(HttpStatus.CREATED).body(registered);
    }

    /**
     * Unregister a listener.
     * DELETE /hub/{id}
     */
    @DeleteMapping("/hub/{id}")
    public ResponseEntity<Void> unregisterListener(@PathVariable String id) {
        serviceInventoryService.unregisterListener(id);
        return ResponseEntity.noContent().build();
    }

    // Convenience endpoints

    /**
     * Get services by external ID.
     */
    @GetMapping("/service/external/{externalId}")
    public ResponseEntity<Service> getServiceByExternalId(@PathVariable String externalId) {
        Optional<Service> service = serviceInventoryService.findByExternalId(externalId);
        return service.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get services by resource ID.
     */
    @GetMapping("/service/byResource/{resourceId}")
    public ResponseEntity<List<Service>> getServicesByResource(@PathVariable String resourceId) {
        List<Service> services = serviceInventoryService.findByResourceId(resourceId);
        return ResponseEntity.ok(services);
    }

    /**
     * Get service statistics.
     */
    @GetMapping("/service/stats")
    public ResponseEntity<Map<String, Object>> getServiceStats() {
        Map<String, Object> stats = serviceInventoryService.getStatistics();
        return ResponseEntity.ok(stats);
    }
}
