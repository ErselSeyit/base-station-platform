package com.huawei.tmf.controller;

import static com.huawei.common.constants.HttpHeaders.HEADER_RESULT_COUNT;
import static com.huawei.common.constants.HttpHeaders.HEADER_TOTAL_COUNT;

import com.huawei.tmf.model.Resource;
import com.huawei.tmf.service.ResourceService;

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
 * TMF639 Resource Inventory Management API Controller.
 * Implements TM Forum TMF639 v4.0 specification.
 *
 * Base path: /tmf-api/resourceInventoryManagement/v4
 */
@RestController
@RequestMapping("/tmf-api/resourceInventoryManagement/v4")
@CrossOrigin(origins = "*")
public class ResourceInventoryController {

    private final ResourceService resourceService;

    public ResourceInventoryController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /**
     * List or find Resources.
     * GET /resource
     *
     * @param fields Comma-separated list of attributes to return
     * @param offset Requested index for start of resources to be provided
     * @param limit Requested number of resources to be provided
     * @param category Filter by category
     * @param resourceType Filter by resource type
     * @param operationalState Filter by operational state
     * @param administrativeState Filter by administrative state
     */
    @GetMapping("/resource")
    public ResponseEntity<List<Resource>> listResources(
            @RequestParam(required = false) String fields,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String operationalState,
            @RequestParam(required = false) String administrativeState,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String externalId) {

        PageRequest pageRequest = PageRequest.of(offset / limit, limit, Sort.by("createdAt").descending());

        Page<Resource> resources = resourceService.findResources(
                category, resourceType, operationalState, administrativeState, name, externalId, pageRequest);

        return ResponseEntity.ok()
                .header(HEADER_TOTAL_COUNT, String.valueOf(resources.getTotalElements()))
                .header(HEADER_RESULT_COUNT, String.valueOf(resources.getNumberOfElements()))
                .body(resources.getContent());
    }

    /**
     * Retrieve a Resource by ID.
     * GET /resource/{id}
     */
    @GetMapping("/resource/{id}")
    public ResponseEntity<Resource> getResource(
            @PathVariable String id,
            @RequestParam(required = false) String fields) {

        Optional<Resource> resource = resourceService.findById(id);
        return resource.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a Resource.
     * POST /resource
     */
    @PostMapping("/resource")
    public ResponseEntity<Resource> createResource(@RequestBody Resource resource) {
        Resource created = resourceService.create(resource);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Update a Resource (full replacement).
     * PUT /resource/{id}
     */
    @PutMapping("/resource/{id}")
    public ResponseEntity<Resource> updateResource(
            @PathVariable String id,
            @RequestBody Resource resource) {

        Optional<Resource> existing = resourceService.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        resource.setId(id);
        Resource updated = resourceService.update(resource);
        return ResponseEntity.ok(updated);
    }

    /**
     * Partial update of a Resource.
     * PATCH /resource/{id}
     */
    @PatchMapping("/resource/{id}")
    public ResponseEntity<Resource> patchResource(
            @PathVariable String id,
            @RequestBody Map<String, Object> updates) {

        Optional<Resource> patched = resourceService.patch(id, updates);
        return patched.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a Resource.
     * DELETE /resource/{id}
     */
    @DeleteMapping("/resource/{id}")
    public ResponseEntity<Void> deleteResource(@PathVariable String id) {
        boolean deleted = resourceService.delete(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // TMF639 Hub (notification subscription) endpoints

    /**
     * Register a listener for resource events.
     * POST /hub
     */
    @PostMapping("/hub")
    public ResponseEntity<Map<String, Object>> registerListener(
            @RequestBody Map<String, Object> subscription) {

        Map<String, Object> registered = resourceService.registerListener(subscription);
        return ResponseEntity.status(HttpStatus.CREATED).body(registered);
    }

    /**
     * Unregister a listener.
     * DELETE /hub/{id}
     */
    @DeleteMapping("/hub/{id}")
    public ResponseEntity<Void> unregisterListener(@PathVariable String id) {
        resourceService.unregisterListener(id);
        return ResponseEntity.noContent().build();
    }

    // Convenience endpoints (not in TMF spec but useful)

    /**
     * Get resources by external ID (e.g., station_id).
     */
    @GetMapping("/resource/external/{externalId}")
    public ResponseEntity<Resource> getResourceByExternalId(@PathVariable String externalId) {
        Optional<Resource> resource = resourceService.findByExternalId(externalId);
        return resource.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get child resources of a resource.
     */
    @GetMapping("/resource/{id}/children")
    public ResponseEntity<List<Resource>> getChildResources(@PathVariable String id) {
        List<Resource> children = resourceService.findChildren(id);
        return ResponseEntity.ok(children);
    }

    /**
     * Get resource statistics.
     */
    @GetMapping("/resource/stats")
    public ResponseEntity<Map<String, Object>> getResourceStats() {
        Map<String, Object> stats = resourceService.getStatistics();
        return ResponseEntity.ok(stats);
    }
}
